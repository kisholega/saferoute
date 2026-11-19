// App entry point: shell (top bar, menu), auth guard, server version check, routes.
import { api, post } from './api.js';
import { loadContacts } from './components/contacts.js';
import { fakeCall } from './fakecall.js';
import { icon, installIcons, LOGO } from './icons.js';
import { ensureMap, refreshSize } from './mapstage.js';
import { currentPath, defineRoute, navigate, setHooks, start } from './router.js';
import { installSos, setSosVisible } from './sos.js';
import { currentTheme, initTheme, toggleTheme } from './theme.js';
import { store } from './store.js';
import { avatarStyle, el, html, initials, installRipples, toast } from './ui.js';

const EXPECTED_API_VERSION = 3;

initTheme();
installIcons();
installRipples();
installSos();

defineRoute('/welcome', () => import('./views/welcome.js'));
defineRoute('/auth', () => import('./views/auth.js'));
defineRoute('/setup', () => import('./views/setup.js'));
defineRoute('/plan', () => import('./views/plan.js'));
defineRoute('/routes', () => import('./views/routes.js'));
defineRoute('/trip/:id', () => import('./views/trip.js'));
defineRoute('/arrived/:id', () => import('./views/arrived.js'));
defineRoute('/contacts', () => import('./views/contacts.js'));
defineRoute('/explore', () => import('./views/explore.js'));
defineRoute('/insights', () => import('./views/insights.js'));
defineRoute('/alerts', () => import('./views/alerts.js'));
defineRoute('/profile', () => import('./views/profile.js'));

const MENU = [
    { path: '/plan', label: 'Plan a safe trip', icon: 'navigate' },
    { path: '/explore', label: 'Safety map', icon: 'compass' },
    { path: '/contacts', label: 'Emergency contacts', icon: 'users' },
    { path: '/alerts', label: 'Alert history', icon: 'history' },
    { path: '/insights', label: 'How the AI works', icon: 'sparkles' },
    { path: '/profile', label: 'Profile & settings', icon: 'sliders' },
];

let lastView = null;
window.addEventListener('themechange', () => { if (lastView) renderTopbar(lastView); });

function renderTopbar(view) {
    const bar = document.getElementById('topbar');
    bar.className = `topbar ${view.layout === 'page' ? 'solid' : ''}`;
    const back = view.back ? (typeof view.back === 'function' ? view.back() : view.back) : null;
    bar.innerHTML = html`
        ${back ? html`<button type="button" class="icon-btn" data-back aria-label="Back">${icon('arrow-left')}</button>` : ''}
        <a class="brand ${view.layout === 'map' ? 'topbar-pill' : ''}" href="#${store.user ? '/plan' : '/welcome'}" aria-label="SafeRoute AI home">
            ${LOGO}<span class="brand-text">SafeRoute <span class="ai">AI</span></span></a>
        <div class="spacer"></div>
        <button type="button" class="icon-btn theme-toggle" data-theme-toggle aria-label="${currentTheme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}" title="${currentTheme() === 'dark' ? 'Light mode' : 'Dark mode'}">
            ${icon(currentTheme() === 'dark' ? 'sun' : 'moon')}</button>
        ${store.user
            ? html`<button type="button" class="topbar-pill" data-menu aria-label="Open menu" style="cursor:pointer">
                    <span class="avatar" style="${avatarStyle(store.user.name)}">${initials(store.user.name)}</span>
                    ${icon('menu')}</button>`
            : view.path !== '/auth' ? html`<a class="btn btn-ghost btn-sm" href="#/auth?mode=login">Sign in</a>` : ''}`;
    bar.querySelector('[data-back]')?.addEventListener('click', () => navigate(back));
    bar.querySelector('[data-menu]')?.addEventListener('click', openMenu);
    bar.querySelector('[data-theme-toggle]').addEventListener('click', toggleTheme);
    lastView = view;
}

function openMenu() {
    const here = currentPath();
    const backdrop = el('<div class="drawer-backdrop"></div>');
    const drawer = el(html`<aside class="drawer" aria-label="Menu">
        <div class="drawer-head">
            <div class="row" style="gap:.8rem">
                <span class="avatar lg">${initials(store.user.name)}</span>
                <div><strong style="font-size:1.1rem">${store.user.name}</strong><div class="small" style="opacity:.85">${store.user.email}</div></div>
            </div>
        </div>
        <nav>
            ${MENU.map((item, i) => html`<a href="#${item.path}" style="--i:${i}" ${here === item.path ? html`aria-current="page"` : ''}>${icon(item.icon)} ${item.label}</a>`)}
            <button type="button" style="--i:${MENU.length}" data-fake>${icon('phone')} Fake incoming call</button>
            <button type="button" style="--i:${MENU.length + 1}" data-logout>${icon('logout')} Sign out</button>
        </nav>
        <div class="drawer-foot">In danger? Hold the red SOS button, or call <a href="tel:112"><strong>112</strong></a>.</div>
    </aside>`);
    const close = () => { backdrop.remove(); drawer.remove(); };
    backdrop.addEventListener('click', close);
    drawer.querySelectorAll('a').forEach(a => a.addEventListener('click', close));
    drawer.querySelector('[data-fake]').addEventListener('click', () => {
        close();
        const primary = (store.contacts || []).find(c => c.isPrimary);
        toast('Fake call in 5 seconds. Put your phone away.', 'info');
        fakeCall(primary ? primary.name : 'Mom', 5);
    });
    drawer.querySelector('[data-logout]').addEventListener('click', async () => {
        close();
        try { await post('/api/auth/logout'); } catch { /* signed out anyway */ }
        store.reset();
        toast('Signed out. Stay safe!', 'success');
        navigate('/welcome');
    });
    document.addEventListener('keydown', function onKey(e) {
        if (e.key === 'Escape') { close(); document.removeEventListener('keydown', onKey); }
    });
    document.body.append(backdrop, drawer);
    drawer.querySelector('nav a')?.focus();
}

setHooks({
    fallback: () => (store.user ? '/plan' : '/welcome'),
    beforeEnter(view, path) {
        if (view.auth && !store.user) return '/welcome';
        if (view.guestOnly && store.user) return '/plan';
        return null;
    },
    afterEnter(view, path) {
        view.path = path;
        renderTopbar(view);
        setSosVisible(Boolean(store.user));
        const stage = document.getElementById('mapStage');
        stage.style.visibility = view.layout === 'map' ? 'visible' : 'hidden';
        if (view.layout === 'map') {
            const info = store.appInfo?.mapCenter;
            ensureMap(info ? [info.latitude, info.longitude] : undefined);
            refreshSize();
        }
    },
});

window.addEventListener('auth:expired', () => {
    if (!store.user) return;
    store.reset();
    toast('Your session ended. Please sign in again.', 'warn');
    navigate('/auth?mode=login');
});

async function boot() {
    try {
        store.appInfo = await api('/api/app-info');
        if (store.appInfo.apiVersion !== EXPECTED_API_VERSION) throw new Error('version');
    } catch (error) {
        if (error.message === 'version' || error.status === 404 || error.status === 500) {
            document.body.append(el(html`<div class="version-warning">The server is running an older version of SafeRoute AI.
                Stop it (Ctrl+C in its terminal) and start it again with <code>mvn spring-boot:run</code>, then reload this page.</div>`));
        }
    }

    try {
        store.user = await api('/api/auth/me', { quiet401: true });
    } catch {
        store.user = null;
    }

    if (store.user) {
        loadContacts().catch(() => { });
        try {
            const active = await api('/api/trips/active');
            if (active && !currentPath().startsWith('/trip/')) {
                toast(`Resuming your Safe Trip to ${active.destinationName}.`, 'info');
                history.replaceState(null, '', `#/trip/${active.id}${active.simulated ? '?sim=1' : ''}`);
            }
        } catch { /* no active trip */ }
        if (!location.hash || location.hash === '#/' || location.hash === '#/welcome') history.replaceState(null, '', '#/plan');
    } else if (!location.hash || location.hash === '#/') {
        history.replaceState(null, '', '#/welcome');
    }
    start();
}

boot();

