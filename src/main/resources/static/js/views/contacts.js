// Emergency contacts screen.
import { mountContactManager } from '../components/contacts.js';
import { icon } from '../icons.js';
import { store } from '../store.js';
import { html } from '../ui.js';

export default {
    layout: 'page',
    auth: true,
    title: 'Emergency contacts',
    back: '/plan',
    mount(root) {
        const channels = store.appInfo?.alertChannels || { push: true };
        root.innerHTML = html`<div class="page">
            <header class="page-head"><div class="grow">
                <p class="eyebrow">Your safety circle</p>
                <h1 class="display">Emergency contacts</h1>
                <p>The people alerted when you press SOS, miss a check-in, or start and finish a Safe Trip. Use <strong>Set up alerts</strong> once per contact so their phone gets instant notifications.</p>
            </div></header>
            <div class="channel-status" style="margin-bottom:1.4rem">
                <div class="channel"><span class="c-icon tone-good">${icon('bell')}</span>
                    <div><strong>Push notifications</strong><div class="tiny muted">On · free · contacts use the ntfy app</div></div></div>
                <div class="channel"><span class="c-icon ${channels.email ? 'tone-good' : 'tone-neutral'}">${icon('mail')}</span>
                    <div><strong>Email</strong><div class="tiny muted">${channels.email ? 'On for contacts with an email address' : 'Off · add MAIL_USERNAME and MAIL_APP_PASSWORD on the server'}</div></div></div>
                <div class="channel"><span class="c-icon ${channels.sms ? 'tone-good' : 'tone-neutral'}">${icon('message')}</span>
                    <div><strong>SMS</strong><div class="tiny muted">${channels.sms ? 'On (Twilio)' : 'Off · add Twilio keys on the server'}</div></div></div>
            </div>
            <div data-manager></div>
            ${!store.appInfo?.publicLinks ? html`<div class="alert-box tone-info" style="margin-top:1.4rem">${icon('info')}
                <div><strong>Live tracking links work on this computer only</strong>The app runs on localhost, so contacts get a Google Maps link to your position in every alert. To give them the live tracking page too, expose the app with a tunnel and set PUBLIC_BASE_URL (see README).</div></div>` : ''}
        </div>`;
        mountContactManager(root.querySelector('[data-manager]'));
    },
};
