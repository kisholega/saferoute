// UI helpers shared by every screen: safe HTML templating, formatting, toasts, modals, motion.

// ---------------------------------------------------------------- safe templating
class SafeHtml {
    constructor(value) { this.value = value; }
    toString() { return this.value; }
}

export function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

/** Marks a trusted string (e.g. our own SVG) as HTML. Never pass user or server text to this. */
export function raw(value) {
    return new SafeHtml(String(value));
}

function render(value) {
    if (value === null || value === undefined || value === false) return '';
    if (Array.isArray(value)) return value.map(render).join('');
    if (value instanceof SafeHtml) return value.value;
    return escapeHtml(value);
}

/** Every ${value} is escaped unless it is itself html`` or raw(). */
export function html(strings, ...values) {
    let out = strings[0];
    values.forEach((value, i) => { out += render(value) + strings[i + 1]; });
    return new SafeHtml(out);
}

export function el(markup) {
    const template = document.createElement('template');
    template.innerHTML = String(markup).trim();
    return template.content.firstElementChild;
}

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

// ---------------------------------------------------------------- formatting
export function riskKey(level) {
    const v = String(level || '').toLowerCase();
    if (v.startsWith('high')) return 'high';
    if (v.startsWith('medium') || v.startsWith('moderate')) return 'medium';
    return 'low';
}

export const RISK_COLORS = { low: '#12a26a', medium: '#e59a0b', high: '#e0393c' };

export function formatMinutes(minutes) {
    if (minutes === null || minutes === undefined || Number.isNaN(minutes)) return '–';
    const m = Math.max(0, Math.round(minutes));
    if (m < 60) return `${m} min`;
    const h = Math.floor(m / 60);
    return m % 60 ? `${h} h ${m % 60} min` : `${h} h`;
}

export function formatKm(km) {
    if (km === null || km === undefined || Number.isNaN(km)) return '–';
    return km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(km < 10 ? 1 : 0)} km`;
}

export function formatMeters(m) {
    return formatKm(m / 1000);
}

export function formatPhone(phone) {
    const match = /^\+91(\d{5})(\d{5})$/.exec(phone || '');
    return match ? `+91 ${match[1]} ${match[2]}` : phone || '';
}

export function clockTime(value) {
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function timeAgo(value) {
    const then = new Date(value);
    if (Number.isNaN(then.getTime())) return '';
    const s = Math.round((Date.now() - then.getTime()) / 1000);
    if (s < 45) return 'just now';
    const m = Math.round(s / 60);
    if (m < 60) return `${m} min ago`;
    const h = Math.round(m / 60);
    if (h < 24) return `${h} h ago`;
    const d = Math.round(h / 24);
    return d === 1 ? 'yesterday' : `${d} days ago`;
}

export function firstName(name) {
    return String(name || '').trim().split(/\s+/)[0] || '';
}

export function initials(name) {
    const parts = String(name || '?').trim().split(/\s+/);
    return ((parts[0]?.[0] || '?') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

const AVATAR_GRADIENTS = [
    'linear-gradient(135deg,#6d28d9,#a21caf)', 'linear-gradient(135deg,#e11d74,#f97316)',
    'linear-gradient(135deg,#0ea5e9,#6d28d9)', 'linear-gradient(135deg,#12a26a,#0ea5e9)',
    'linear-gradient(135deg,#a21caf,#e11d74)', 'linear-gradient(135deg,#f59e0b,#e11d74)',
];

export function avatarStyle(name) {
    let hash = 0;
    for (const ch of String(name || '')) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
    return `background:${AVATAR_GRADIENTS[hash % AVATAR_GRADIENTS.length]}`;
}

export function greeting() {
    const h = new Date().getHours();
    if (h < 5) return 'Up late';
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
}

export function debounce(fn, ms) {
    let timer;
    return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
}

export function pad(n) {
    return String(n).padStart(2, '0');
}

export function timeInput(date = new Date()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// ---------------------------------------------------------------- toasts
export function toast(message, type = 'info', ms = 3600) {
    const host = document.getElementById('toasts');
    if (!host) return;
    const iconName = { success: 'check', error: 'alert', warn: 'alert', info: 'info' }[type] || 'info';
    const node = el(html`<div class="toast ${type}" role="status"><svg class="icon" aria-hidden="true"><use href="#i-${iconName}"/></svg><span>${message}</span></div>`);
    host.append(node);
    setTimeout(() => {
        node.classList.add('out');
        setTimeout(() => node.remove(), 320);
    }, ms);
}

// ---------------------------------------------------------------- modal
/**
 * Opens a modal dialog. content: SafeHtml. Returns { root, close }.
 * Closes on backdrop click and Escape unless options.locked.
 */
export function openModal(content, { wide = false, locked = false, className = '', onClose } = {}) {
    const overlay = el(html`<div class="overlay ${className}" role="dialog" aria-modal="true"><div class="modal ${wide ? 'wide' : ''}">${content}</div></div>`);
    document.body.append(overlay);
    const previouslyFocused = document.activeElement;
    const close = () => {
        overlay.remove();
        document.removeEventListener('keydown', onKey);
        previouslyFocused?.focus?.();
        onClose?.();
    };
    const onKey = event => { if (event.key === 'Escape' && !locked) close(); };
    document.addEventListener('keydown', onKey);
    overlay.addEventListener('click', event => { if (event.target === overlay && !locked) close(); });
    overlay.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', close));
    setTimeout(() => overlay.querySelector('input, button:not([data-close]), [tabindex]')?.focus(), 50);
    return { root: overlay, close };
}

// ---------------------------------------------------------------- motion
export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Counts a number up inside an element. */
export function countUp(node, to, { duration = 900, decimals = 0 } = {}) {
    if (!node) return;
    if (reducedMotion()) { node.textContent = to.toFixed(decimals); return; }
    const start = performance.now();
    const step = now => {
        const t = Math.min(1, (now - start) / duration);
        const eased = 1 - Math.pow(1 - t, 3);
        node.textContent = (to * eased).toFixed(decimals);
        if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
}

/** Material-style ripple on buttons and chips. */
export function installRipples() {
    document.addEventListener('pointerdown', event => {
        const target = event.target.closest('.btn, .chip');
        if (!target || target.disabled || reducedMotion()) return;
        const rect = target.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height);
        const ripple = document.createElement('span');
        ripple.className = 'ripple';
        ripple.style.width = ripple.style.height = `${size}px`;
        ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
        ripple.style.top = `${event.clientY - rect.top - size / 2}px`;
        target.append(ripple);
        setTimeout(() => ripple.remove(), 650);
    });
}

/** A burst of confetti (arrival celebration). */
export function confetti() {
    if (reducedMotion()) return;
    const canvas = document.createElement('canvas');
    canvas.id = 'confetti';
    canvas.width = innerWidth;
    canvas.height = innerHeight;
    document.body.append(canvas);
    const ctx = canvas.getContext('2d');
    const colors = ['#6d28d9', '#a21caf', '#e11d74', '#12a26a', '#f59e0b', '#38bdf8'];
    const pieces = Array.from({ length: 160 }, () => ({
        x: innerWidth / 2, y: innerHeight / 3,
        vx: (Math.random() - .5) * 16, vy: Math.random() * -14 - 4,
        size: 5 + Math.random() * 7, rot: Math.random() * 6, vr: (Math.random() - .5) * .3,
        color: colors[Math.floor(Math.random() * colors.length)],
    }));
    let frame = 0;
    const tick = () => {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        pieces.forEach(p => {
            p.vy += .38; p.vx *= .99; p.x += p.vx; p.y += p.vy; p.rot += p.vr;
            ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot);
            ctx.fillStyle = p.color; ctx.fillRect(-p.size / 2, -p.size / 3, p.size, p.size / 1.6);
            ctx.restore();
        });
        if (++frame < 170) requestAnimationFrame(tick); else canvas.remove();
    };
    tick();
}

// ---------------------------------------------------------------- device helpers
export function speak(text) {
    try {
        if (!('speechSynthesis' in window)) return;
        window.speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance(text);
        u.rate = 1; u.pitch = 1;
        window.speechSynthesis.speak(u);
    } catch { /* speech not available */ }
}

export function vibrate(pattern) {
    try { navigator.vibrate?.(pattern); } catch { /* ignore */ }
}

export async function batteryLevel() {
    try {
        const battery = await navigator.getBattery?.();
        return battery ? Math.round(battery.level * 100) : null;
    } catch {
        return null;
    }
}

export async function copyText(text) {
    try {
        await navigator.clipboard.writeText(text);
        return true;
    } catch {
        const area = document.createElement('textarea');
        area.value = text;
        document.body.append(area);
        area.select();
        const ok = document.execCommand('copy');
        area.remove();
        return ok;
    }
}

const loadedScripts = new Map();
export function loadScript(src) {
    if (!loadedScripts.has(src)) {
        loadedScripts.set(src, new Promise((resolve, reject) => {
            const s = document.createElement('script');
            s.src = src;
            s.onload = resolve;
            s.onerror = () => reject(new Error('Could not load ' + src));
            document.head.append(s);
        }));
    }
    return loadedScripts.get(src);
}

/** Current position as {latitude, longitude, accuracy} or null (permission denied / unavailable). */
export function currentPosition(timeout = 8000) {
    return new Promise(resolve => {
        if (!navigator.geolocation) return resolve(null);
        navigator.geolocation.getCurrentPosition(
            p => resolve({ latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy }),
            () => resolve(null),
            { enableHighAccuracy: true, timeout, maximumAge: 30000 });
    });
}

export function waLink(phone, text) {
    return `https://wa.me/${String(phone || '').replace(/\D/g, '')}?text=${encodeURIComponent(text)}`;
}

export function smsLink(phones, text) {
    const list = Array.isArray(phones) ? phones.join(',') : phones;
    return `sms:${list}?body=${encodeURIComponent(text)}`;
}
