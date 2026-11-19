// Profile, alert channel status and credits.
import { ApiError, put } from '../api.js';
import { icon } from '../icons.js';
import { store } from '../store.js';
import { avatarStyle, formatPhone, html, initials, toast } from '../ui.js';
import { setThemePreference, themePreference } from '../theme.js';

export default {
    layout: 'page',
    auth: true,
    title: 'Profile',
    back: '/plan',
    mount(root) {
        const info = store.appInfo || {};
        const channels = info.alertChannels || {};
        root.innerHTML = html`<div class="page page-narrow">
            <header class="page-head"><div class="row" style="gap:1rem">
                <span class="avatar lg" style="${avatarStyle(store.user.name)}">${initials(store.user.name)}</span>
                <div><h1 class="display" style="font-size:2rem">${store.user.name}</h1><p class="muted">${store.user.email}</p></div></div></header>
            <section class="card pad-lg">
                <div class="card-title">${icon('user')} Your details</div>
                <p class="card-sub">Your first name and phone number appear in alerts so contacts can call you back.</p>
                <form class="stack" style="margin-top:1rem" novalidate>
                    <div class="grid grid-2">
                        <div class="field"><label for="pName">Name</label><input class="input" id="pName" name="name" value="${store.user.name}" maxlength="80"><div class="field-error" data-error="name"></div></div>
                        <div class="field"><label for="pPhone">Mobile number</label><input class="input" id="pPhone" name="phone" value="${formatPhone(store.user.phone)}"><div class="field-error" data-error="phone"></div></div>
                    </div>
                    <div><button type="submit" class="btn btn-primary">Save changes</button></div>
                </form>
            </section>
            <section class="card pad-lg" style="margin-top:1rem">
                <div class="card-title">${icon('moon')} Appearance</div>
                <p class="card-sub">Dark mode is easier on the eyes at night and saves battery on OLED phones.</p>
                <div class="segmented" style="margin-top:1rem;max-width:420px" data-theme-choice>
                    ${[['system', 'sliders', 'System'], ['light', 'sun', 'Light'], ['dark', 'moon', 'Dark']].map(([value, ic, label]) =>
                        html`<button type="button" data-value="${value}" aria-pressed="${themePreference() === value}">${icon(ic)} ${label}</button>`)}
                </div>
            </section>
            <section class="card pad-lg" style="margin-top:1rem">
                <div class="card-title">${icon('bell')} How alerts are delivered</div>
                <div class="stack" style="margin-top:1rem">
                    <div class="row between"><span class="row">${icon('bell')} Push notifications (ntfy)</span><span class="badge ${channels.push ? 'tone-good' : 'tone-neutral'}">${channels.push ? 'On' : 'Off'}</span></div>
                    <div class="row between"><span class="row">${icon('mail')} Email (SMTP)</span><span class="badge ${channels.email ? 'tone-good' : 'tone-neutral'}">${channels.email ? 'On' : 'Not configured'}</span></div>
                    <div class="row between"><span class="row">${icon('message')} SMS (Twilio)</span><span class="badge ${channels.sms ? 'tone-good' : 'tone-neutral'}">${channels.sms ? 'On' : 'Not configured'}</span></div>
                    <div class="row between"><span class="row">${icon('external')} Live tracking links</span><span class="badge ${info.publicLinks ? 'tone-good' : 'tone-warn'}">${info.publicLinks ? 'Public' : 'This computer only'}</span></div>
                </div>
                <p class="small muted" style="margin-top:1rem">Email, SMS and public links are switched on by the person running the server, in the <span class="code">.env</span> file (see <span class="code">.env.example</span> and the README).</p>
            </section>
            <section class="card pad-lg" style="margin-top:1rem">
                <div class="card-title">${icon('heart')} Built with open data</div>
                <p class="small muted" style="margin-top:.5rem">Map data © OpenStreetMap contributors (ODbL) · Map tiles © CARTO · Routing: OSRM / FOSSGIS · Place search: Photon & Nominatim · Push: ntfy · Machine learning: Weka.</p>
                <p class="small muted" style="margin-top:.5rem">SafeRoute AI is a safety aid, not a guarantee. In an emergency, call <a href="tel:112"><strong>112</strong></a>; women helpline <a href="tel:1091">1091</a> / <a href="tel:181">181</a>.</p>
            </section></div>`;

        root.querySelectorAll('[data-theme-choice] button').forEach(b => b.addEventListener('click', () => {
            setThemePreference(b.dataset.value);
            root.querySelectorAll('[data-theme-choice] button').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
        }));
        const form = root.querySelector('form');
        form.addEventListener('submit', async event => {
            event.preventDefault();
            form.querySelectorAll('[data-error]').forEach(e => { e.textContent = ''; });
            const button = form.querySelector('[type="submit"]');
            button.classList.add('loading');
            try {
                store.user = await put('/api/auth/me', { name: form.elements.name.value, phone: form.elements.phone.value });
                toast('Profile saved.', 'success');
            } catch (error) {
                if (error instanceof ApiError && Object.keys(error.fieldErrors).length) {
                    Object.entries(error.fieldErrors).forEach(([f, m]) => { const box = form.querySelector(`[data-error="${f}"]`); if (box) box.textContent = m; });
                } else toast(error.message, 'error');
            } finally {
                button.classList.remove('loading');
            }
        });
    },
};
