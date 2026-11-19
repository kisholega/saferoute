// The one Leaflet map behind every map screen. Views draw into named layers and clear them when they leave.
import { iconForPlaceType } from './icons.js';
import { escapeHtml, reducedMotion, RISK_COLORS, riskKey } from './ui.js';

let map = null;
const layers = {};
let meMarker = null;
let meAccuracy = null;
const clickHandlers = new Set();

export const DEFAULT_CENTER = [12.9716, 77.5946];

export function ensureMap(center = DEFAULT_CENTER) {
    if (map) return map;
    const element = document.getElementById('mapStage');
    if (typeof window.L === 'undefined') {
        element.innerHTML = '<div style="display:grid;place-items:center;height:100%;color:#7a7290;padding:2rem;text-align:center">The map could not load. Check the internet connection.</div>';
        return null;
    }
    map = L.map(element, { zoomControl: false, attributionControl: true, preferCanvas: false }).setView(center, 13);
    L.control.zoom({ position: 'bottomright' }).addTo(map);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        className: 'soft-tiles',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
    ['zones', 'reports', 'places', 'routes', 'route', 'trail', 'pins', 'me', 'extra'].forEach(name => {
        layers[name] = L.layerGroup().addTo(map);
    });
    map.on('click', event => clickHandlers.forEach(handler => handler(event)));
    return map;
}

export function getMap() {
    return map;
}

export function layer(name) {
    return layers[name];
}

export function clear(...names) {
    names.forEach(name => layers[name]?.clearLayers());
}

export function onMapClick(handler) {
    clickHandlers.add(handler);
    return () => clickHandlers.delete(handler);
}

/** Called when the map becomes visible again after a full-page screen. */
export function refreshSize() {
    if (map) setTimeout(() => map.invalidateSize(), 60);
}

/** Space the floating panel / bottom sheet covers, so fitted content stays visible. */
function padding() {
    const mobile = window.innerWidth <= 760;
    return mobile
        ? { paddingTopLeft: [24, 90], paddingBottomRight: [24, Math.round(window.innerHeight * 0.62) + 16] }
        : { paddingTopLeft: [470, 90], paddingBottomRight: [60, 60] };
}

export function fit(latlngs, maxZoom = 16) {
    if (!map || !latlngs.length) return;
    const bounds = L.latLngBounds(latlngs);
    if (bounds.isValid()) map.flyToBounds(bounds, { ...padding(), maxZoom, duration: reducedMotion() ? 0 : 0.9 });
}

export function flyTo(latlng, zoom = 16) {
    if (map) map.flyTo(latlng, zoom, { duration: reducedMotion() ? 0 : 0.8 });
}

/** Animates a Leaflet path so it draws itself from start to end. */
export function drawIn(polyline, duration = 1100, delay = 0) {
    if (reducedMotion()) return;
    const path = polyline.getElement?.();
    if (!path || !path.getTotalLength) return;
    const length = path.getTotalLength();
    path.style.strokeDasharray = `${length}`;
    path.style.strokeDashoffset = `${length}`;
    path.getBoundingClientRect();
    path.style.transition = `stroke-dashoffset ${duration}ms cubic-bezier(.2,.8,.2,1) ${delay}ms`;
    path.style.strokeDashoffset = '0';
    setTimeout(() => {
        path.style.strokeDasharray = '';
        path.style.strokeDashoffset = '';
        path.style.transition = '';
    }, duration + delay + 80);
}

// ---------------------------------------------------------------- markers
function svgUse(name) {
    return `<svg class="icon" aria-hidden="true"><use href="#i-${name}"/></svg>`;
}

export function pinIcon(kind) {
    const color = kind === 'start' ? '#6d28d9' : '#e11d74';
    const inner = kind === 'start' ? '<circle cx="17" cy="16" r="5" fill="#fff"/>' : '<path d="M17 11.5l1.5 3 3.3.5-2.4 2.3.6 3.3-3-1.6-3 1.6.6-3.3-2.4-2.3 3.3-.5z" fill="#fff"/>';
    return L.divIcon({
        className: '',
        html: `<svg class="pin-marker" viewBox="0 0 34 44"><path d="M17 43s14-13.2 14-25.5A14 14 0 003 17.5C3 29.8 17 43 17 43z" fill="${color}" stroke="#fff" stroke-width="2.5"/>${inner}</svg>`,
        iconSize: [34, 44],
        iconAnchor: [17, 43],
        popupAnchor: [0, -38],
    });
}

export function placeIcon(type) {
    return L.divIcon({
        className: '',
        html: `<div class="place-marker ${String(type).toLowerCase()}">${svgUse(iconForPlaceType(type))}</div>`,
        iconSize: [32, 32],
        iconAnchor: [16, 16],
        popupAnchor: [0, -14],
    });
}

export function setPins(start, end) {
    layers.pins.clearLayers();
    if (start) L.marker(start.latlng, { icon: pinIcon('start'), zIndexOffset: 500 }).bindPopup(`<strong>Start</strong><br>${escapeHtml(start.label || '')}`).addTo(layers.pins);
    if (end) L.marker(end.latlng, { icon: pinIcon('end'), zIndexOffset: 500 }).bindPopup(`<strong>Destination</strong><br>${escapeHtml(end.label || '')}`).addTo(layers.pins);
}

/** The blue (or violet, when simulated) "you are here" dot with its accuracy circle. */
export function setMe(latlng, accuracy = 0, simulated = false) {
    if (!map) return;
    if (!meMarker) {
        meMarker = L.marker(latlng, {
            icon: L.divIcon({ className: '', html: `<div class="me-dot ${simulated ? 'sim' : ''}"></div>`, iconSize: [22, 22], iconAnchor: [11, 11] }),
            zIndexOffset: 1000,
            interactive: false,
        }).addTo(layers.me);
        meAccuracy = L.circle(latlng, { radius: accuracy || 1, color: '#3b82f6', weight: 1, fillOpacity: 0.08, opacity: 0.3, interactive: false }).addTo(layers.me);
    } else {
        meMarker.setLatLng(latlng);
        meAccuracy.setLatLng(latlng).setRadius(accuracy || 1);
        const dot = meMarker.getElement()?.querySelector('.me-dot');
        dot?.classList.toggle('sim', simulated);
    }
}

export function clearMe() {
    layers.me?.clearLayers();
    meMarker = null;
    meAccuracy = null;
}

export function drawZones(zones) {
    layers.zones.clearLayers();
    zones.forEach(zone => {
        const color = RISK_COLORS[riskKey(zone.riskLevel)];
        L.circle([zone.latitude, zone.longitude], {
            radius: zone.radiusKm * 1000, color, weight: 1.5, fillColor: color, fillOpacity: 0.14, className: 'zone-pulse',
        }).bindPopup(`<strong>${escapeHtml(zone.name)}</strong><br>${escapeHtml(zone.riskLevel)}<br><span style="color:#7a7290">${escapeHtml(zone.description || '')}</span>`)
            .addTo(layers.zones);
    });
}

const PLACE_LABEL = { POLICE: 'Police', HOSPITAL: 'Hospital', CLINIC: 'Clinic', PHARMACY: 'Pharmacy', FUEL: 'Fuel station' };

export function drawPlaces(places, { onClick } = {}) {
    layers.places.clearLayers();
    places.forEach(place => {
        const lat = place.latitude;
        const lon = place.longitude;
        const meta = place.metersFromRoute !== undefined
            ? `${place.metersFromRoute} m from your route`
            : place.distanceKm !== undefined ? `${place.distanceKm} km away` : '';
        L.marker([lat, lon], { icon: placeIcon(place.type) })
            .bindPopup(`<strong>${escapeHtml(place.name)}</strong><br>${escapeHtml(PLACE_LABEL[place.type] || place.type)}${place.open24h ? ' · open 24/7' : ''}<br><span style="color:#7a7290">${escapeHtml(meta)}</span>
                <div class="map-popup-actions"><button type="button" onclick="window.open('https://www.google.com/maps/dir/?api=1&destination=${lat},${lon}','_blank')">Directions</button></div>`)
            .on('click', () => onClick?.(place))
            .addTo(layers.places);
    });
}

const REPORT_ICON = { POOR_LIGHTING: 'lamp', HARASSMENT: 'alert', ISOLATED: 'people', UNSAFE_CROWD: 'users', OTHER: 'flag', SAFE_SPOT: 'heart' };

export function drawReports(reports) {
    layers.reports.clearLayers();
    reports.forEach(r => {
        L.marker([r.latitude, r.longitude], {
            icon: L.divIcon({
                className: '',
                html: `<div class="report-marker ${r.positive ? 'positive' : ''}">${svgUse(REPORT_ICON[r.category] || 'flag')}</div>`,
                iconSize: [28, 28], iconAnchor: [14, 14], popupAnchor: [0, -14],
            }),
        }).bindPopup(`<strong>${escapeHtml(r.categoryLabel)}</strong><br>${r.note ? escapeHtml(r.note) + '<br>' : ''}<span style="color:#7a7290">Community report · ${new Date(r.createdAt).toLocaleDateString()}</span>`)
            .addTo(layers.reports);
    });
}

/**
 * Draws route options: the others in muted grey, the selected one coloured stretch by stretch
 * (green / amber / red), drawing itself in.
 */
export function drawRoutes(routes, selectedId, { onSelect, animate = true } = {}) {
    layers.routes.clearLayers();
    layers.route.clearLayers();
    const selected = routes.find(r => r.routeId === selectedId) || routes[0];
    routes.filter(r => r !== selected).forEach(route => {
        L.polyline(route.geometry, { color: '#8f86a6', weight: 6, opacity: 0.55, lineCap: 'round', lineJoin: 'round' })
            .bindTooltip(`${route.label} · safety ${Math.round(route.safetyScore)}/100 · tap to compare`, { sticky: true })
            .on('click', () => onSelect?.(route.routeId))
            .addTo(layers.routes);
    });
    if (selected) drawSelectedRoute(selected, animate);
}

export function drawSelectedRoute(route, animate = true) {
    layers.route.clearLayers();
    const casing = L.polyline(route.geometry, { color: '#ffffff', weight: 12, opacity: 1, lineCap: 'round', lineJoin: 'round', interactive: false }).addTo(layers.route);
    const total = route.geometry.length - 1 || 1;
    const segments = route.segments?.length ? route.segments : [{ start: 0, end: total, level: route.riskLevel }];
    if (animate) drawIn(casing, 1100);
    segments.forEach(segment => {
        const coords = route.geometry.slice(segment.start, segment.end + 1);
        if (coords.length < 2) return;
        const line = L.polyline(coords, {
            color: RISK_COLORS[riskKey(segment.level)], weight: 7, opacity: 1, lineCap: 'round', lineJoin: 'round',
            dashArray: route.demoMode ? '10 12' : null, className: 'route-path',
        }).bindTooltip(`${escapeHtml(segment.level || route.riskLevel)}${segment.note ? ' · ' + escapeHtml(segment.note) : ''}`, { sticky: true })
            .addTo(layers.route);
        if (animate && !route.demoMode) drawIn(line, 700, 1100 * segment.start / total);
    });
}
