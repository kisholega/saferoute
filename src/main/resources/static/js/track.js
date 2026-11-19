// Public live-tracking page for emergency contacts (/track/{token}). No login: the token is the key.
import { api } from './api.js';
import { icon, installIcons, LOGO } from './icons.js';
import * as stage from './mapstage.js';
import { clockTime, html, timeAgo } from './ui.js';
import { initTheme } from './theme.js';

initTheme();
installIcons();
const token = decodeURIComponent(location.pathname.split('/').pop());
const card = document.getElementById('card');
let snap = null;
let trail = null;
let first = true;

const STATUS = {
    ACTIVE: { cls: '', icon: 'navigate', title: n => `${n} is on the way` },
    CHECK_IN_REQUIRED: { cls: 'check', icon: 'alert', title: n => `Checking on ${n}` },
    ALERTED: { cls: 'alerted', icon: 'siren', title: n => `${n} may need help` },
    SOS: { cls: 'sos', icon: 'siren', title: n => `${n} pressed SOS` },
    ARRIVED: { cls: 'safe', icon: 'check', title: n => `${n} arrived safely` },
    RESOLVED: { cls: 'safe', icon: 'heart', title: n => `${n} is safe` },
    ENDED: { cls: '', icon: 'stop', title: n => `${n} ended the trip` },
};

function render() {
    const s = STATUS[snap.status] || STATUS.ACTIVE;
    const urgent = ['SOS', 'ALERTED', 'CHECK_IN_REQUIRED'].includes(snap.status);
    card.innerHTML = html`
        <div class="track-hero ${s.cls}">
            <div class="row between"><span class="row" style="gap:.4rem;font-weight:800;font-size:.85rem;opacity:.9">${LOGO} SafeRoute AI</span>
                ${['ACTIVE', 'SOS', 'ALERTED', 'CHECK_IN_REQUIRED'].includes(snap.status) ? html`<span class="live-pill" style="color:#fff">LIVE</span>` : ''}</div>
            <div class="row" style="margin-top:.6rem;gap:.8rem">
                <span class="b-icon" style="width:46px;height:46px;border-radius:15px;background:rgba(255,255,255,.18);display:grid;place-items:center">${icon(s.icon, 'icon-lg')}</span>
                <div><h1>${s.title(snap.name)}</h1><p class="small" style="opacity:.9">${snap.statusText}</p></div>
            </div>
        </div>
        <div class="track-body stack" style="gap:.6rem">
            <div class="card">
                ${snap.destinationName ? html`<div class="row between small"><span class="muted">Going to</span><strong>${snap.destinationName}</strong></div>` : ''}
                ${snap.eta && snap.kind === 'TRIP' && snap.status === 'ACTIVE' ? html`<div class="row between small" style="margin-top:.35rem"><span class="muted">Expected around</span><strong>${clockTime(snap.eta)}</strong></div>` : ''}
                <div class="row between small" style="margin-top:.35rem"><span class="muted">Last update</span><strong data-updated>${snap.updatedAt ? timeAgo(snap.updatedAt) : 'waiting…'}</strong></div>
                ${snap.battery != null ? html`<div class="row between small" style="margin-top:.35rem"><span class="muted">Phone battery</span><strong>${snap.battery}%</strong></div>` : ''}
                ${snap.kind === 'TRIP' ? html`<div class="progress"><div style="width:${Math.round((snap.progress || 0) * 100)}%"></div></div>` : ''}
            </div>
            <div class="grid grid-2" style="gap:.5rem">
                <a class="btn btn-primary" href="tel:${snap.phone}">${icon('phone')} Call ${snap.name}</a>
                ${urgent ? html`<a class="btn btn-danger" href="tel:112">${icon('siren')} Call 112</a>`
                    : snap.latitude != null ? html`<a class="btn btn-ghost" target="_blank" rel="noopener" href="https://www.google.com/maps?q=${snap.latitude},${snap.longitude}">${icon('external')} Google Maps</a>` : ''}
            </div>
        </div>`;
}

function drawMap() {
    if (!stage.getMap()) stage.ensureMap(snap.latitude != null ? [snap.latitude, snap.longitude] : stage.DEFAULT_CENTER);
    if (first && snap.geometry) {
        stage.drawSelectedRoute({ geometry: snap.geometry, segments: snap.segments || [], riskLevel: 'Low Risk' }, true);
        if (snap.destinationName && snap.geometry.length) {
            const end = snap.geometry[snap.geometry.length - 1];
            stage.setPins(null, { latlng: end, label: snap.destinationName });
        }
    }
    if (!trail) trail = L.polyline([], { color: '#6d28d9', weight: 4, opacity: 0.6, dashArray: '2 8' }).addTo(stage.layer('trail'));
    trail.setLatLngs(snap.trail || []);
    if (snap.latitude != null) {
        stage.setMe([snap.latitude, snap.longitude], snap.accuracy || 0);
        if (first) stage.getMap().setView([snap.latitude, snap.longitude], 16);
        else stage.getMap().panTo([snap.latitude, snap.longitude], { animate: true });
    }
    first = false;
}

async function load() {
    try {
        snap = await api(`/api/track/${encodeURIComponent(token)}`);
        render();
        drawMap();
        document.title = `${(STATUS[snap.status] || STATUS.ACTIVE).title(snap.name)} · SafeRoute AI`;
    } catch (error) {
        card.innerHTML = html`<div class="card pad-lg" style="text-align:center">
            ${LOGO}<h1 class="display" style="font-size:1.5rem;margin:.8rem 0 .4rem">${error.status === 410 ? 'This link has expired' : 'Link not available'}</h1>
            <p class="muted small">${error.message}</p></div>`.toString();
        stage.ensureMap();
        return false;
    }
    return true;
}

function listen() {
    if (!window.EventSource) { setInterval(load, 10000); return; }
    const source = new EventSource(`/api/track/${encodeURIComponent(token)}/stream`);
    const apply = event => {
        const update = JSON.parse(event.data);
        if (update.latitude != null) {
            snap.latitude = update.latitude;
            snap.longitude = update.longitude;
            snap.accuracy = update.accuracy;
            snap.trail = [...(snap.trail || []), [update.latitude, update.longitude]];
        }
        if (update.battery != null) snap.battery = update.battery;
        if (update.progress != null) snap.progress = update.progress;
        if (update.at) snap.updatedAt = update.at;
        if (update.status && update.status !== snap.status) {
            load(); // status text changes: refetch the full snapshot
            return;
        }
        render();
        drawMap();
    };
    source.addEventListener('position', apply);
    source.addEventListener('status', apply);
    source.onerror = () => { /* the browser reconnects automatically */ };
    setInterval(load, 30000); // safety net in case the stream drops silently
    setInterval(() => {
        const node = document.querySelector('[data-updated]');
        if (node && snap?.updatedAt) node.textContent = timeAgo(snap.updatedAt);
    }, 15000);
}

load().then(ok => { if (ok) listen(); });

