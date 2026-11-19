// Landing screen for signed-out visitors.
import { icon } from '../icons.js';
import { navigate } from '../router.js';
import { html, raw } from '../ui.js';

const PHONE_ART = `
<svg viewBox="0 0 260 440" aria-hidden="true">
  <defs>
    <linearGradient id="art-route" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#6d28d9"/><stop offset=".6" stop-color="#a21caf"/><stop offset="1" stop-color="#e11d74"/></linearGradient>
    <pattern id="art-blocks" width="46" height="46" patternUnits="userSpaceOnUse"><rect width="46" height="46" fill="#f6f2fb"/><rect x="5" y="5" width="36" height="36" rx="6" fill="#efe9f7"/></pattern>
  </defs>
  <rect width="260" height="440" fill="url(#art-blocks)"/>
  <path d="M0 140 H260 M0 300 H260 M90 0 V440 M190 0 V440" stroke="#fff" stroke-width="12"/>
  <rect x="140" y="320" width="90" height="70" rx="14" fill="#d9f2e5"/>
  <circle cx="60" cy="90" r="28" fill="#fde2ee"/>
  <path d="M60 360 C 90 300, 150 320, 160 250 S 230 160, 200 90" fill="none" stroke="#fff" stroke-width="16" stroke-linecap="round"/>
  <path class="art-route" d="M60 360 C 90 300, 150 320, 160 250 S 230 160, 200 90" fill="none" stroke="url(#art-route)" stroke-width="8" stroke-linecap="round"/>
  <circle cx="60" cy="360" r="9" fill="#6d28d9" stroke="#fff" stroke-width="4"/>
  <path d="M200 98s-14-12-14-24a14 14 0 0128 0c0 12-14 24-14 24z" fill="#e11d74" stroke="#fff" stroke-width="3"/>
  <circle r="7" fill="#fff" stroke="#6d28d9" stroke-width="4">
    <animateMotion dur="6s" begin="3.4s" repeatCount="indefinite" path="M60 360 C 90 300, 150 320, 160 250 S 230 160, 200 90"/>
  </circle>
  <rect x="18" y="20" width="224" height="44" rx="14" fill="#fff" opacity=".95"/>
  <circle cx="40" cy="42" r="8" fill="#12a26a"/><rect x="56" y="34" width="110" height="7" rx="3.5" fill="#1f1633" opacity=".8"/>
  <rect x="56" y="46" width="70" height="6" rx="3" fill="#a59fb5"/>
</svg>`;

export default {
    layout: 'page',
    guestOnly: true,
    title: 'Every journey, a safer one',
    mount(root) {
        root.innerHTML = html`<div class="page">
            <section class="hero">
                <div class="stagger">
                    <p class="eyebrow" style="--i:0">Women's safety · AI route planning</p>
                    <h1 class="display" style="--i:1;margin-top:.8rem">Every journey,<br><em class="grad-text">a safer one.</em></h1>
                    <p class="lead" style="--i:2">SafeRoute AI compares routes by what is really on the street (lighting, people around, help nearby) and keeps the people you trust with you, live, until you arrive.</p>
                    <div class="hero-cta" style="--i:3">
                        <button type="button" class="btn btn-primary btn-lg" data-go="/auth?mode=register">Get started, it's free ${icon('arrow-right', 'arrow')}</button>
                        <button type="button" class="btn btn-ghost btn-lg" data-go="/auth?mode=login">I have an account</button>
                    </div>
                    <div class="trust-row" style="--i:4">
                        <span>${icon('check')} Live street data from OpenStreetMap</span>
                        <span>${icon('check')} Instant alerts to your contacts</span>
                        <span>${icon('check')} One-press SOS</span>
                    </div>
                </div>
                <div class="hero-art">
                    <div class="blob a"></div><div class="blob b"></div>
                    <div class="phone">${raw(PHONE_ART)}</div>
                    <div class="float-card" style="left:-2%;top:22%;animation-delay:-2s">
                        <span class="fi tone-good">${icon('shield-check')}</span><span>Safety score<br><span class="grad-text" style="font-size:1.1rem">86 / 100</span></span></div>
                    <div class="float-card" style="right:-4%;top:46%;animation-delay:-4s">
                        <span class="fi tone-info">${icon('users')}</span><span>Amma is following<br><span class="muted small">your trip live</span></span></div>
                    <div class="float-card" style="left:4%;bottom:8%">
                        <span class="fi" style="background:#e8efff;color:#2563eb">${icon('police')}</span><span>Police station<br><span class="muted small">300 m from route</span></span></div>
                </div>
            </section>

            <section class="features">
                <article class="feature"><div class="f-icon tone-info">${icon('route', 'icon-lg')}</div>
                    <h3>Prevent</h3><p>Every route is scored stretch by stretch: street lamps, open shops, transit, police and hospitals, community reports and the time of day. The safest practical route wins.</p></article>
                <article class="feature"><div class="f-icon tone-good">${icon('shield-check', 'icon-lg')}</div>
                    <h3>Protect</h3><p>Safe Trip shares your live location, warns you before risky stretches, and checks on you if you leave the route or stop responding.</p></article>
                <article class="feature"><div class="f-icon tone-bad">${icon('siren', 'icon-lg')}</div>
                    <h3>Respond</h3><p>Hold SOS and every contact gets a push notification, email or SMS with your live location, with 112 and women's helplines one tap away.</p></article>
            </section>

            <h2 class="display" style="font-size:2rem;margin-bottom:1.6rem">How it works</h2>
            <section class="steps-row">
                <div class="step-card"><h4>Add trusted contacts</h4><p>They get your alerts as instant push notifications. Free, no account needed.</p></div>
                <div class="step-card"><h4>Say where you're going</h4><p>Type any place in the city. Suggestions appear as you type.</p></div>
                <div class="step-card"><h4>Pick the safest route</h4><p>See why each route scores the way it does, on a live map.</p></div>
                <div class="step-card"><h4>Travel with a safety net</h4><p>Contacts follow live, and you get warnings and check-ins until you arrive.</p></div>
            </section>
            <p class="tiny muted" style="margin-top:3rem;text-align:center">SafeRoute AI is a safety aid, not a guarantee. In an emergency, always call 112.</p>
        </div>`;
        root.querySelectorAll('[data-go]').forEach(b => b.addEventListener('click', () => navigate(b.dataset.go)));
    },
};
