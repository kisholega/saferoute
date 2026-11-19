// SOS: hold the button (1.2 s) -> 5 s cancellable countdown -> alert every contact over every channel.
// While active: live delivery status, location streaming to the tracking page, one-tap phone fallbacks.
import { api, post } from './api.js';
import { icon } from './icons.js';
import { store } from './store.js';
import {
    batteryLevel, currentPosition, el, firstName, html, smsLink, speak, toast, vibrate, waLink,
} from './ui.js';

const HOLD_MS = 1200;
let fab;
let holdTimer;
let activeAlert = null;
let overlay = null;
let watchId = null;
let lastSent = 0;
let pollTimer = null;

export function installSos() {
    fab = el(html`<button type="button" class="sos-fab hidden" aria-label="SOS. Press and hold for one second to alert your emergency contacts.">
        <svg class="hold-ring" viewBox="0 0 88 88"><circle cx="44" cy="44" r="42"/></svg>
        <span class="sos-label">SOS<small>HOLD</small></span></button>`);
    document.body.append(fab);

    const startHold = event => {
        if (event.button !== undefined && event.button !== 0) return;
        if (activeAlert) { showActive(); return; }
        fab.classList.add('holding');
        vibrate(30);
        holdTimer = setTimeout(() => {
            fab.classList.remove('holding');
            vibrate([60, 40, 60]);
            countdown();
        }, HOLD_MS);
    };
    const cancelHold = () => {
        if (!fab.classList.contains('holding')) return;
        clearTimeout(holdTimer);
        fab.classList.remove('holding');
        hint('Press and hold for 1 second to send SOS');
    };
    fab.addEventListener('pointerdown', startHold);
    fab.addEventListener('pointerup', cancelHold);
    fab.addEventListener('pointerleave', cancelHold);
    fab.addEventListener('contextmenu', e => e.preventDefault());
    // Keyboard users: Enter / Space opens the countdown directly (it can still be cancelled).
    fab.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            if (activeAlert) showActive(); else countdown();
        }
    });
}

export function setSosVisible(visible) {
    fab?.classList.toggle('hidden', !visible);
}

function hint(text) {
    document.querySelector('.sos-hint')?.remove();
    const node = el(html`<div class="sos-hint">${text}</div>`);
    document.body.append(node);
    setTimeout(() => node.remove(), 2200);
}

function closeOverlay() {
    overlay?.remove();
    overlay = null;
}

function countdown() {
    closeOverlay();
    let n = 5;
    const C = 2 * Math.PI * 85;
    overlay = el(html`<div class="overlay sos" role="alertdialog" aria-labelledby="sosTitle">
        <div class="sos-sheet">
            <h2 id="sosTitle">Sending SOS in</h2>
            <div class="countdown"><svg viewBox="0 0 190 190"><circle class="bg" cx="95" cy="95" r="85"/><circle class="fg" cx="95" cy="95" r="85" stroke-dasharray="${C}" stroke-dashoffset="0"/></svg>
                <div class="n"><span>5</span></div></div>
            <p style="opacity:.9">Your emergency contacts will get your live location.</p>
            <div class="row" style="justify-content:center;margin-top:1.2rem;gap:.6rem">
                <button type="button" class="btn btn-lg" style="background:#fff;color:var(--sos)" data-act="now">${icon('send')} Send now</button>
                <button type="button" class="btn btn-lg" style="background:rgba(255,255,255,.18);color:#fff" data-act="cancel">${icon('x')} Cancel</button>
            </div>
        </div></div>`);
    document.body.append(overlay);
    const fg = overlay.querySelector('.fg');
    const number = overlay.querySelector('.n');
    const tick = setInterval(() => {
        n -= 1;
        vibrate(80);
        fg.style.strokeDashoffset = String(C * (5 - n) / 5);
        if (n <= 0) {
            clearInterval(tick);
            send();
        } else {
            number.innerHTML = `<span>${n}</span>`;
        }
    }, 1000);
    overlay.querySelector('[data-act="cancel"]').addEventListener('click', () => {
        clearInterval(tick);
        closeOverlay();
        toast('SOS cancelled. No one was alerted.', 'info');
    });
    overlay.querySelector('[data-act="now"]').addEventListener('click', () => {
        clearInterval(tick);
        send();
    });
    overlay.querySelector('[data-act="cancel"]').focus();
}

async function send() {
    renderActive(null, true);
    const [position, battery] = await Promise.all([currentPosition(6000), batteryLevel()]);
    try {
        activeAlert = await post('/api/alerts/sos', {
            latitude: position?.latitude ?? null,
            longitude: position?.longitude ?? null,
            accuracy: position?.accuracy ?? null,
            battery,
        });
        fab.classList.add('active');
        fab.querySelector('.sos-label').innerHTML = 'SOS<small>ACTIVE</small>';
        speak('SOS sent. Your emergency contacts are being alerted.');
        renderActive(position);
        startStreaming();
        poll();
    } catch (error) {
        renderActive(position, false, error.message);
    }
}

function contactsList() {
    return store.contacts || [];
}

function fallbackMessage(position) {
    const where = position ? `https://maps.google.com/?q=${position.latitude.toFixed(5)},${position.longitude.toFixed(5)}` : 'location unavailable';
    const track = activeAlert?.trackUrl && store.appInfo?.publicLinks ? ` Live: ${activeAlert.trackUrl}` : '';
    return `EMERGENCY from ${firstName(store.user?.name) || 'me'}: I need help. My location: ${where}.${track} If you can't reach me, call 112.`;
}

function renderActive(position, sending = false, error = null) {
    closeOverlay();
    const contacts = contactsList();
    const message = fallbackMessage(position);
    overlay = el(html`<div class="overlay sos" role="alertdialog" aria-labelledby="sosActiveTitle">
        <div class="sos-sheet">
            <div class="radar"><div class="core">${icon('siren', 'icon-xl')}</div></div>
            <h2 id="sosActiveTitle">${sending ? 'Sending SOS…' : error ? 'SOS could not be sent' : 'SOS sent'}</h2>
            <p style="opacity:.92">${error ? error + ' Use the buttons below to reach help directly.' : sending ? 'Getting your location and alerting your contacts.' : 'Your contacts are being alerted with your live location.'}</p>
            <div class="sos-card">
                <div class="call-grid">
                    <a class="primary" href="tel:112">${icon('phone', 'icon-lg')}Call 112</a>
                    <a href="tel:1091">${icon('phone', 'icon-lg')}Women 1091</a>
                    <a href="tel:181">${icon('phone', 'icon-lg')}Helpline 181</a>
                </div>
                <div class="divider"></div>
                <div class="row between"><strong>Alert delivery</strong><span class="tiny muted" data-progress>${sending ? 'Starting…' : ''}</span></div>
                <div data-deliveries>${!contacts.length ? html`<p class="small muted" style="margin-top:.5rem">You have no emergency contacts yet. Call 112 or a helpline above.</p>` : ''}</div>
                ${contacts.length ? html`<div class="divider"></div>
                <strong class="small">Also message them from your phone</strong>
                <div class="stack" style="margin-top:.5rem;gap:.4rem">
                    ${contacts.map(c => html`<div class="row between small">
                        <span class="row" style="gap:.5rem"><span class="avatar" style="width:28px;height:28px;font-size:.7rem">${c.name[0]}</span>${c.name}</span>
                        <span class="row" style="gap:.35rem">
                            <a class="btn btn-sm btn-soft" href="${waLink(c.phone, message)}" target="_blank" rel="noopener">${icon('chat', 'icon-sm')} WhatsApp</a>
                            <a class="btn btn-sm btn-ghost" href="tel:${c.phone}">${icon('phone', 'icon-sm')}</a>
                        </span></div>`)}
                    <a class="btn btn-ghost btn-sm" href="${smsLink(contacts.map(c => c.phone), message)}">${icon('message', 'icon-sm')} Text everyone by SMS</a>
                </div>` : ''}
                <div class="row" style="margin-top:1rem;gap:.5rem">
                    ${activeAlert ? html`<button type="button" class="btn btn-safe btn-lg" style="flex:1" data-act="safe">${icon('shield-check')} I'm safe now</button>` : ''}
                    <button type="button" class="btn btn-ghost btn-lg" data-act="hide">${activeAlert || error ? 'Hide' : 'Close'}</button>
                </div>
            </div>
        </div></div>`);
    document.body.append(overlay);
    overlay.querySelector('[data-act="hide"]').addEventListener('click', closeOverlay);
    overlay.querySelector('[data-act="safe"]')?.addEventListener('click', resolve);
    if (activeAlert) updateDeliveries(activeAlert);
}

function updateDeliveries(alert) {
    const box = overlay?.querySelector('[data-deliveries]');
    if (!box) return;
    const visible = alert.deliveries.filter(d => d.status !== 'SKIPPED' || d.channel === 'PUSH');
    const labels = { PUSH: 'Push notification', EMAIL: 'Email', SMS: 'SMS' };
    box.innerHTML = html`${visible.map(d => html`<div class="delivery">
        <span class="status-icon ${d.status}">${d.status === 'SENT' ? icon('check') : d.status === 'FAILED' ? icon('x') : d.status === 'SKIPPED' ? icon('info') : ''}</span>
        <span class="who">${d.contactName}<span class="tiny muted" style="display:block;font-weight:500">${labels[d.channel]}${d.status === 'FAILED' || d.status === 'SKIPPED' ? ' · ' + (d.detail || '') : ''}</span></span>
        <span class="badge ${d.status === 'SENT' ? 'tone-good' : d.status === 'FAILED' ? 'tone-bad' : 'tone-neutral'}">${d.status === 'SENT' ? 'Delivered' : d.status === 'PENDING' ? 'Sending' : d.status === 'FAILED' ? 'Failed' : 'Skipped'}</span>
    </div>`)}`;
    const sent = alert.deliveries.filter(d => d.status === 'SENT').length;
    const progress = overlay.querySelector('[data-progress]');
    if (progress) progress.textContent = alert.deliveryInProgress ? 'Sending…' : `${sent} delivered`;
}

async function poll() {
    clearTimeout(pollTimer);
    if (!activeAlert) return;
    try {
        activeAlert = await api(`/api/alerts/${activeAlert.id}`);
        updateDeliveries(activeAlert);
        if (activeAlert.status !== 'ACTIVE') { finish(); return; }
    } catch { /* keep trying */ }
    pollTimer = setTimeout(poll, activeAlert?.deliveryInProgress ? 1200 : 8000);
}

function startStreaming() {
    if (!navigator.geolocation || watchId !== null) return;
    watchId = navigator.geolocation.watchPosition(async p => {
        if (!activeAlert || Date.now() - lastSent < 10000) return;
        lastSent = Date.now();
        try {
            await post(`/api/alerts/${activeAlert.id}/location`, {
                latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy, battery: await batteryLevel(),
            });
        } catch { /* next update will retry */ }
    }, () => { }, { enableHighAccuracy: true, maximumAge: 5000 });
}

async function resolve() {
    try {
        await post(`/api/alerts/${activeAlert.id}/resolve`);
        toast('Glad you are safe. Your contacts have been told.', 'success', 5000);
    } catch (error) {
        toast(error.message, 'error');
        return;
    }
    finish();
    closeOverlay();
}

function finish() {
    activeAlert = null;
    clearTimeout(pollTimer);
    if (watchId !== null) navigator.geolocation.clearWatch(watchId);
    watchId = null;
    fab.classList.remove('active');
    fab.querySelector('.sos-label').innerHTML = 'SOS<small>HOLD</small>';
}

function showActive() {
    renderActive(null);
}

/** Lets other screens (e.g. trip check-in "I need help") show the active SOS state. */
export function adoptAlert(alert) {
    activeAlert = alert;
    fab.classList.add('active');
    fab.querySelector('.sos-label').innerHTML = 'SOS<small>ACTIVE</small>';
    poll();
}

