// Light / dark theme. The choice is remembered per browser; "system" follows the OS setting.
const KEY = 'saferoute.theme';
const media = window.matchMedia('(prefers-color-scheme: dark)');

/** 'light' | 'dark' | 'system' */
export function themePreference() {
    try {
        const value = localStorage.getItem(KEY);
        return value === 'light' || value === 'dark' ? value : 'system';
    } catch {
        return 'system';
    }
}

export function currentTheme() {
    return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

function resolve(preference) {
    return preference === 'system' ? (media.matches ? 'dark' : 'light') : preference;
}

function apply(theme) {
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#110c1a' : '#6d28d9');
    window.dispatchEvent(new CustomEvent('themechange', { detail: theme }));
}

/** Switches theme with a soft cross-fade (where the browser supports view transitions). */
function applyAnimated(theme) {
    if (theme === currentTheme()) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!document.startViewTransition || reduced) {
        apply(theme);
        return;
    }
    document.documentElement.classList.add('theme-switching');
    const transition = document.startViewTransition(() => apply(theme));
    transition.ready.catch(() => { });
    transition.finished.catch(() => { }).finally(() => document.documentElement.classList.remove('theme-switching'));
}

export function setThemePreference(preference) {
    try {
        if (preference === 'system') localStorage.removeItem(KEY); else localStorage.setItem(KEY, preference);
    } catch { /* storage blocked: still switch for this page */ }
    applyAnimated(resolve(preference));
}

export function toggleTheme() {
    const next = currentTheme() === 'dark' ? 'light' : 'dark';
    setThemePreference(next);
    return next;
}

export function initTheme() {
    apply(resolve(themePreference()));
    media.addEventListener?.('change', () => {
        if (themePreference() === 'system') applyAnimated(resolve('system'));
    });
}
