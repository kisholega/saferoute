// Step 1 of the trip flow: where from, where to, when, how.
import { api } from '../api.js';
import { attachAutocomplete } from '../autocomplete.js';
import { icon } from '../icons.js';
import * as stage from '../mapstage.js';
import { navigate } from '../router.js';
import { store } from '../store.js';
import {
    avatarStyle, currentPosition, debounce, firstName, greeting, html, initials, timeInput, toast,
} from '../ui.js';

const TIPS = [
    'Share your trip with someone you trust before you leave. Safe Trip does it in one tap.',
    'Main roads with open shops are usually safer at night than quiet shortcuts.',
    'Keep your phone charged above 20% on night journeys; SafeRoute shares your battery level in alerts.',
    'If something feels wrong, trust your instinct. Hold SOS, or use the fake call to leave a situation.',
    'Pre-book cabs and check the number plate and driver photo before getting in.',
    'Save 112 (emergency) and 1091 (women helpline) on speed dial.',
];

export default {
    layout: 'map',
    auth: true,
    title: 'Plan a trip',
    mount(root) {
        const draft = store.draft || {};
        let timeMode = draft.timeMode || 'now';
        let mode = draft.mode || 'WALK';
        let here = null; // last known GPS position

        root.innerHTML = html`
            <aside class="panel" aria-label="Plan a trip">
                <button type="button" class="sheet-handle" aria-label="Expand or collapse"></button>
                <div class="panel-scroll">
                    <p class="eyebrow">${greeting()}, ${firstName(store.user.name)}</p>
                    <h1 class="display greeting">Where are you <em class="grad-text">headed</em> today?</h1>
                    <form novalidate data-form>
                        <div class="trip-box">
                            <div class="loc-row">
                                <span class="loc-marker"><span class="ring"></span></span>
                                <input data-from placeholder="Starting point" aria-label="Starting point">
                                <button type="button" class="locate" data-locate title="Use my current location" aria-label="Use my current location">${icon('locate')}</button>
                            </div>
                            <span class="connector"></span>
                            <button type="button" class="swap" data-swap aria-label="Swap start and destination">${icon('swap')}</button>
                            <div class="loc-row">
                                <span class="loc-marker"><span class="pin"></span></span>
                                <input data-to placeholder="Where to? Try “Whitefield”" aria-label="Destination">
                            </div>
                        </div>
                        <p class="hint" style="margin:.55rem 0 0 .3rem">${icon('pin', 'icon-sm')} Or tap the map to drop a pin.</p>

                        <div class="section-label">When</div>
                        <div class="chips" data-when>
                            <button type="button" class="chip" data-when-mode="now">${icon('clock')} Now</button>
                            <button type="button" class="chip" data-when-mode="soon">In 30 min</button>
                            <button type="button" class="chip" data-when-mode="night">${icon('moon')} Tonight 9 PM</button>
                            <button type="button" class="chip" data-when-mode="pick">Pick a time</button>
                        </div>
                        <div class="input-wrap" data-time-wrap style="margin-top:.55rem" hidden>${icon('clock')}<input type="time" class="input" data-time value="${draft.time || timeInput()}"></div>

                        <div class="section-label">How are you travelling?</div>
                        <div class="segmented" data-mode>
                            <button type="button" data-m="WALK">${icon('walk')} Walking</button>
                            <button type="button" data-m="DRIVE">${icon('car')} Cab / auto</button>
                        </div>
                        <button type="submit" class="btn btn-primary btn-lg btn-block" style="margin-top:1.3rem">${icon('shield-check')} Find safe routes ${icon('arrow-right', 'arrow')}</button>
                    </form>
                    <div data-ready style="margin-top:1rem"></div>
                    <div data-recent></div>
                    <div class="section-label">Safety tip</div>
                    <div class="tip-card">${icon('sparkles')}<p class="small tip-text" data-tip></p></div>
                </div>
            </aside>
            <div class="floating map-controls">
                <button type="button" class="icon-btn" data-center aria-label="Show my location">${icon('locate')}</button>
            </div>`;

        const map = stage.ensureMap();
        const panel = root.querySelector('.panel');
        const fromInput = root.querySelector('[data-from]');
        const toInput = root.querySelector('[data-to]');
        const bias = () => (map ? map.getCenter() : null);
        const fromAc = attachAutocomplete(fromInput, fromInput.parentElement, { getBias: bias, allowCurrentLocation: true, onPick: showPins });
        const toAc = attachAutocomplete(toInput, toInput.parentElement, { getBias: bias, onPick: showPins });
        if (draft.from) setField(fromAc, fromInput, draft.from);
        if (draft.to) setField(toAc, toInput, draft.to);

        function setField(ac, input, place) {
            if (place.latitude != null) ac.set(place); else input.value = place.label || '';
        }

        // --- when / mode controls
        const whenButtons = root.querySelectorAll('[data-when-mode]');
        const timeWrap = root.querySelector('[data-time-wrap]');
        const syncWhen = () => {
            whenButtons.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.whenMode === timeMode)));
            timeWrap.hidden = timeMode !== 'pick';
        };
        whenButtons.forEach(b => b.addEventListener('click', () => { timeMode = b.dataset.whenMode; syncWhen(); }));
        syncWhen();
        const modeButtons = root.querySelectorAll('[data-m]');
        const syncMode = () => modeButtons.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.m === mode)));
        modeButtons.forEach(b => b.addEventListener('click', () => { mode = b.dataset.m; syncMode(); }));
        syncMode();

        root.querySelector('[data-swap]').addEventListener('click', () => {
            const a = fromAc.get() || (fromAc.text() ? { label: fromAc.text() } : null);
            const b = toAc.get() || (toAc.text() ? { label: toAc.text() } : null);
            fromAc.clear(); toAc.clear();
            if (b) setField(fromAc, fromInput, b);
            if (a) setField(toAc, toInput, a);
            showPins();
        });

        // --- location
        async function locate(fillFrom) {
            const position = await currentPosition();
            if (!position) {
                if (fillFrom) toast('Could not get your location. Allow location access, or type a starting point.', 'warn');
                return null;
            }
            here = position;
            stage.setMe([position.latitude, position.longitude], position.accuracy);
            if (fillFrom) {
                fromAc.set({ label: 'Current location', latitude: position.latitude, longitude: position.longitude, current: true });
                api(`/api/locations/reverse?lat=${position.latitude}&lon=${position.longitude}`).then(place => {
                    if (fromInput.value === 'Current location') {
                        fromAc.set({ label: place.name.startsWith('Near ') ? place.name : `${place.name} (you)`, latitude: position.latitude, longitude: position.longitude, current: true });
                    }
                }).catch(() => { });
                showPins();
            }
            return position;
        }
        root.querySelector('[data-locate]').addEventListener('click', () => locate(true));
        root.querySelector('[data-center]').addEventListener('click', async () => {
            const p = here || await locate(false);
            if (p) stage.flyTo([p.latitude, p.longitude], 16);
        });

        // Start from the current location automatically when permission was already given.
        (async () => {
            let granted = false;
            try { granted = (await navigator.permissions?.query({ name: 'geolocation' }))?.state === 'granted'; } catch { /* unsupported */ }
            const p = granted ? await locate(!draft.from) : null;
            const center = p ? [p.latitude, p.longitude] : (store.appInfo ? [store.appInfo.mapCenter.latitude, store.appInfo.mapCenter.longitude] : stage.DEFAULT_CENTER);
            if (!draft.from && !draft.to) stage.flyTo(center, 15);
            loadHelp(center);
        })();

        // --- map layers
        api('/api/safety-zones').then(stage.drawZones).catch(() => { });
        const loadHelp = debounce(async center => {
            try {
                const places = await api(`/api/safe-locations?latitude=${center[0]}&longitude=${center[1]}&radius=1500`);
                stage.drawPlaces(places.slice(0, 40));
            } catch { /* help points are a bonus on this screen */ }
        }, 400);

        function showPins() {
            const from = fromAc.get();
            const to = toAc.get();
            stage.setPins(from ? { latlng: [from.latitude, from.longitude], label: from.label } : null,
                to ? { latlng: [to.latitude, to.longitude], label: to.label } : null);
            const points = [from, to].filter(Boolean).map(p => [p.latitude, p.longitude]);
            if (points.length === 2) stage.fit(points, 15);
            else if (points.length === 1) stage.flyTo(points[0], 15);
            if (to) loadHelp([to.latitude, to.longitude]);
        }
        if (draft.from || draft.to) setTimeout(showPins, 300);

        // Tap the map: choose this point as start or destination.
        const offClick = stage.onMapClick(event => {
            const { lat, lng } = event.latlng;
            const box = document.createElement('div');
            box.innerHTML = html`<strong data-name>Dropped pin</strong><br><span class="tiny muted">${lat.toFixed(5)}, ${lng.toFixed(5)}</span>
                <div class="map-popup-actions"><button type="button" data-as="from">Start here</button><button type="button" data-as="to">Go here</button></div>`.toString();
            let place = { label: 'Dropped pin', latitude: lat, longitude: lng };
            api(`/api/locations/reverse?lat=${lat}&lon=${lng}`).then(p => {
                place = { label: p.name, displayName: p.displayName, latitude: lat, longitude: lng };
                box.querySelector('[data-name]').textContent = p.name;
            }).catch(() => { });
            const popup = L.popup({ closeButton: false, offset: [0, -4] }).setLatLng(event.latlng).setContent(box).openOn(map);
            box.querySelectorAll('[data-as]').forEach(b => b.addEventListener('click', () => {
                (b.dataset.as === 'from' ? fromAc : toAc).set(place);
                map.closePopup(popup);
                showPins();
            }));
        });

        // --- contacts readiness & recent trips
        const ready = root.querySelector('[data-ready]');
        const contacts = store.contacts || [];
        ready.innerHTML = contacts.length
            ? html`<div class="ready-card"><div class="avatars">${contacts.slice(0, 4).map(c => html`<span class="avatar" style="${avatarStyle(c.name)}">${initials(c.name)}</span>`)}</div>
                <div class="small"><strong>${contacts.length} trusted contact${contacts.length > 1 ? 's' : ''}</strong> ready to follow your trip</div></div>`
            : html`<a class="ready-card tone-warn" href="#/contacts" style="border-color:transparent">${icon('alert')}
                <div class="small"><strong>No emergency contacts yet.</strong> Add someone so they can be alerted.</div>${icon('chevron-right')}</a>`;

        api('/api/routes/history').then(history => {
            const seen = new Set();
            const recent = history.filter(h => h.destinationLatitude != null && !seen.has(h.destinationLocation) && seen.add(h.destinationLocation)).slice(0, 4);
            if (!recent.length) return;
            const host = root.querySelector('[data-recent]');
            host.innerHTML = html`<div class="section-label">Recent destinations</div><div class="chips">${recent.map((h, i) =>
                html`<button type="button" class="chip" data-recent="${i}">${icon('history')} ${h.destinationLocation}</button>`)}</div>`;
            host.querySelectorAll('[data-recent]').forEach(b => b.addEventListener('click', () => {
                const h = recent[Number(b.dataset.recent)];
                toAc.set({ label: h.destinationLocation, latitude: h.destinationLatitude, longitude: h.destinationLongitude });
                showPins();
            }));
        }).catch(() => { });

        // --- tips carousel
        const tipNode = root.querySelector('[data-tip]');
        let tipIndex = Math.floor(Math.random() * TIPS.length);
        tipNode.textContent = TIPS[tipIndex];
        const tipTimer = setInterval(() => {
            tipNode.style.opacity = 0;
            setTimeout(() => { tipIndex = (tipIndex + 1) % TIPS.length; tipNode.textContent = TIPS[tipIndex]; tipNode.style.opacity = 1; }, 400);
        }, 7000);

        // --- mobile bottom sheet
        root.querySelector('.sheet-handle').addEventListener('click', () => panel.classList.toggle('collapsed'));

        // --- submit
        root.querySelector('[data-form]').addEventListener('submit', event => {
            event.preventDefault();
            const from = fromAc.get() || (fromAc.text() ? { label: fromAc.text() } : (here ? { label: 'Current location', latitude: here.latitude, longitude: here.longitude } : null));
            const to = toAc.get() || (toAc.text() ? { label: toAc.text() } : null);
            if (!from || !to) {
                const box = root.querySelector('.trip-box');
                box.classList.add('shake');
                setTimeout(() => box.classList.remove('shake'), 450);
                toast(!from ? 'Where are you starting from? Type a place or use your location.' : 'Where are you going?', 'warn');
                (!from ? fromInput : toInput).focus();
                return;
            }
            const now = new Date();
            let time = timeInput(now);
            let timeLabel = 'now';
            if (timeMode === 'soon') { const t = new Date(now.getTime() + 30 * 60000); time = timeInput(t); timeLabel = `at ${time}`; }
            if (timeMode === 'night') { time = '21:00'; timeLabel = 'tonight at 9 PM'; }
            if (timeMode === 'pick') { time = root.querySelector('[data-time]').value || time; timeLabel = `at ${time}`; }
            store.draft = { from, to, time, timeLabel, timeMode, mode };
            store.results = null;
            store.selectedRouteId = null;
            store.save();
            navigate('/routes');
        });

        return () => {
            offClick();
            clearInterval(tipTimer);
            fromAc.destroy();
            toAc.destroy();
            stage.clear('places', 'pins', 'zones');
            map?.closePopup();
        };
    },
};

