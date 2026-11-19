// Step 2 of the trip flow: compare routes and start a Safe Trip on the chosen one.
import { api, post } from '../api.js';
import { wireTooltips } from '../charts.js';
import { icon, iconForPlaceType } from '../icons.js';
import * as stage from '../mapstage.js';
import { navigate } from '../router.js';
import { store } from '../store.js';
import { countUp, formatKm, formatMeters, formatMinutes, html, raw, RISK_COLORS, riskKey, toast } from '../ui.js';

const STEPS = ['Finding real routes', 'Scanning street lighting & activity', 'Locating police, hospitals & help', 'Scoring every stretch with the AI model'];
const HIGHLIGHT_TONE = { good: 'tone-good', warn: 'tone-warn', bad: 'tone-bad' };
const PLACE_NAME = { POLICE: 'Police', HOSPITAL: 'Hospital', CLINIC: 'Clinic', PHARMACY: 'Pharmacy', FUEL: 'Fuel station' };

function requestKey(draft) {
    return JSON.stringify([draft.from, draft.to, draft.time, draft.mode]);
}

export default {
    layout: 'map',
    auth: true,
    title: 'Compare routes',
    back: '/plan',
    mount(root) {
        const draft = store.draft;
        if (!draft) { navigate('/plan', { replace: true }); return null; }
        let data = store.results && store.results.key === requestKey(draft) ? store.results.data : null;
        let selectedId = store.selectedRouteId;
        let stepTimer = null;
        let disposed = false;

        root.innerHTML = html`
            <aside class="panel" aria-label="Route options">
                <button type="button" class="sheet-handle" aria-label="Expand or collapse"></button>
                <div class="panel-scroll">
                    <div class="panel-head">
                        <button type="button" class="icon-btn" data-edit aria-label="Change trip">${icon('arrow-left')}</button>
                        <div style="min-width:0">
                            <div class="route-summary"><span class="from-to">${draft.from.label}</span>${icon('arrow-right', 'icon-sm')}<span class="from-to">${draft.to.label}</span></div>
                            <div class="tiny muted">${draft.mode === 'DRIVE' ? 'Cab / auto' : 'Walking'} · leaving ${draft.timeLabel || draft.time}</div>
                        </div>
                    </div>
                    <div data-body></div>
                </div>
                <div class="panel-footer hidden" data-footer></div>
            </aside>
            <div class="map-legend floating" aria-hidden="true">
                <span><span class="legend-line" style="background:${RISK_COLORS.low}"></span>Safer stretch</span>
                <span><span class="legend-line" style="background:${RISK_COLORS.medium}"></span>Take care</span>
                <span><span class="legend-line" style="background:${RISK_COLORS.high}"></span>Riskier stretch</span>
                <span><span class="legend-line" style="background:#8f86a6"></span>Other routes</span>
            </div>`;

        const body = root.querySelector('[data-body]');
        const footer = root.querySelector('[data-footer]');
        root.querySelector('[data-edit]').addEventListener('click', () => navigate('/plan'));
        root.querySelector('.sheet-handle').addEventListener('click', () => root.querySelector('.panel').classList.toggle('collapsed'));
        api('/api/safety-zones').then(stage.drawZones).catch(() => { });

        if (data) render(); else load();

        async function load() {
            footer.classList.add('hidden');
            let step = 0;
            const drawSteps = () => {
                body.innerHTML = html`<div class="loader-steps" aria-live="polite">${STEPS.map((s, i) => html`
                    <div class="loader-step ${i < step ? 'done' : i === step ? 'active' : ''}"><span class="bullet">${i < step ? icon('check', 'icon-sm') : ''}</span>${s}</div>`)}</div>
                    <div class="stack" style="margin-top:1rem"><div class="skeleton" style="height:120px"></div><div class="skeleton" style="height:120px;opacity:.6"></div></div>`;
            };
            drawSteps();
            stepTimer = setInterval(() => { if (step < STEPS.length - 1) { step++; drawSteps(); } }, 1600);
            const from = draft.from;
            const to = draft.to;
            if (from.latitude != null && to.latitude != null) {
                stage.setPins({ latlng: [from.latitude, from.longitude], label: from.label }, { latlng: [to.latitude, to.longitude], label: to.label });
                stage.fit([[from.latitude, from.longitude], [to.latitude, to.longitude]]);
            }
            try {
                const response = await post('/api/routes/recommend', {
                    source: from.label,
                    sourceLatitude: from.latitude ?? null,
                    sourceLongitude: from.longitude ?? null,
                    destination: to.label,
                    destinationLatitude: to.latitude ?? null,
                    destinationLongitude: to.longitude ?? null,
                    travelTime: draft.time,
                    mode: draft.mode,
                });
                if (disposed) return;
                clearInterval(stepTimer);
                data = response;
                selectedId = response.recommendedRouteId;
                store.results = { key: requestKey(draft), data };
                store.selectedRouteId = selectedId;
                store.save();
                render();
            } catch (error) {
                if (disposed) return;
                clearInterval(stepTimer);
                body.innerHTML = html`<div class="empty"><div class="e-icon">${icon('alert', 'icon-xl')}</div>
                    <h3 class="display" style="font-size:1.4rem">We couldn't plan this trip</h3>
                    <p class="muted" style="margin:.5rem 0 1.2rem">${error.message}</p>
                    <div class="row" style="justify-content:center"><button type="button" class="btn btn-primary" data-retry>${icon('refresh')} Try again</button>
                    <button type="button" class="btn btn-ghost" data-change>Change trip</button></div></div>`;
                body.querySelector('[data-retry]').addEventListener('click', load);
                body.querySelector('[data-change]').addEventListener('click', () => navigate('/plan'));
            }
        }

        function selected() {
            return data.routes.find(r => r.routeId === selectedId) || data.routes[0];
        }

        function comparison() {
            const routes = data.routes;
            const best = routes.find(r => r.routeId === data.recommendedRouteId);
            const fastest = routes.reduce((a, b) => (b.travelTimeMinutes < a.travelTimeMinutes ? b : a));
            if (routes.length < 2) return data.demoMode ? 'Live routing is unavailable right now, so this is a rough straight-line estimate.' : 'Only one practical route exists for this trip. Here is how safe it is.';
            if (best === fastest) return `${best.label} is both the safest and the fastest option.`;
            const extra = best.travelTimeMinutes - fastest.travelTimeMinutes;
            const points = Math.round(best.safetyScore - fastest.safetyScore);
            const avoids = fastest.weakestStretch ? ` It avoids the fastest route's weakest stretch (${fastest.weakestStretch.description}).` : '';
            return `${best.label} takes ${extra > 0 ? extra + ' min longer' : 'about the same time'} but is ${points} points safer.${avoids}`;
        }

        function strip(route) {
            const total = route.segments.reduce((s, seg) => s + seg.lengthMeters, 0) || 1;
            return html`<div class="risk-strip" aria-label="Risk along the route">${route.segments.map(seg => html`<span class="${riskKey(seg.level)}" style="width:${(100 * seg.lengthMeters / total).toFixed(2)}%" title="${seg.level}: ${seg.note}"></span>`)}</div>
                <div class="strip-axis"><span>Start</span><span>${formatKm(route.distanceKm)}</span></div>`;
        }

        function scoreRing(route) {
            const C = 2 * Math.PI * 28;
            const color = RISK_COLORS[riskKey(route.riskLevel)];
            return html`<div class="score-ring" aria-label="Safety score ${Math.round(route.safetyScore)} out of 100">
                <svg viewBox="0 0 66 66"><circle class="track" cx="33" cy="33" r="28" fill="none" stroke-width="6"/>
                    <circle class="value" cx="33" cy="33" r="28" fill="none" stroke="${color}" stroke-width="6" stroke-linecap="round" stroke-dasharray="${C}" stroke-dashoffset="${C}" data-offset="${C * (1 - route.safetyScore / 100)}"/></svg>
                <div class="label"><div><b class="num" data-count="${route.safetyScore}">0</b><small>SAFETY</small></div></div></div>`;
        }

        function details(route) {
            return html`<div class="route-details">
                ${route.highlights.map(h => html`<div class="highlight"><span class="h-icon ${HIGHLIGHT_TONE[h.tone]}">${icon(h.icon)}</span>${h.text}</div>`)}
                ${route.weakestStretch ? html`<div class="weak">${icon('alert', 'icon-sm')}<span><strong>Weakest stretch:</strong> ${route.weakestStretch.description}</span></div>` : ''}
                ${route.helpPoints.length ? html`<div class="section-label" style="margin:.6rem 0 .2rem">Help along this route</div>
                    ${route.helpPoints.slice(0, 6).map((p, i) => html`<div class="help-row" data-help="${i}">
                        <span class="h-icon">${icon(iconForPlaceType(p.type))}</span>
                        <span><strong>${p.name}</strong><span class="tiny muted" style="display:block">${PLACE_NAME[p.type] || p.type}${p.open24h ? ' · 24/7' : ''}</span></span>
                        <span class="meta">${formatMeters(p.metersAlong)} in<br>${p.metersFromRoute} m off route</span></div>`)}` : ''}
                <div class="section-label" style="margin:.8rem 0 .2rem">Best time to leave</div>
                <div data-besttime><div class="skeleton" style="height:120px"></div></div>
                <div class="data-note">${icon(route.dataQuality === 'live' ? 'wifi' : 'info', 'icon-sm')}
                    ${route.dataQuality === 'live' ? 'Scored with live OpenStreetMap street data and the Random Forest model.' : 'Street data was unavailable, so this score is estimated.'}</div>
            </div>`;
        }

        function render() {
            const route = selected();
            body.innerHTML = html`
                <div class="compare">${icon('sparkles')}<span>${comparison()}</span></div>
                <div class="stack stagger" data-cards>
                    ${data.routes.map((r, i) => html`<article class="route-card ${r.routeId === route.routeId ? 'selected' : ''}" style="--i:${i}" tabindex="0" role="button" aria-pressed="${r.routeId === route.routeId}" data-id="${r.routeId}">
                        <div class="route-top">${scoreRing(r)}
                            <div class="route-info">
                                <h3>${r.label} ${r.tags.map(t => html`<span class="badge tag-${t.toLowerCase()}">${t === 'Recommended' ? icon('shield-check') : icon('clock')} ${t}</span>`)}</h3>
                                <div class="metrics"><span>${icon('clock')} ${formatMinutes(r.travelTimeMinutes)}</span><span>${icon('route')} ${formatKm(r.distanceKm)}</span>
                                    <span class="badge tone-${riskKey(r.riskLevel)}">${r.riskLevel}</span></div>
                            </div></div>
                        ${strip(r)}
                        ${r.routeId === route.routeId ? details(r) : html`<p class="small muted" style="margin-top:.6rem">${r.warning}</p>`}
                    </article>`)}
                </div>
                <p class="tiny muted" style="margin-top:1rem">${data.message} Estimates only; always stay alert.</p>`;

            body.querySelectorAll('.score-ring .value').forEach(c => requestAnimationFrame(() => { c.style.strokeDashoffset = c.dataset.offset; }));
            body.querySelectorAll('[data-count]').forEach(n => countUp(n, Number(n.dataset.count)));
            body.querySelectorAll('.route-card').forEach(card => {
                const pick = () => select(card.dataset.id);
                card.addEventListener('click', event => { if (!event.target.closest('[data-help]')) pick(); });
                card.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); pick(); } });
            });
            body.querySelectorAll('[data-help]').forEach(row => row.addEventListener('click', () => {
                const p = selected().helpPoints[Number(row.dataset.help)];
                stage.flyTo([p.latitude, p.longitude], 17);
            }));

            drawMap(true);
            renderFooter();
            loadBestTime(route);
        }

        // ------------------------------------------------------------ best time to leave
        const profiles = new Map();
        const hourShort = h => (h === 0 ? '12a' : h < 12 ? `${h}a` : h === 12 ? '12p' : `${h - 12}p`);
        const hourLong = h => (h === 0 ? '12 AM' : h < 12 ? `${h} AM` : h === 12 ? '12 PM' : `${h - 12} PM`);

        async function loadBestTime(route) {
            const host = body.querySelector('[data-besttime]');
            if (!host) return;
            const startHour = Number(String(draft.time || '').split(':')[0]) || new Date().getHours();
            try {
                if (!profiles.has(route.routeId)) {
                    profiles.set(route.routeId, await post('/api/routes/time-profile', { geometry: route.geometry, startHour, hours: 12 }));
                }
                if (disposed || selected().routeId !== route.routeId) return;
                drawBestTime(host, profiles.get(route.routeId), startHour);
            } catch (error) {
                host.innerHTML = html`<p class="small muted">${error.message}</p>`.toString();
            }
        }

        function drawBestTime(host, profile, startHour) {
            const current = profile.find(p => p.hour === startHour) || profile[0];
            const best = profile.reduce((a, b) => (b.safetyScore > a.safetyScore ? b : a));
            const gain = Math.round(best.safetyScore - current.safetyScore);
            const W = 380, H = 150, top = 22, base = 118, barW = 18;
            const step = W / profile.length;
            const bars = profile.map((p, i) => {
                const x = i * step + (step - barW) / 2;
                const h = Math.max(3, (base - top) * p.safetyScore / 100);
                const y = base - h;
                const r = Math.min(4, h / 2);
                const path = `M${x},${base} V${y + r} Q${x},${y} ${x + r},${y} H${x + barW - r} Q${x + barW},${y} ${x + barW},${y + r} V${base} Z`;
                const mark = p === best && gain >= 3 ? 'Best' : p.hour === startHour ? 'You' : '';
                return `<path class="bar-rect" d="${path}" opacity="${p === best || p.hour === startHour ? 1 : 0.55}"
                        data-tip="Leave at ${hourLong(p.hour)}: safety ${Math.round(p.safetyScore)}/100 (${p.riskLevel})"></path>
                    ${mark ? `<text class="t-ink" x="${x + barW / 2}" y="${y - 6}" text-anchor="middle" font-size="11" font-weight="800">${mark}</text>` : ''}
                    <text class="t-muted" x="${x + barW / 2}" y="${base + 16}" text-anchor="middle" font-size="10.5">${hourShort(p.hour)}</text>`;
            }).join('');
            const message = gain >= 5
                ? html`Leaving at <strong>${hourLong(best.hour)}</strong> instead of ${hourLong(current.hour)} raises safety from <strong>${Math.round(current.safetyScore)}</strong> to <strong>${Math.round(best.safetyScore)}</strong>.`
                : html`Your departure time is already among the safest for this route in the next 12 hours.`;
            host.innerHTML = html`<p class="small" style="margin-bottom:.4rem">${message}</p>
                <svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Safety score by departure hour">
                    <line class="grid-line" x1="0" x2="${W}" y1="${base}" y2="${base}" stroke-width="1"></line>${raw(bars)}</svg>`.toString();
            wireTooltips(host);
        }

        function select(id) {
            if (id === selectedId) return;
            selectedId = id;
            store.selectedRouteId = id;
            store.save();
            render();
        }

        function drawMap(fitView) {
            const route = selected();
            stage.drawRoutes(data.routes, route.routeId, { onSelect: select });
            stage.drawPlaces(route.helpPoints);
            stage.setPins({ latlng: [data.source.latitude, data.source.longitude], label: data.source.displayName },
                { latlng: [data.destination.latitude, data.destination.longitude], label: data.destination.displayName });
            if (fitView) stage.fit(data.routes.flatMap(r => r.geometry));
        }

        function renderFooter() {
            const route = selected();
            const hasContacts = (store.contacts || []).length > 0;
            footer.classList.remove('hidden');
            footer.innerHTML = html`
                <label class="switch" style="margin-bottom:.75rem"><input type="checkbox" data-notify ${hasContacts ? 'checked' : 'disabled'}><span class="track"></span>
                    <span class="small">${hasContacts ? html`<strong>Share this trip live</strong> with ${store.contacts.length} contact${store.contacts.length > 1 ? 's' : ''}` : html`<span class="muted">Add contacts to share trips live</span>`}</span></label>
                <div class="row" style="gap:.5rem">
                    <button type="button" class="btn btn-primary btn-lg" style="flex:1" data-start>${icon('navigate')} Start Safe Trip on ${route.label}</button>
                    <button type="button" class="btn btn-ghost btn-lg" data-sim title="Simulate the journey (for demos)" aria-label="Simulate this trip">${icon('play')}</button>
                </div>`;
            footer.querySelector('[data-start]').addEventListener('click', e => start(false, e.currentTarget));
            footer.querySelector('[data-sim]').addEventListener('click', e => start(true, e.currentTarget));
        }

        async function start(simulated, button) {
            const route = selected();
            button.classList.add('loading');
            try {
                const trip = await post('/api/trips', {
                    originName: data.source.name,
                    destinationName: data.destination.name,
                    destinationLatitude: data.destination.latitude,
                    destinationLongitude: data.destination.longitude,
                    geometry: route.geometry,
                    segments: route.segments,
                    distanceKm: route.distanceKm,
                    durationMinutes: route.travelTimeMinutes,
                    safetyScore: route.safetyScore,
                    mode: data.mode,
                    notifyContacts: footer.querySelector('[data-notify]').checked,
                    simulated,
                });
                store.tripContext = { tripId: trip.id, helpPoints: route.helpPoints, segments: route.segments, geometry: route.geometry, label: route.label };
                store.save();
                navigate(`/trip/${trip.id}${simulated ? '?sim=1' : ''}`);
            } catch (error) {
                button.classList.remove('loading');
                toast(error.message, 'error');
            }
        }

        return () => {
            disposed = true;
            clearInterval(stepTimer);
            stage.clear('routes', 'route', 'places', 'pins', 'zones');
        };
    },
};
