// First-run setup: 1 add contacts · 2 how alerts reach them · 3 location permission.
import { mountContactManager, openShare } from '../components/contacts.js';
import { icon } from '../icons.js';
import { navigate } from '../router.js';
import { store } from '../store.js';
import { currentPosition, firstName, html, toast } from '../ui.js';

export default {
    layout: 'page',
    auth: true,
    title: 'Set up',
    mount(root) {
        let step = 1;

        function draw() {
            root.innerHTML = html`<div class="page">
                <div class="stepper">${[1, 2, 3].map(n => html`<div class="s ${n <= step ? 'done' : ''}"></div>`)}</div>
                <div data-step></div></div>`;
            const host = root.querySelector('[data-step]');
            if (step === 1) stepContacts(host);
            if (step === 2) stepAlerts(host);
            if (step === 3) stepLocation(host);
        }

        function stepContacts(host) {
            host.innerHTML = html`
                <header class="page-head"><div class="grow">
                    <p class="eyebrow">Step 1 of 3</p>
                    <h1 class="display">Who should we call on, ${firstName(store.user.name)}?</h1>
                    <p>Add up to 5 people you trust: family, a close friend, a roommate. They are alerted in an emergency and can follow your trips live.</p>
                </div></header>
                <div data-manager></div>
                <div class="row" style="margin-top:1.6rem;gap:.6rem">
                    <button type="button" class="btn btn-primary btn-lg" data-next>Continue ${icon('arrow-right', 'arrow')}</button>
                    <button type="button" class="btn btn-ghost btn-lg" data-skip>Skip for now</button>
                </div>`;
            mountContactManager(host.querySelector('[data-manager]'));
            host.querySelector('[data-next]').addEventListener('click', () => {
                if (!(store.contacts || []).length) {
                    toast('Add at least one contact, or choose "Skip for now".', 'warn');
                    return;
                }
                step = 2; draw();
            });
            host.querySelector('[data-skip]').addEventListener('click', () => { step = 3; draw(); });
        }

        function stepAlerts(host) {
            const channels = store.appInfo?.alertChannels || { push: true };
            host.innerHTML = html`
                <header class="page-head"><div class="grow">
                    <p class="eyebrow">Step 2 of 3</p>
                    <h1 class="display">Make sure your alerts reach them</h1>
                    <p>SafeRoute sends alerts as instant <strong>push notifications</strong> through the free ntfy app. Send each contact their personal setup link. It takes them a minute.</p>
                </div></header>
                <div class="stack-lg">
                    <div class="stack">
                        ${(store.contacts || []).map(c => html`<div class="card row between wrap">
                            <div class="row"><span class="avatar">${c.name[0]}</span><div><strong>${c.name}</strong><div class="small muted">${c.relation}</div></div></div>
                            <button type="button" class="btn btn-soft btn-sm" data-share="${c.id}">${icon('qr', 'icon-sm')} Send setup link</button></div>`)}
                    </div>
                    <div class="channel-status">
                        <div class="channel"><span class="c-icon tone-good">${icon('bell')}</span><div><strong>Push</strong><div class="tiny muted">On · free · instant</div></div></div>
                        <div class="channel"><span class="c-icon ${channels.email ? 'tone-good' : 'tone-neutral'}">${icon('mail')}</span><div><strong>Email</strong><div class="tiny muted">${channels.email ? 'On' : 'Optional · set up on server'}</div></div></div>
                        <div class="channel"><span class="c-icon ${channels.sms ? 'tone-good' : 'tone-neutral'}">${icon('message')}</span><div><strong>SMS</strong><div class="tiny muted">${channels.sms ? 'On' : 'Optional · set up on server'}</div></div></div>
                    </div>
                    <p class="small muted">In an emergency you can also message contacts on WhatsApp or SMS straight from your phone.</p>
                </div>
                <div class="row" style="margin-top:1.6rem;gap:.6rem">
                    <button type="button" class="btn btn-primary btn-lg" data-next>Continue ${icon('arrow-right', 'arrow')}</button>
                    <button type="button" class="btn btn-ghost btn-lg" data-back>Back</button>
                </div>`;
            host.querySelectorAll('[data-share]').forEach(b => b.addEventListener('click', () =>
                openShare(store.contacts.find(c => c.id === Number(b.dataset.share)))));
            host.querySelector('[data-next]').addEventListener('click', () => { step = 3; draw(); });
            host.querySelector('[data-back]').addEventListener('click', () => { step = 1; draw(); });
        }

        function stepLocation(host) {
            host.innerHTML = html`
                <header class="page-head"><div class="grow">
                    <p class="eyebrow">Step 3 of 3</p>
                    <h1 class="display">Allow location for live safety</h1>
                    <p>Your location is used to start trips from where you are, warn you before risky stretches, and share your position with your contacts during a trip or an SOS. Never otherwise.</p>
                </div></header>
                <div class="card pad-lg row wrap" style="gap:1.2rem">
                    <span class="avatar lg" style="background:var(--grad)">${icon('locate', 'icon-lg')}</span>
                    <div style="flex:1;min-width:14rem"><strong>Location access</strong><p class="small muted" data-status>Not requested yet.</p></div>
                    <button type="button" class="btn btn-soft" data-allow>${icon('locate')} Allow location</button>
                </div>
                <div class="row" style="margin-top:1.6rem;gap:.6rem">
                    <button type="button" class="btn btn-primary btn-lg" data-done>Start planning ${icon('arrow-right', 'arrow')}</button>
                </div>`;
            host.querySelector('[data-allow]').addEventListener('click', async event => {
                event.currentTarget.classList.add('loading');
                const position = await currentPosition();
                event.currentTarget.classList.remove('loading');
                host.querySelector('[data-status]').textContent = position
                    ? `Got it: accurate to about ${Math.round(position.accuracy)} m.`
                    : 'Location is blocked or unavailable. You can still type places, and use trip simulation for demos.';
            });
            host.querySelector('[data-done]').addEventListener('click', () => navigate('/plan'));
        }

        draw();
    },
};
