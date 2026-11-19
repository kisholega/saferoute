// Safety map: live help points, flagged zones, community reports, and "report a spot".
import { api, post } from '../api.js';
import { attachAutocomplete } from '../autocomplete.js';
import { icon, iconForPlaceType } from '../icons.js';
import * as stage from '../mapstage.js';
import { store } from '../store.js';
import { currentPosition, formatKm, html, openModal, toast } from '../ui.js';

const RADII = [[1000, '1 km'], [2500, '2.5 km'], [5000, '5 km']];

const TYPE_LABEL = { POLICE: 'Police', HOSPITAL: 'Hospital', CLINIC: 'Clinic', PHARMACY: 'Pharmacy', FUEL: 'Fuel' };
const CATEGORY_ICON = { POOR_LIGHTING: 'lamp', HARASSMENT: 'alert', ISOLATED: 'people', UNSAFE_CROWD: 'users', OTHER: 'flag', SAFE_SPOT: 'heart' };

export default {
    layout: 'map',
    auth: true,
    title: 'Safety map',
    back: '/plan',
    mount(root) {
        const map = stage.ensureMap();
        const layers = { help: true, zones: true, reports: true };
        let filter = 'ALL';
        let places = [];
        let reports = [];
        let zones = [];
        let reporting = false;
        let lastCenter = null;
        let centerLabel = '';
        let radius = 2500;

        root.innerHTML = html`
            <aside class="panel" aria-label="Help nearby">
                <button type="button" class="sheet-handle" aria-label="Expand or collapse"></button>
                <div class="panel-scroll">
                    <p class="eyebrow">Live from OpenStreetMap</p>
                    <h1 class="display" style="margin:.3rem 0 .4rem">Help nearby</h1>
                    <p class="small muted">Find police stations, hospitals, clinics, pharmacies and fuel stations around any place: search below, or tap the map.</p>
                    <div class="trip-box" style="margin-top:1rem">
                        <div class="loc-row">
                            <span class="loc-marker"><span class="pin"></span></span>
                            <input data-place placeholder="Search an area, e.g. Koramangala" aria-label="Area to search for help">
                            <button type="button" class="locate" style="margin-right:4px" data-here title="Use my current location" aria-label="Use my current location">${icon('locate')}</button>
                        </div>
                    </div>
                    <div class="row between wrap" style="margin-top:.8rem;gap:.5rem">
                        <span class="small" data-around></span>
                        <div class="segmented" style="padding:3px" data-radius>
                            ${RADII.map(([value, label]) => html`<button type="button" style="min-height:32px;font-size:.8rem;padding:.25rem .6rem" data-r="${value}" aria-pressed="${value === radius}">${label}</button>`)}
                        </div>
                    </div>
                    <div class="chips" style="margin:1rem 0" data-filter>
                        ${['ALL', 'POLICE', 'HOSPITAL', 'PHARMACY', 'FUEL'].map(t => html`<button type="button" class="chip" data-type="${t}" aria-pressed="${t === filter}">
                            ${t === 'ALL' ? '' : icon(iconForPlaceType(t))} ${t === 'ALL' ? 'All' : TYPE_LABEL[t]}</button>`)}
                    </div>
                    <div class="stack" data-list></div>
                </div>
            </aside>
            <div class="layer-bar interactive">
                <button type="button" class="chip" data-layer="help" aria-pressed="true">${icon('police')} Help points</button>
                <button type="button" class="chip" data-layer="zones" aria-pressed="true" hidden>${icon('flag')} Risk zones</button>
                <button type="button" class="chip" data-layer="reports" aria-pressed="true">${icon('users')} Community reports</button>
            </div>
            <div data-search></div>
            <button type="button" class="btn btn-dark btn-lg report-fab interactive" data-report>${icon('flag')} Report a spot</button>
            <div class="floating map-controls" style="bottom:172px"><button type="button" class="icon-btn" data-locate aria-label="Go to my location">${icon('locate')}</button></div>
            <div data-hint></div>`;

        const list = root.querySelector('[data-list]');
        root.querySelector('.sheet-handle').addEventListener('click', () => root.querySelector('.panel').classList.toggle('collapsed'));

        /**
         * Loads help points and reports around a point.
         * @param label  what to call the point ("Koramangala", "your location", "this spot")
         * @param fly    move the map there
         */
        let loadToken = 0;
        async function loadAround(center, label = 'the map centre', fly = false) {
            const token = ++loadToken;
            lastCenter = center;
            centerLabel = label;
            root.querySelector('[data-search]').innerHTML = '';
            stage.setPins(null, { latlng: center, label });
            if (fly) stage.flyTo(center, radius >= 5000 ? 13 : radius >= 2500 ? 14 : 15);
            root.querySelector('[data-around]').innerHTML = html`${icon('pin', 'icon-sm')} Around <strong>${label}</strong>`.toString();
            list.innerHTML = html`${[0, 1, 2, 3].map(() => html`<div class="skeleton" style="height:60px"></div>`)}`;
            const [p, r] = await Promise.allSettled([
                api(`/api/safe-locations?latitude=${center[0]}&longitude=${center[1]}&radius=${radius}`),
                api(`/api/reports?latitude=${center[0]}&longitude=${center[1]}&radiusKm=${Math.max(2, radius / 1000 + 1)}`),
            ]);
            if (token !== loadToken) return; // a newer search started meanwhile
            places = p.status === 'fulfilled' ? p.value : [];
            reports = r.status === 'fulfilled' ? r.value : [];
            draw();
            if (p.status === 'rejected') list.innerHTML = html`<div class="alert-box tone-warn">${icon('wifi')}<div>${p.reason.message}</div></div>`.toString();
        }

        function draw() {
            const visible = places.filter(p => filter === 'ALL' || p.type === filter || (filter === 'HOSPITAL' && p.type === 'CLINIC'));
            if (layers.help) stage.drawPlaces(visible.slice(0, 80)); else stage.clear('places');
            if (layers.reports) stage.drawReports(reports); else stage.clear('reports');
            if (layers.zones) stage.drawZones(zones); else stage.clear('zones');
            const counts = places.reduce((acc, p) => { acc[p.type] = (acc[p.type] || 0) + 1; return acc; }, {});
            const summary = places.length
                ? html`<p class="tiny muted" style="margin-bottom:.4rem">${places.length} help points within ${formatKm(radius / 1000)}:
                    ${counts.POLICE || 0} police · ${(counts.HOSPITAL || 0) + (counts.CLINIC || 0)} hospitals/clinics · ${counts.PHARMACY || 0} pharmacies · ${counts.FUEL || 0} fuel</p>`
                : '';
            list.innerHTML = visible.length ? html`${summary}${visible.slice(0, 40).map((p, i) => html`<div class="help-row" data-i="${i}">
                <span class="h-icon">${icon(iconForPlaceType(p.type))}</span>
                <span style="min-width:0"><strong>${p.name}</strong><span class="tiny muted" style="display:block">${TYPE_LABEL[p.type] || p.type}${p.open24h ? ' · open 24/7' : ''}</span></span>
                <span class="meta">${formatKm(p.distanceKm)}<br><a href="https://www.google.com/maps/dir/?api=1&destination=${p.latitude},${p.longitude}" target="_blank" rel="noopener" data-directions>Directions</a></span></div>`)}`
                : places.length
                    ? html`${summary}<p class="small muted">None of this type within ${formatKm(radius / 1000)}. Try "All" or a bigger radius.</p>`
                    : html`<div class="empty" style="padding:1.5rem .5rem"><div class="e-icon">${icon('police', 'icon-xl')}</div>
                        <strong>No help points mapped within ${formatKm(radius / 1000)} of ${centerLabel}</strong>
                        <p class="small" style="margin-top:.4rem">Try a bigger radius, search a nearby area, or tap another spot on the map.</p>
                        ${radius < 5000 ? html`<button type="button" class="btn btn-soft btn-sm" style="margin-top:.8rem" data-wider>Search within 5 km</button>` : ''}</div>`;
            list.querySelector('[data-wider]')?.addEventListener('click', () => setRadius(5000));
            list.querySelectorAll('[data-i]').forEach(row => row.addEventListener('click', event => {
                if (event.target.closest('[data-directions]')) return;
                const place = visible[Number(row.dataset.i)];
                stage.flyTo([place.latitude, place.longitude], 17);
            }));
        }

        function setRadius(value) {
            radius = value;
            root.querySelectorAll('[data-r]').forEach(b => b.setAttribute('aria-pressed', String(Number(b.dataset.r) === radius)));
            if (lastCenter) loadAround(lastCenter, centerLabel, true);
        }
        root.querySelectorAll('[data-r]').forEach(b => b.addEventListener('click', () => setRadius(Number(b.dataset.r))));

        // --- search any area
        const placeInput = root.querySelector('[data-place]');
        const placeAc = attachAutocomplete(placeInput, placeInput.parentElement, {
            getBias: () => (map ? map.getCenter() : null),
            allowCurrentLocation: true,
            onPick: place => {
                placeInput.blur();
                if (place.current) stage.setMe([place.latitude, place.longitude], place.accuracy || 0);
                loadAround([place.latitude, place.longitude], place.current ? 'your location' : place.label, true);
            },
        });
        placeInput.addEventListener('keydown', async event => {
            // Enter without picking a suggestion: look the typed text up.
            if (event.key !== 'Enter' || placeAc.get() || !placeInput.value.trim()) return;
            event.preventDefault();
            try {
                const results = await api(`/api/locations/search?query=${encodeURIComponent(placeInput.value.trim())}`);
                if (!results.length) { toast('No place found with that name.', 'warn'); return; }
                placeAc.set({ label: results[0].name, latitude: results[0].latitude, longitude: results[0].longitude });
                loadAround([results[0].latitude, results[0].longitude], results[0].name, true);
            } catch (error) {
                toast(error.message, 'error');
            }
        });
        root.querySelector('[data-here]').addEventListener('click', () => locateMe());

        root.querySelectorAll('[data-type]').forEach(b => b.addEventListener('click', () => {
            filter = b.dataset.type;
            root.querySelectorAll('[data-type]').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
            draw();
        }));
        root.querySelectorAll('[data-layer]').forEach(b => b.addEventListener('click', () => {
            layers[b.dataset.layer] = !layers[b.dataset.layer];
            b.setAttribute('aria-pressed', String(layers[b.dataset.layer]));
            draw();
        }));

        const onMove = () => {
            if (!lastCenter || reporting) return;
            const c = map.getCenter();
            const moved = map.distance(c, L.latLng(lastCenter)) > 900;
            const host = root.querySelector('[data-search]');
            if (moved && !host.innerHTML) {
                host.innerHTML = html`<button type="button" class="btn btn-dark btn-sm search-area interactive">${icon('refresh', 'icon-sm')} Search this area</button>`.toString();
                host.querySelector('button').addEventListener('click', () => {
                    placeAc.clear();
                    loadAround([c.lat, c.lng], 'this area');
                    nameSpot([c.lat, c.lng]);
                });
            }
        };
        map?.on('moveend', onMove);

        /** Replaces a generic label ("this spot") with a real street / area name once it is known. */
        function nameSpot(center) {
            api(`/api/locations/reverse?lat=${center[0]}&lon=${center[1]}`).then(place => {
                if (lastCenter !== center) return;
                centerLabel = place.name;
                placeAc.set({ label: place.name, latitude: center[0], longitude: center[1] });
                root.querySelector('[data-around]').innerHTML = html`${icon('pin', 'icon-sm')} Around <strong>${place.name}</strong>`.toString();
                stage.setPins(null, { latlng: center, label: place.name });
                draw();
            }).catch(() => { });
        }

        async function locateMe() {
            const p = await currentPosition();
            if (!p) { toast('Location unavailable. Allow location access, or search an area instead.', 'warn'); return; }
            stage.setMe([p.latitude, p.longitude], p.accuracy);
            placeAc.set({ label: 'Current location', latitude: p.latitude, longitude: p.longitude, current: true });
            loadAround([p.latitude, p.longitude], 'your location', true);
        }
        root.querySelector('[data-locate]').addEventListener('click', locateMe);

        // --- report a spot
        const reportButton = root.querySelector('[data-report]');
        reportButton.addEventListener('click', () => {
            reporting = !reporting;
            document.body.classList.toggle('crosshair-mode', reporting);
            reportButton.innerHTML = html`${icon(reporting ? 'x' : 'flag')} ${reporting ? 'Cancel' : 'Report a spot'}`.toString();
            root.querySelector('[data-hint]').innerHTML = reporting
                ? html`<div class="trip-banner reporting-hint interactive"><span class="b-icon">${icon('pin', 'icon-lg')}</span><div><strong>Tap the spot on the map</strong><span>Then tell us what it's like there.</span></div></div>`.toString()
                : '';
        });
        const offClick = stage.onMapClick(event => {
            if (!reporting) {
                // Tap anywhere: offer to show help around that spot.
                const center = [event.latlng.lat, event.latlng.lng];
                const box = document.createElement('div');
                box.innerHTML = html`<strong>This spot</strong><div class="map-popup-actions">
                    <button type="button" data-help-here>${icon('police', 'icon-sm')} Show help around here</button></div>`.toString();
                const popup = L.popup({ closeButton: false, offset: [0, -4] }).setLatLng(event.latlng).setContent(box).openOn(map);
                box.querySelector('[data-help-here]').addEventListener('click', () => {
                    map.closePopup(popup);
                    placeAc.clear();
                    loadAround(center, 'this spot');
                    nameSpot(center);
                });
                return;
            }
            reporting = false;
            document.body.classList.remove('crosshair-mode');
            reportButton.innerHTML = html`${icon('flag')} Report a spot`.toString();
            root.querySelector('[data-hint]').innerHTML = '';
            openReport(event.latlng);
        });

        async function openReport(latlng) {
            let categories = [];
            try { categories = await api('/api/reports/categories'); } catch { /* fall back below */ }
            let chosen = null;
            const modal = openModal(html`
                <button type="button" class="icon-btn modal-close" data-close aria-label="Close">${icon('x')}</button>
                <p class="eyebrow">Community report</p>
                <h2 class="display" style="margin:.3rem 0 1rem">What is this spot like?</h2>
                <div class="category-grid">${categories.map(c => html`<button type="button" class="${c.positive ? 'positive' : ''}" data-cat="${c.value}" aria-pressed="false">
                    <span class="c-icon">${icon(CATEGORY_ICON[c.value] || 'flag')}</span>${c.label}</button>`)}</div>
                <div class="field" style="margin-top:1rem"><label for="rNote">Add a note <span class="muted">(optional)</span></label>
                    <textarea id="rNote" class="input" rows="2" maxlength="280" placeholder="e.g. Street lights not working after 9 PM"></textarea></div>
                <p class="tiny muted" style="margin:.5rem 0 1rem">Reports are anonymous to other users and fade out after 90 days.</p>
                <button type="button" class="btn btn-primary btn-lg btn-block" data-submit disabled>Submit report</button>`);
            modal.root.querySelectorAll('[data-cat]').forEach(b => b.addEventListener('click', () => {
                chosen = b.dataset.cat;
                modal.root.querySelectorAll('[data-cat]').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
                modal.root.querySelector('[data-submit]').disabled = false;
            }));
            modal.root.querySelector('[data-submit]').addEventListener('click', async event => {
                event.currentTarget.classList.add('loading');
                try {
                    const report = await post('/api/reports', { latitude: latlng.lat, longitude: latlng.lng, category: chosen, note: modal.root.querySelector('#rNote').value });
                    reports = [report, ...reports];
                    layers.reports = true;
                    root.querySelector('[data-layer="reports"]').setAttribute('aria-pressed', 'true');
                    draw();
                    modal.close();
                    toast('Thank you! Your report now helps score routes for everyone.', 'success');
                } catch (error) {
                    event.currentTarget.classList.remove('loading');
                    toast(error.message, 'error');
                }
            });
        }

        // --- start
        api('/api/safety-zones').then(z => {
            zones = z;
            root.querySelector('[data-layer="zones"]').hidden = !zones.length;
            draw();
        }).catch(() => { });
        (async () => {
            const fallback = store.appInfo ? [store.appInfo.mapCenter.latitude, store.appInfo.mapCenter.longitude] : stage.DEFAULT_CENTER;
            let center = fallback;
            let label = store.appInfo?.city ? `central ${store.appInfo.city}` : 'the city centre';
            try {
                if ((await navigator.permissions?.query({ name: 'geolocation' }))?.state === 'granted') {
                    const p = await currentPosition();
                    if (p) { center = [p.latitude, p.longitude]; label = 'your location'; stage.setMe(center, p.accuracy); }
                }
            } catch { /* use fallback */ }
            loadAround(center, label, true);
            if (label !== 'your location') setTimeout(() => placeInput.focus(), 400);
        })();

        return () => {
            offClick();
            placeAc.destroy();
            map?.closePopup();
            stage.clear('pins');
            map?.off('moveend', onMove);
            document.body.classList.remove('crosshair-mode');
            stage.clear('places', 'reports', 'zones');
        };
    },
};
