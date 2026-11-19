// Place search for an input. Two-speed: instant prefix matches from the built-in city index first,
// then online results merged in. Keyboard: ↑ ↓ to move, Enter to pick, Esc to close.
import { api } from './api.js';
import { icon, iconForPlaceType } from './icons.js';
import { html, escapeHtml, raw, currentPosition, toast } from './ui.js';

let instance = 0;

/**
 * @param input    the <input>
 * @param host     element the dropdown is positioned in (position: relative)
 * @param options  { getBias(): {lat, lng} | null, allowCurrentLocation, onPick(place) }
 */
export function attachAutocomplete(input, host, { getBias, allowCurrentLocation = false, onPick } = {}) {
    const listId = `ac-${++instance}`;
    const list = document.createElement('ul');
    list.id = listId;
    list.className = 'ac-list';
    list.setAttribute('role', 'listbox');
    list.hidden = true;
    host.append(list);
    Object.assign(input, { autocomplete: 'off', spellcheck: false });
    input.setAttribute('role', 'combobox');
    input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-controls', listId);
    input.setAttribute('aria-expanded', 'false');

    let items = [];
    let active = -1;
    let selected = null;
    let quickTimer;
    let fullTimer;
    let controller;
    let query = '';

    const open = () => { list.hidden = false; input.setAttribute('aria-expanded', 'true'); };
    const close = () => { list.hidden = true; input.setAttribute('aria-expanded', 'false'); active = -1; };

    function highlight(name) {
        const q = query.trim().toLowerCase();
        if (!q) return escapeHtml(name);
        const lower = name.toLowerCase();
        let index = lower.indexOf(q);
        if (index > 0 && /\w/.test(lower[index - 1])) index = lower.indexOf(' ' + q) + 1 || -1;
        if (index < 0) return escapeHtml(name);
        return escapeHtml(name.slice(0, index)) + '<mark>' + escapeHtml(name.slice(index, index + q.length)) + '</mark>' + escapeHtml(name.slice(index + q.length));
    }

    function shortArea(place) {
        const parts = place.displayName.split(',').map(s => s.trim()).filter(Boolean);
        return parts.slice(1, 4).join(', ') || 'Bengaluru';
    }

    function renderList(message) {
        const rows = [];
        if (allowCurrentLocation && (!query.trim() || 'current location'.startsWith(query.trim().toLowerCase()))) {
            rows.push({ current: true });
        }
        items.forEach(place => rows.push(place));
        rendered = rows;
        if (!rows.length && !message) { close(); return; }
        list.innerHTML = html`${rows.map((row, i) => row.current
            ? html`<li class="ac-item" role="option" id="${listId}-${i}" data-i="${i}" aria-selected="${i === active}">
                    <span class="ac-icon here">${icon('locate')}</span>
                    <span class="ac-text"><strong>Current location</strong><span>Use GPS to find where you are</span></span></li>`
            : html`<li class="ac-item" role="option" id="${listId}-${i}" data-i="${i}" aria-selected="${i === active}">
                    <span class="ac-icon">${icon(iconForPlaceType(row.type))}</span>
                    <span class="ac-text"><strong>${raw(highlight(row.name))}</strong><span>${shortArea(row)}</span></span>
                    ${row.type ? html`<span class="ac-type">${row.type}</span>` : ''}</li>`)}
            ${message ? html`<li class="ac-message">${message}</li>` : ''}`;
        if (active >= 0) input.setAttribute('aria-activedescendant', `${listId}-${active}`);
        open();
    }
    let rendered = [];

    async function search(text, quick) {
        controller?.abort();
        controller = new AbortController();
        const params = new URLSearchParams({ query: text, quick: String(quick) });
        const bias = getBias?.();
        if (bias) {
            params.set('lat', bias.lat.toFixed(4));
            params.set('lon', bias.lng.toFixed(4));
        }
        try {
            const result = await api(`/api/locations/search?${params}`, { signal: controller.signal });
            if (text !== input.value.trim()) return;
            items = result;
            active = items.length ? (allowCurrentLocation && rendered[0]?.current ? 1 : 0) : -1;
            renderList(items.length || quick ? '' : 'No places found. Try the area name, a landmark or a metro station.');
        } catch (error) {
            if (error.name !== 'AbortError') renderList(error.message);
        }
    }

    async function choose(index) {
        const row = rendered[index];
        if (!row) return;
        close();
        if (row.current) {
            input.value = 'Locating…';
            const position = await currentPosition();
            if (!position) {
                input.value = '';
                toast('Location is unavailable. Allow location access, or type a place.', 'warn');
                return;
            }
            let label = 'Current location';
            try {
                const place = await api(`/api/locations/reverse?lat=${position.latitude}&lon=${position.longitude}`);
                label = place.name.startsWith('Near ') ? place.name : `${place.name} (current location)`;
            } catch { /* keep generic label */ }
            set({ label, latitude: position.latitude, longitude: position.longitude, accuracy: position.accuracy, current: true });
        } else {
            set({ label: row.name, displayName: row.displayName, latitude: row.latitude, longitude: row.longitude, type: row.type });
        }
        onPick?.(selected);
    }

    function set(place) {
        selected = place;
        input.value = place.label;
    }

    input.addEventListener('input', () => {
        selected = null;
        query = input.value;
        const text = input.value.trim();
        clearTimeout(quickTimer);
        clearTimeout(fullTimer);
        if (text.length < 2) {
            items = [];
            controller?.abort();
            if (allowCurrentLocation && document.activeElement === input) renderList(); else close();
            return;
        }
        quickTimer = setTimeout(() => search(text, true), 70);
        if (text.length >= 3) fullTimer = setTimeout(() => search(text, false), 320);
    });

    input.addEventListener('focus', () => {
        query = input.value;
        if (!input.value.trim() && allowCurrentLocation) renderList();
        else if (items.length && !selected) renderList();
    });

    input.addEventListener('keydown', event => {
        if (list.hidden || !rendered.length) return;
        if (event.key === 'ArrowDown') { event.preventDefault(); active = (active + 1) % rendered.length; renderList(); }
        else if (event.key === 'ArrowUp') { event.preventDefault(); active = (active - 1 + rendered.length) % rendered.length; renderList(); }
        else if (event.key === 'Enter' && active >= 0) { event.preventDefault(); choose(active); }
        else if (event.key === 'Escape') close();
    });

    list.addEventListener('mousedown', event => {
        const item = event.target.closest('.ac-item');
        if (!item) return;
        event.preventDefault();
        choose(Number(item.dataset.i));
    });

    input.addEventListener('blur', () => setTimeout(close, 160));

    return {
        get: () => (selected && input.value === selected.label ? selected : null),
        set,
        clear() { selected = null; input.value = ''; },
        text: () => input.value.trim(),
        destroy() { list.remove(); },
    };
}
