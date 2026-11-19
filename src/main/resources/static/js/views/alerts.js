// Alert history: every alert and exactly who it reached, how.
import { api, post } from '../api.js';
import { icon } from '../icons.js';
import { html, timeAgo, toast } from '../ui.js';

const TYPES = {
    SOS: { icon: 'siren', label: 'SOS', tone: 'tone-bad' },
    CHECK_IN_MISSED: { icon: 'alert', label: 'Missed check-in', tone: 'tone-bad' },
    LOST_CONTACT: { icon: 'wifi', label: 'Lost contact', tone: 'tone-warn' },
    TRIP_STARTED: { icon: 'navigate', label: 'Trip started', tone: 'tone-info' },
    ARRIVED: { icon: 'check', label: 'Arrived safely', tone: 'tone-good' },
    ALL_CLEAR: { icon: 'heart', label: 'All clear', tone: 'tone-good' },
    TEST: { icon: 'bell', label: 'Test alert', tone: 'tone-neutral' },
};
const CHANNEL_ICON = { PUSH: 'bell', EMAIL: 'mail', SMS: 'message' };

export default {
    layout: 'page',
    auth: true,
    title: 'Alert history',
    back: '/plan',
    mount(root) {
        root.innerHTML = html`<div class="page page-narrow">
            <header class="page-head"><div class="grow"><p class="eyebrow">Transparency</p><h1 class="display">Alert history</h1>
                <p>Every message SafeRoute sent for you, and whether it reached each contact.</p></div></header>
            <div class="timeline" data-list><div class="skeleton" style="height:110px"></div><div class="skeleton" style="height:110px"></div></div></div>`;
        const list = root.querySelector('[data-list]');

        async function load() {
            try {
                const alerts = await api('/api/alerts');
                if (!alerts.length) {
                    list.innerHTML = html`<div class="empty"><div class="e-icon">${icon('bell', 'icon-xl')}</div>
                        <h3 class="display" style="font-size:1.3rem">No alerts yet</h3><p>Send a test alert from <a href="#/contacts">Emergency contacts</a> to check everything works.</p></div>`.toString();
                    return;
                }
                list.innerHTML = html`${alerts.map((a, i) => {
                    const t = TYPES[a.type] || TYPES.TEST;
                    const shown = a.deliveries.filter(d => d.status !== 'SKIPPED' || d.channel === 'PUSH');
                    return html`<article class="t-item" style="animation:rise .45s var(--ease) both;animation-delay:${i * 40}ms">
                        <div class="t-icon ${t.tone}">${icon(t.icon, 'icon-lg')}</div>
                        <div style="min-width:0">
                            <div class="row between wrap"><strong>${t.label}</strong><span class="tiny muted">${timeAgo(a.createdAt)} · ${new Date(a.createdAt).toLocaleString()}</span></div>
                            <p class="small muted" style="margin-top:.2rem;overflow:hidden;text-overflow:ellipsis;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical">${a.message || ''}</p>
                            <div class="delivery-chips">${shown.map(d => html`<span class="badge ${d.status === 'SENT' ? 'tone-good' : d.status === 'FAILED' ? 'tone-bad' : 'tone-neutral'}" title="${d.detail || ''}">
                                ${icon(CHANNEL_ICON[d.channel])} ${d.contactName} · ${d.status === 'SENT' ? 'delivered' : d.status.toLowerCase()}</span>`)}</div>
                            ${a.status === 'ACTIVE' ? html`<button type="button" class="btn btn-safe btn-sm" style="margin-top:.7rem" data-resolve="${a.id}">${icon('shield-check', 'icon-sm')} I'm safe now</button>` : ''}
                        </div></article>`;
                })}`.toString();
                list.querySelectorAll('[data-resolve]').forEach(b => b.addEventListener('click', async () => {
                    b.classList.add('loading');
                    try {
                        await post(`/api/alerts/${b.dataset.resolve}/resolve`);
                        toast('Resolved. Your contacts have been told you are safe.', 'success');
                        load();
                    } catch (error) { toast(error.message, 'error'); b.classList.remove('loading'); }
                }));
            } catch (error) {
                list.innerHTML = html`<div class="alert-box tone-bad">${icon('alert')}<div>${error.message}</div></div>`.toString();
            }
        }
        load();
    },
};
