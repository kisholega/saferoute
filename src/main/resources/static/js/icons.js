// Hand-drawn SVG icon set (24px grid, 1.8px round stroke). Injected once as a sprite; use icon('name').
import { raw } from './ui.js';

const ICONS = {
    shield: '<path d="M12 3l7 3v5.5c0 4.4-3 8.2-7 9.5-4-1.3-7-5.1-7-9.5V6z"/>',
    'shield-check': '<path d="M12 3l7 3v5.5c0 4.4-3 8.2-7 9.5-4-1.3-7-5.1-7-9.5V6z"/><path d="M9 12l2 2 4-4"/>',
    pin: '<path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0113 0C18.5 15.4 12 21 12 21z"/><circle cx="12" cy="10" r="2.4"/>',
    navigate: '<path d="M4 11l16-7-7 16-2-7z"/>',
    locate: '<circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2.5"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    walk: '<circle cx="13.5" cy="4.5" r="1.8"/><path d="M9 21l2.5-6.5L14 17v4M7.5 12.5L10 8l3.5 1 1.8 3 2.7.8M11.5 14.5L13 9"/>',
    car: '<path d="M5 17h14v-5l-2-5H7l-2 5z"/><path d="M5 12h14"/><circle cx="8" cy="17" r="1.8"/><circle cx="16" cy="17" r="1.8"/>',
    users: '<circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><path d="M16 5.2a3 3 0 010 5.6M18 14.3c1.8.8 3 2.6 3 4.7"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8"/>',
    phone: '<path d="M5 4h3l2 5-2.5 1.5a11 11 0 005 5L14 13l5 2v3a2 2 0 01-2 2A15 15 0 013 6a2 2 0 012-2z"/>',
    'phone-off': '<path d="M5 4h3l2 5-2.5 1.5a11 11 0 005 5L14 13l5 2v3a2 2 0 01-2 2A15 15 0 013 6a2 2 0 012-2z"/><path d="M3 3l18 18"/>',
    mail: '<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M3.5 7.5l8.5 6 8.5-6"/>',
    bell: '<path d="M6 16v-5a6 6 0 0112 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 004 0"/>',
    message: '<path d="M4 5h16v11H9.5L4 20z"/><path d="M8 9.5h8M8 12.5h5"/>',
    chat: '<path d="M20 12a8 8 0 01-11.8 7L4 20l1.1-4A8 8 0 1120 12z"/><path d="M9 10.5c.5 2 2 3.5 4.5 4l1.2-1.2 1.8.7"/>',
    alert: '<path d="M12 3.5l9.5 16.5h-19z"/><path d="M12 10v4.5M12 17.4v.01"/>',
    siren: '<path d="M7 18v-6a5 5 0 0110 0v6"/><path d="M5 18h14v3H5z"/><path d="M12 3v2M4.2 6.2l1.4 1.4M19.8 6.2l-1.4 1.4M10 12a2 2 0 012-2"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    x: '<path d="M6 6l12 12M18 6L6 18"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h10"/>',
    'arrow-left': '<path d="M19 12H5M11 6l-6 6 6 6"/>',
    'arrow-right': '<path d="M5 12h14M13 6l6 6-6 6"/>',
    swap: '<path d="M7 4v16M7 4L4 7M7 4l3 3M17 20V4M17 20l-3-3M17 20l3-3"/>',
    route: '<circle cx="6" cy="18" r="2.5"/><circle cx="18" cy="6" r="2.5"/><path d="M8.5 18H15a3 3 0 000-6H9a3 3 0 010-6h6.5"/>',
    star: '<path d="M12 3.5l2.6 5.3 5.9.9-4.2 4.1 1 5.8L12 16.9l-5.3 2.7 1-5.8-4.2-4.1 5.9-.9z"/>',
    heart: '<path d="M12 20s-7-4.4-7-10a4 4 0 017-2.6A4 4 0 0119 10c0 5.6-7 10-7 10z"/>',
    lamp: '<path d="M9 21h6M12 21V6.5A2.5 2.5 0 0114.5 4H17"/><path d="M15 4h5l-1 3h-3z"/><path d="M17.5 9.5v1.3M15.2 9l-.7 1M19.8 9l.7 1"/>',
    police: '<path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z"/><path d="M12 8.3l1.2 2.4 2.6.4-1.9 1.8.5 2.6L12 14.3l-2.4 1.2.5-2.6-1.9-1.8 2.6-.4z"/>',
    hospital: '<rect x="4" y="4" width="16" height="16" rx="4.5"/><path d="M12 8v8M8 12h8"/>',
    pill: '<rect x="3.2" y="9" width="17.6" height="6" rx="3" transform="rotate(-45 12 12)"/><path d="M9.5 9.5l5 5"/>',
    fuel: '<path d="M5 21V5.5A2.5 2.5 0 017.5 3h5A2.5 2.5 0 0115 5.5V21M4 21h12"/><path d="M8 7.5h4"/><path d="M15 10h2a2 2 0 012 2v4a1.5 1.5 0 003 0V9l-3-3"/>',
    store: '<path d="M4 9l1.5-5h13L20 9"/><path d="M4 9a2.7 2.7 0 005.3 0 2.7 2.7 0 005.4 0 2.7 2.7 0 005.3 0"/><path d="M5 11v9h14v-9M10 20v-5h4v5"/>',
    people: '<circle cx="8" cy="7.5" r="2.5"/><circle cx="16" cy="7.5" r="2.5"/><path d="M3.5 20v-3a4.5 4.5 0 019 0v3M11.5 17a4.5 4.5 0 019 0v3"/>',
    flag: '<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.01"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    moon: '<path d="M20 14.5A8 8 0 019.5 4a8 8 0 1010.5 10.5z"/>',
    share: '<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="M8.3 10.8l7.4-4M8.3 13.2l7.4 4"/>',
    copy: '<rect x="8" y="8" width="12" height="12" rx="2.5"/><path d="M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2"/>',
    play: '<path d="M7 5l12 7-12 7z"/>',
    pause: '<path d="M8 5v14M16 5v14"/>',
    stop: '<rect x="6" y="6" width="12" height="12" rx="2.5"/>',
    sparkles: '<path d="M11 3l1.9 4.9L17.8 9.8l-4.9 1.9L11 16.6l-1.9-4.9L4.2 9.8l4.9-1.9z"/><path d="M18.5 14.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>',
    chart: '<path d="M4 20V4M4 20h16"/><path d="M8.5 16v-4M12.5 16V8M16.5 16v-6"/>',
    sliders: '<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
    logout: '<path d="M15 4h3a2 2 0 012 2v12a2 2 0 01-2 2h-3"/><path d="M10 8l-4 4 4 4M6 12h10"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    trash: '<path d="M4 7h16M9.5 7V4h5v3M6 7l1 13h10l1-13"/><path d="M10 11v5M14 11v5"/>',
    edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
    'chevron-right': '<path d="M9 6l6 6-6 6"/>',
    'chevron-down': '<path d="M6 9l6 6 6-6"/>',
    lock: '<rect x="5" y="10" width="14" height="10" rx="2.5"/><path d="M8 10V7a4 4 0 018 0v3"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
    'eye-off': '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/><path d="M4 4l16 16"/>',
    battery: '<rect x="3" y="8" width="16" height="9" rx="2.2"/><path d="M21 11v3"/><path d="M6 11h6v3H6z"/>',
    qr: '<rect x="4" y="4" width="6" height="6" rx="1.2"/><rect x="14" y="4" width="6" height="6" rx="1.2"/><rect x="4" y="14" width="6" height="6" rx="1.2"/><path d="M14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5zM14 19.5h1.5M19.5 14H20"/>',
    home: '<path d="M4 11l8-7 8 7v9H4z"/><path d="M10 20v-5h4v5"/>',
    layers: '<path d="M12 4l9 5-9 5-9-5z"/><path d="M3 14l9 5 9-5"/>',
    send: '<path d="M21 3L10 14M21 3l-7 18-4-7-7-4z"/>',
    refresh: '<path d="M20 11a8 8 0 10-2.3 5.7M20 4v7h-7"/>',
    external: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 01-1 1H5a1 1 0 01-1-1V7a1 1 0 011-1h5"/>',
    compass: '<circle cx="12" cy="12" r="9"/><path d="M15.5 8.5l-2 5-5 2 2-5z"/>',
    history: '<path d="M3.5 12a8.5 8.5 0 102.5-6"/><path d="M3 4v4h4"/><path d="M12 8v4l3 2"/>',
    volume: '<path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M16.5 9a4 4 0 010 6M19 6.5a7.5 7.5 0 010 11"/>',
    train: '<rect x="6" y="3" width="12" height="13" rx="3"/><path d="M6 10h12M8.5 20l-1.5 2M15.5 20l1.5 2M9 16l-1.5 4h9L15 16"/><circle cx="9" cy="13" r=".6"/><circle cx="15" cy="13" r=".6"/>',
    cap: '<path d="M2 9l10-5 10 5-10 5z"/><path d="M6 11v5c0 1.5 3 3 6 3s6-1.5 6-3v-5M21 9v5"/>',
    tree: '<path d="M12 21v-5"/><path d="M12 3l5.5 7h-11z"/><path d="M12 7.5l7 8.5H5z"/>',
    target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.2"/>',
    wifi: '<path d="M2.5 9a14 14 0 0119 0M5.5 12.5a9.5 9.5 0 0113 0M8.7 16a5 5 0 016.6 0"/><path d="M12 19.5v.01"/>',
    wand: '<path d="M4 20L15 9M14 4v3M17.5 5.5l-2 2M20 10h-3M12.5 7.5l4 4"/>',
};

/** The brand mark: a gradient shield holding a heart. */
export const LOGO = raw(`<svg class="brand-mark" viewBox="0 0 40 40" aria-hidden="true">
  <defs><linearGradient id="lg-brand" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#6d28d9"/><stop offset=".55" stop-color="#a21caf"/><stop offset="1" stop-color="#e11d74"/>
  </linearGradient></defs>
  <path d="M20 3l14 5.5v10c0 8.6-6 15.6-14 18.5C12 34.1 6 27.1 6 18.5v-10z" fill="url(#lg-brand)"/>
  <path d="M20 28s-7.2-5-7.2-10.4a4.2 4.2 0 017.2-3 4.2 4.2 0 017.2 3C27.2 23 20 28 20 28z" fill="#fff"/>
</svg>`);

export function installIcons() {
    const symbols = Object.entries(ICONS)
        .map(([name, body]) => `<symbol id="i-${name}" viewBox="0 0 24 24">${body}</symbol>`).join('');
    const sprite = document.createElement('div');
    sprite.style.display = 'none';
    sprite.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg">${symbols}</svg>`;
    document.body.prepend(sprite);
}

/** <svg> markup for an icon, safe to put in html`` templates. */
export function icon(name, extraClass = '') {
    const safe = ICONS[name] ? name : 'info';
    return raw(`<svg class="icon ${extraClass}" aria-hidden="true"><use href="#i-${safe}"/></svg>`);
}

/** Icon names for help-point and place types. */
export function iconForPlaceType(type) {
    const t = String(type || '').toUpperCase();
    if (t === 'POLICE') return 'police';
    if (t === 'HOSPITAL' || t === 'CLINIC') return 'hospital';
    if (t === 'PHARMACY') return 'pill';
    if (t === 'FUEL') return 'fuel';
    const pretty = String(type || '').toLowerCase();
    if (pretty.includes('station') || pretty.includes('halt') || pretty.includes('stop')) return 'train';
    if (pretty.includes('mall') || pretty.includes('shop') || pretty.includes('market')) return 'store';
    if (pretty.includes('college') || pretty.includes('university') || pretty.includes('school')) return 'cap';
    if (pretty.includes('hospital') || pretty.includes('clinic')) return 'hospital';
    if (pretty.includes('park') || pretty.includes('garden')) return 'tree';
    if (pretty.includes('attraction') || pretty.includes('museum')) return 'star';
    if (['suburb', 'neighbourhood', 'quarter', 'town', 'village', 'hamlet', 'locality', 'residential'].some(k => pretty.includes(k))) return 'home';
    if (pretty.includes('road') || pretty.includes('street') || pretty.includes('primary') || pretty.includes('secondary')) return 'route';
    return 'pin';
}
