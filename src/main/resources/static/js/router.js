// Hash router with animated view transitions and an auth guard.
// Each view module exports: { layout: 'map' | 'page', auth: boolean, title, mount(root, ctx) -> cleanup? }
import { reducedMotion } from './ui.js';

const routes = [];
let current = null;
let cleanup = null;
let hooks = { beforeEnter: null, afterEnter: null };

export function defineRoute(pattern, loader) {
    const keys = [];
    const regex = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, key) => { keys.push(key); return '([^/]+)'; }) + '$');
    routes.push({ pattern, regex, keys, loader });
}

export function setHooks(next) {
    hooks = { ...hooks, ...next };
}

export function navigate(path, { replace = false } = {}) {
    const target = '#' + path;
    if (location.hash === target) {
        render();
        return;
    }
    if (replace) {
        history.replaceState(null, '', target);
        render();
    } else {
        location.hash = path;
    }
}

export function currentPath() {
    return (location.hash || '#/').slice(1).split('?')[0] || '/';
}

function parse() {
    const [path, queryString = ''] = (location.hash || '#/').slice(1).split('?');
    return { path: path || '/', query: Object.fromEntries(new URLSearchParams(queryString)) };
}

async function render() {
    const { path, query } = parse();
    let match = null;
    for (const route of routes) {
        const m = route.regex.exec(path);
        if (m) {
            match = { route, params: Object.fromEntries(route.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])])) };
            break;
        }
    }
    if (!match) {
        navigate(hooks.fallback?.() || '/plan', { replace: true });
        return;
    }
    const module = await match.route.loader();
    const view = module.default;
    const redirect = hooks.beforeEnter?.(view, path);
    if (redirect && redirect !== path) {
        navigate(redirect, { replace: true });
        return;
    }

    const container = document.getElementById('view');
    const swap = () => {
        try { cleanup?.(); } catch (e) { console.error(e); }
        cleanup = null;
        container.innerHTML = '';
        const root = document.createElement('div');
        root.className = `view layout-${view.layout || 'page'}`;
        container.append(root);
        current = { view, path, params: match.params, query };
        hooks.afterEnter?.(view, path);
        const result = view.mount(root, { params: match.params, query, path });
        cleanup = typeof result === 'function' ? result : null;
        document.title = view.title ? `${typeof view.title === 'function' ? view.title() : view.title} · SafeRoute AI` : 'SafeRoute AI';
        return root;
    };

    if (document.startViewTransition && !reducedMotion()) {
        const transition = document.startViewTransition(() => { swap(); });
        // A newer navigation can interrupt this animation; that is expected, not an error.
        transition.ready.catch(() => { });
        transition.finished.catch(() => { });
    } else {
        swap().classList.add('view-enter');
    }
}

export function start() {
    window.addEventListener('hashchange', render);
    render();
}

export function refresh() {
    render();
}

export function currentView() {
    return current;
}
