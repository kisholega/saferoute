// Step 3 of the trip flow: travelling with a safety net.
import { api, post } from '../api.js';
import { fakeCall } from '../fakecall.js';
import { Route, distance } from '../geo.js';
import { icon } from '../icons.js';
import * as stage from '../mapstage.js';
import { navigate } from '../router.js';
import { adoptAlert } from '../sos.js';
import { store } from '../store.js';
import {
    batteryLevel, clockTime, copyText, firstName, formatKm, formatMeters, formatMinutes, html, openModal, riskKey, speak,
    toast, vibrate, waLink,
} from '../ui.js';

const BASE_SPEED = { WALK: 1.4, DRIVE: 8 }; // metres per second
const KMH = { WALK: 4.8, DRIVE: 22 };

export default {
    layout: 'map',
    auth: true,
    title: 'Safe Trip',
    mount(root, { params, query }) {
        const tripId = Number(params.id);
        const context = store.tripContext?.tripId === tripId ? store.tripContext : null;
        const map = stage.ensureMap();
        let trip = null;
        let route = null;
        let segments = [];
        let simulating = query.sim === '1';
        const sim = { along: 0, mult: 8, playing: true, offset: 0, wrong: false };
        let simTimer = null;
        let watchId = null;
        let lastPing = 0;
        let pinging = false;
        let following = true;
        let firstFix = true;
        const warned = new Set();
        let riskBanner = null;
        let riskBannerUntil = 0;
        let checkIn = null;
        let disposed = false;
        let trail = [];
        let trailLine = null;
        let lastPosition = null;

        root.innerHTML = html`<div data-banner></div><section class="trip-panel interactive" data-panel aria-live="polite">
            <div class="skeleton" style="height:90px"></div></section>
            <div class="floating map-controls" style="bottom:auto;top:calc(var(--topbar-h) + 96px)">
                <button type="button" class="icon-btn" data-recenter aria-label="Follow my position">${icon('locate')}</button>
            </div>`;
        const bannerHost = root.querySelector('[data-banner]');
        const panel = root.querySelector('[data-panel]');
        root.querySelector('[data-recenter]').addEventListener('click', () => {
            following = true;
            if (lastPosition) stage.flyTo(lastPosition, 17);
        });
        map?.on('dragstart', stopFollowing);
        function stopFollowing() { following = false; }

        load();

        async function load() {
            try {
                trip = await api(`/api/trips/${tripId}`);
            } catch (error) {
                toast(error.message, 'error');
                navigate('/plan', { replace: true });
                return;
            }
            if (disposed) return;
            if (trip.status === 'ARRIVED') { navigate(`/arrived/${tripId}`, { replace: true }); return; }
            if (trip.status === 'ENDED') { toast('This trip has ended.', 'info'); navigate('/plan', { replace: true }); return; }
            simulating = simulating || trip.simulated;
            route = new Route(trip.geometry);
            segments = trip.segments || context?.segments || [];
            const avgRisk = segments.length ? segments.reduce((s, x) => s + x.risk, 0) / segments.length : 0.3;
            stage.drawSelectedRoute({ geometry: trip.geometry, segments, riskLevel: avgRisk > 0.55 ? 'High Risk' : avgRisk > 0.3 ? 'Medium Risk' : 'Low Risk' }, true);
            stage.setPins(null, { latlng: [trip.destinationLatitude, trip.destinationLongitude], label: trip.destinationName });
            if (context?.helpPoints) stage.drawPlaces(context.helpPoints);
            stage.fit(trip.geometry);
            trailLine = L.polyline([], { color: '#6d28d9', weight: 4, opacity: 0.55, dashArray: '2 8', lineCap: 'round' }).addTo(stage.layer('trail'));
            if (trip.lastLatitude != null && trip.progress > 0) {
                sim.along = route.length * trip.progress;
            }
            update();
            if (simulating) startSim(); else startGps();
        }

        // ------------------------------------------------------------ position sources
        function startGps() {
            if (!navigator.geolocation) { offerSimulation('This browser cannot share location.'); return; }
            watchId = navigator.geolocation.watchPosition(
                p => onPosition([p.coords.latitude, p.coords.longitude], p.coords.accuracy, false),
                error => offerSimulation(error.code === 1 ? 'Location access is blocked.' : 'Waiting for a GPS signal…'),
                { enableHighAccuracy: true, maximumAge: 3000, timeout: 20000 });
        }

        function offerSimulation(reason) {
            if (simulating || disposed) return;
            showBanner('warn', 'alert', reason, 'Allow location, or simulate the trip to see how it works.',
                html`<button type="button" class="btn btn-sm" style="background:#fff;color:#9a5c00" data-simulate>${icon('play', 'icon-sm')} Simulate</button>`);
            bannerHost.querySelector('[data-simulate]')?.addEventListener('click', () => {
                if (watchId !== null) navigator.geolocation.clearWatch(watchId);
                watchId = null;
                simulating = true;
                startSim();
                update();
            });
        }

        function startSim() {
            clearInterval(simTimer);
            simTimer = setInterval(() => {
                if (!sim.playing || !route) return;
                const speed = BASE_SPEED[trip.mode] || 1.4;
                if (sim.wrong) {
                    sim.offset = Math.min(260, sim.offset + 60);
                    sim.along = Math.min(route.length, sim.along + speed * sim.mult * 0.15);
                } else {
                    sim.offset = Math.max(0, sim.offset - 90);
                    sim.along = Math.min(route.length, sim.along + speed * sim.mult);
                }
                const point = sim.offset > 0 ? route.offsetPoint(sim.along, sim.offset) : route.pointAt(sim.along);
                onPosition(point, 8, true);
            }, 1000);
        }

        // ------------------------------------------------------------ each new position
        async function onPosition(latlng, accuracy, isSim) {
            if (disposed || !trip) return;
            lastPosition = latlng;
            stage.setMe(latlng, accuracy, isSim);
            trail.push(latlng);
            if (trail.length > 400) trail = trail.slice(-400);
            trailLine?.setLatLngs(trail);
            if (following) {
                if (firstFix) { map.setView(latlng, 17, { animate: true }); firstFix = false; }
                else map.panTo(latlng, { animate: true, duration: 0.8 });
            }
            checkRiskAhead(latlng);

            const interval = isSim ? 1000 : 4000;
            if (pinging || Date.now() - lastPing < interval) return;
            pinging = true;
            lastPing = Date.now();
            try {
                trip = await post(`/api/trips/${tripId}/location`, {
                    latitude: latlng[0], longitude: latlng[1], accuracy: Math.round(accuracy), battery: await batteryLevel(),
                });
                update();
            } catch (error) {
                if (error.status !== 401) toast('Live location could not be sent. Retrying…', 'warn', 2500);
            } finally {
                pinging = false;
            }
        }

        function checkRiskAhead(latlng) {
            if (!segments.length || !route) return;
            const { along, off } = route.project(latlng);
            if (off > 120) return;
            for (let i = 0; i < segments.length; i++) {
                const seg = segments[i];
                const level = riskKey(seg.level);
                if (level === 'low' || warned.has(i)) continue;
                const startAlong = route.cum[Math.min(seg.start, route.cum.length - 1)];
                const endAlong = route.cum[Math.min(seg.end, route.cum.length - 1)];
                const ahead = startAlong - along;
                const inside = along >= startAlong && along <= endAlong;
                if ((ahead >= 0 && ahead <= 220) || inside) {
                    warned.add(i);
                    const help = nearestHelp(latlng);
                    const what = level === 'high' ? 'Riskier stretch' : 'Quieter stretch';
                    const when = inside ? 'You are in a' : `In ${Math.round(Math.max(ahead, 10) / 10) * 10} m:`;
                    const title = inside ? `${what} now` : `${what} ahead`;
                    const sub = `${seg.note}${help ? ` · Nearest help: ${help.name}, ${formatMeters(help.d)}` : ''}`;
                    riskBanner = { tone: level === 'high' ? 'bad' : 'warn', title, sub };
                    riskBannerUntil = Date.now() + 14000;
                    vibrate([200, 100, 200]);
                    speak(`${inside ? when + ' ' + what.toLowerCase() : when + ' ' + what.toLowerCase()}. ${seg.note}. ${help ? `Nearest help is ${help.name}.` : ''} Stay alert.`);
                    renderBanner();
                    break;
                }
            }
        }

        function nearestHelp(latlng) {
            const points = context?.helpPoints || [];
            let best = null;
            points.forEach(p => {
                const d = distance(latlng, [p.latitude, p.longitude]);
                if (!best || d < best.d) best = { ...p, d };
            });
            return best && best.d < 1500 ? best : null;
        }

        // ------------------------------------------------------------ UI
        function showBanner(tone, iconName, title, sub, action = '') {
            bannerHost.innerHTML = html`<div class="trip-banner ${tone} interactive" role="status">
                <span class="b-icon">${icon(iconName, 'icon-lg')}</span>
                <div style="min-width:0"><strong>${title}</strong><span>${sub}</span></div>${action}</div>`;
        }

        function renderBanner() {
            if (!trip) return;
            if (trip.status === 'ALERTED') {
                showBanner('bad', 'siren', 'Your contacts have been alerted', 'They have your last location. If you are safe, tell them now.',
                    html`<button type="button" class="btn btn-sm" style="background:#fff;color:#b5242a" data-safe>I'm safe</button>`);
                bannerHost.querySelector('[data-safe]').addEventListener('click', () => answerCheckIn(true));
                return;
            }
            if (trip.status === 'CHECK_IN_REQUIRED') {
                showBanner('warn', 'alert', 'Are you OK?', 'Please confirm. Your contacts will be alerted if you do not answer.');
                return;
            }
            if (trip.offRoute) {
                showBanner('warn', 'route', `You're ${trip.deviationMeters ? Math.round(trip.deviationMeters) + ' m' : ''} off your route`, 'Head back to the planned route, or SafeRoute will check on you.');
                return;
            }
            if (riskBanner && Date.now() < riskBannerUntil) {
                showBanner(riskBanner.tone, riskBanner.tone === 'bad' ? 'alert' : 'lamp', riskBanner.title, riskBanner.sub);
                return;
            }
            const minutes = (trip.remainingKm ?? trip.distanceKm) / (KMH[trip.mode] || 4.8) * 60;
            showBanner('', 'navigate', `Heading to ${trip.destinationName}`, `${formatKm(trip.remainingKm ?? trip.distanceKm)} to go · about ${formatMinutes(minutes)}`);
        }

        function update() {
            if (!trip || disposed) return;
            if (trip.status === 'ARRIVED') {
                cleanup();
                navigate(`/arrived/${tripId}`);
                return;
            }
            renderBanner();
            renderPanel();
            handleCheckIn();
        }

        function renderPanel() {
            const minutes = (trip.remainingKm ?? trip.distanceKm) / (KMH[trip.mode] || 4.8) * 60;
            const arrive = new Date(Date.now() + minutes * 60000);
            const pct = Math.round((trip.progress || 0) * 100);
            panel.innerHTML = html`
                <div class="row between">
                    <div><div class="tiny muted" style="font-weight:700">ARRIVING AROUND</div><div class="eta num">${clockTime(arrive)}</div></div>
                    <div style="text-align:right">
                        <div class="${trip.notifyContacts ? 'live-pill' : 'tiny muted'}">${trip.notifyContacts ? 'Contacts following live' : 'Shared with SafeRoute only'}</div>
                        <div class="small" style="margin-top:.2rem"><strong>${formatMinutes(minutes)}</strong> · ${formatKm(trip.remainingKm ?? trip.distanceKm)} left</div>
                    </div>
                </div>
                <div class="progress" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"><div style="width:${pct}%"></div></div>
                <div class="row between tiny muted"><span>${trip.originName}</span><span>${pct}%</span><span>${trip.destinationName}</span></div>
                <div class="trip-actions">
                    <button type="button" class="btn btn-soft" data-share>${icon('share')} Share live</button>
                    <button type="button" class="btn btn-ghost" data-fake>${icon('phone')} Fake call</button>
                    <button type="button" class="btn btn-ghost" data-end>${icon('stop')} End trip</button>
                </div>
                ${simulating ? html`<div class="demo-bar">
                    <span class="label">${icon('wand', 'icon-sm')} <strong>Demo</strong> simulated walk</span>
                    <button type="button" class="chip" data-play>${sim.playing ? icon('pause') : icon('play')} ${sim.playing ? 'Pause' : 'Play'}</button>
                    ${[4, 8, 16].map(m => html`<button type="button" class="chip" aria-pressed="${sim.mult === m}" data-speed="${m}">${m}×</button>`)}
                    <button type="button" class="chip" aria-pressed="${sim.wrong}" data-wrong>${icon('route')} ${sim.wrong ? 'Back on route' : 'Wrong turn'}</button>
                </div>` : ''}`;
            panel.querySelector('[data-share]').addEventListener('click', share);
            panel.querySelector('[data-fake]').addEventListener('click', () => {
                const primary = (store.contacts || []).find(c => c.isPrimary);
                toast('Fake call in 5 seconds.', 'info');
                fakeCall(primary ? primary.name : 'Mom', 5);
            });
            panel.querySelector('[data-end]').addEventListener('click', confirmEnd);
            panel.querySelector('[data-play]')?.addEventListener('click', () => { sim.playing = !sim.playing; renderPanel(); });
            panel.querySelectorAll('[data-speed]').forEach(b => b.addEventListener('click', () => { sim.mult = Number(b.dataset.speed); renderPanel(); }));
            panel.querySelector('[data-wrong]')?.addEventListener('click', () => {
                sim.wrong = !sim.wrong;
                toast(sim.wrong ? 'Simulating a wrong turn. SafeRoute will notice in about 15 seconds.' : 'Heading back to the route.', 'info');
                renderPanel();
            });
        }

        function handleCheckIn() {
            const required = trip.status === 'CHECK_IN_REQUIRED' && trip.checkIn?.required;
            if (!required) {
                if (checkIn) { checkIn.close(); checkIn = null; }
                return;
            }
            if (checkIn) return;
            let seconds = trip.checkIn.secondsLeft;
            const total = Math.max(seconds, 1);
            const C = 2 * Math.PI * 85;
            const modal = openModal(html`
                <div style="text-align:center">
                    <p class="eyebrow">Safety check-in</p>
                    <h2 class="display" style="margin:.4rem 0">Are you OK, ${firstName(store.user.name)}?</h2>
                    <p class="muted">SafeRoute noticed that ${trip.checkIn.reason || 'something changed'}. If you don't answer, your contacts will be alerted.</p>
                    <div class="countdown" style="width:160px;height:160px"><svg viewBox="0 0 190 190"><circle class="bg" cx="95" cy="95" r="85" style="stroke:var(--bg-2)"/>
                        <circle class="fg" cx="95" cy="95" r="85" style="stroke:var(--primary)" stroke-dasharray="${C}" stroke-dashoffset="0"/></svg>
                        <div class="n" style="color:var(--ink);font-size:3.2rem" data-n>${seconds}</div></div>
                    <div class="grid grid-2">
                        <button type="button" class="btn btn-safe btn-lg" data-ok>${icon('check')} I'm OK</button>
                        <button type="button" class="btn btn-danger btn-lg" data-help>${icon('siren')} I need help</button>
                    </div>
                </div>`, { locked: true });
            vibrate([400, 200, 400]);
            speak(`Are you okay, ${firstName(store.user.name)}? Please confirm you are safe.`);
            const fg = modal.root.querySelector('.fg');
            const n = modal.root.querySelector('[data-n]');
            const timer = setInterval(() => {
                seconds = Math.max(0, seconds - 1);
                n.textContent = seconds;
                fg.style.strokeDashoffset = String(C * (1 - seconds / total));
                if (seconds <= 0) clearInterval(timer);
            }, 1000);
            modal.root.querySelector('[data-ok]').addEventListener('click', () => answerCheckIn(true));
            modal.root.querySelector('[data-help]').addEventListener('click', () => answerCheckIn(false));
            checkIn = { close: () => { clearInterval(timer); modal.close(); } };
        }

        async function answerCheckIn(ok) {
            try {
                trip = await post(`/api/trips/${tripId}/checkin`, { ok });
                if (checkIn) { checkIn.close(); checkIn = null; }
                if (ok) {
                    sim.wrong = false;
                    toast('Thanks for confirming. Stay safe!', 'success');
                } else {
                    toast('Alerting your contacts now.', 'warn');
                    if (trip.alertId) api(`/api/alerts/${trip.alertId}`).then(adoptAlert).catch(() => { });
                }
                update();
            } catch (error) {
                toast(error.message, 'error');
            }
        }

        async function share() {
            const text = `I'm on my way to ${trip.destinationName} with SafeRoute AI. Follow my trip live: ${trip.trackUrl}`;
            if (navigator.share) {
                try { await navigator.share({ title: 'My Safe Trip', text, url: trip.trackUrl }); return; } catch { /* cancelled */ }
            }
            const modal = openModal(html`
                <button type="button" class="icon-btn modal-close" data-close aria-label="Close">${icon('x')}</button>
                <p class="eyebrow">Live tracking link</p>
                <h2 class="display" style="margin:.3rem 0 .6rem">Let someone follow your trip</h2>
                <p class="small muted">Anyone with this link sees your live position until 2 hours after you arrive.${store.appInfo?.publicLinks ? '' : ' (The app is on localhost, so the link only opens on this computer.)'}</p>
                <p class="code" style="display:block;margin:.8rem 0;padding:.6rem">${trip.trackUrl}</p>
                <div class="grid grid-2">
                    <button type="button" class="btn btn-primary" data-copy>${icon('copy')} Copy link</button>
                    <a class="btn btn-ghost" href="${trip.trackUrl}" target="_blank" rel="noopener">${icon('external')} Open</a>
                </div>
                ${(store.contacts || []).length ? html`<div class="divider"></div><div class="stack" style="gap:.4rem">${store.contacts.map(c => html`
                    <a class="btn btn-ghost btn-sm" href="${waLink(c.phone, text)}" target="_blank" rel="noopener">${icon('chat', 'icon-sm')} Send to ${c.name} on WhatsApp</a>`)}</div>` : ''}`);
            modal.root.querySelector('[data-copy]').addEventListener('click', async () => {
                if (await copyText(trip.trackUrl)) toast('Link copied.', 'success');
            });
        }

        function confirmEnd() {
            const modal = openModal(html`
                <h2 class="display">End this trip?</h2>
                <p class="muted" style="margin:.5rem 0 1.2rem">Live sharing stops and SafeRoute stops checking on you.</p>
                <div class="grid grid-2"><button type="button" class="btn btn-danger" data-yes>End trip</button><button type="button" class="btn btn-ghost" data-close>Keep going</button></div>`);
            modal.root.querySelector('[data-yes]').addEventListener('click', async () => {
                try {
                    await post(`/api/trips/${tripId}/end`);
                    modal.close();
                    store.tripContext = null;
                    store.save();
                    toast('Trip ended.', 'info');
                    navigate('/plan');
                } catch (error) {
                    toast(error.message, 'error');
                }
            });
        }

        const bannerTimer = setInterval(() => { if (riskBanner && Date.now() > riskBannerUntil) { riskBanner = null; renderBanner(); } }, 2000);
        // Server-side state (watchdog) can change without a ping, e.g. a missed check-in: poll gently.
        const statusTimer = setInterval(async () => {
            if (!trip || pinging) return;
            try { trip = await api(`/api/trips/${tripId}`); update(); } catch { /* ignore */ }
        }, 6000);

        function cleanup() {
            if (disposed) return;
            disposed = true;
            clearInterval(simTimer);
            clearInterval(bannerTimer);
            clearInterval(statusTimer);
            if (watchId !== null) navigator.geolocation.clearWatch(watchId);
            checkIn?.close();
            map?.off('dragstart', stopFollowing);
            try { window.speechSynthesis?.cancel(); } catch { /* ignore */ }
            stage.clear('route', 'routes', 'places', 'pins', 'trail');
            stage.clearMe();
        }
        return cleanup;
    },
};

