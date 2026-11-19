// Sign in / create account.
import { ApiError, post } from '../api.js';
import { loadContacts } from '../components/contacts.js';
import { icon } from '../icons.js';
import { navigate } from '../router.js';
import { store } from '../store.js';
import { firstName, html, toast } from '../ui.js';

export default {
    layout: 'page',
    guestOnly: true,
    title: 'Sign in',
    back: '/welcome',
    mount(root, { query }) {
        let mode = query.mode === 'login' ? 'login' : 'register';

        function draw() {
            root.innerHTML = html`<div class="auth-wrap">
                <aside class="auth-side">
                    <svg class="rings" viewBox="0 0 480 480"><circle cx="240" cy="240" r="80"/><circle cx="240" cy="240" r="140"/><circle cx="240" cy="240" r="200"/><circle cx="240" cy="240" r="236"/></svg>
                    <p class="eyebrow" style="color:#fff;opacity:.8">SafeRoute AI</p>
                    <h2 style="margin-top:.6rem">Walk home with<br>your people beside you.</h2>
                    <p>Your contacts are alerted the moment you need them, and they can follow your trip live.</p>
                </aside>
                <section class="auth-form">
                    <div class="auth-card stagger">
                        <div class="tabs" role="tablist" style="--i:0">
                            <button type="button" role="tab" aria-selected="${mode === 'register'}" data-mode="register">Create account</button>
                            <button type="button" role="tab" aria-selected="${mode === 'login'}" data-mode="login">Sign in</button>
                        </div>
                        <h1 class="display" style="font-size:2rem;--i:1">${mode === 'register' ? 'Start travelling safer' : 'Welcome back'}</h1>
                        <p class="muted" style="margin:.4rem 0 1.4rem;--i:2">${mode === 'register' ? 'Takes 30 seconds. Your details are only used to alert the contacts you choose.' : 'Sign in to plan a safe trip.'}</p>
                        <form class="stack" novalidate style="--i:3">
                            ${mode === 'register' ? html`<div class="field"><label for="aName">Your name</label>
                                <div class="input-wrap">${icon('user')}<input class="input" id="aName" name="name" autocomplete="name" required maxlength="80" placeholder="Priya Sharma"></div>
                                <div class="field-error" data-error="name"></div></div>` : ''}
                            <div class="field"><label for="aEmail">Email</label>
                                <div class="input-wrap">${icon('mail')}<input class="input" id="aEmail" name="email" type="email" autocomplete="email" required placeholder="you@example.com"></div>
                                <div class="field-error" data-error="email"></div></div>
                            ${mode === 'register' ? html`<div class="field"><label for="aPhone">Mobile number</label>
                                <div class="input-wrap">${icon('phone')}<input class="input" id="aPhone" name="phone" type="tel" autocomplete="tel" required placeholder="98765 43210"></div>
                                <div class="hint">Shown to your contacts so they can call you back.</div>
                                <div class="field-error" data-error="phone"></div></div>` : ''}
                            <div class="field"><label for="aPass">Password</label>
                                <div class="input-wrap">${icon('lock')}<input class="input" id="aPass" name="password" type="password" autocomplete="${mode === 'register' ? 'new-password' : 'current-password'}" required minlength="8" placeholder="${mode === 'register' ? 'At least 8 characters' : 'Your password'}">
                                    <button type="button" class="icon-btn input-action" style="width:36px;height:36px;box-shadow:none;border:0" data-toggle aria-label="Show password">${icon('eye')}</button></div>
                                <div class="field-error" data-error="password"></div></div>
                            <div class="field-error" data-error="form"></div>
                            <button type="submit" class="btn btn-primary btn-lg btn-block">${mode === 'register' ? 'Create account' : 'Sign in'} ${icon('arrow-right', 'arrow')}</button>
                        </form>
                    </div>
                </section></div>`;
            root.querySelectorAll('[data-mode]').forEach(b => b.addEventListener('click', () => {
                mode = b.dataset.mode;
                history.replaceState(null, '', `#/auth?mode=${mode}`);
                draw();
            }));
            const pass = root.querySelector('[name="password"]');
            root.querySelector('[data-toggle]').addEventListener('click', event => {
                const show = pass.type === 'password';
                pass.type = show ? 'text' : 'password';
                event.currentTarget.innerHTML = html`${icon(show ? 'eye-off' : 'eye')}`.toString();
            });
            root.querySelector('form').addEventListener('submit', submit);
            root.querySelector('input').focus();
        }

        async function submit(event) {
            event.preventDefault();
            const form = event.currentTarget;
            form.querySelectorAll('[data-error]').forEach(e => { e.textContent = ''; });
            form.querySelectorAll('.invalid').forEach(e => e.classList.remove('invalid'));
            const data = Object.fromEntries(new FormData(form));
            const button = form.querySelector('[type="submit"]');
            button.classList.add('loading');
            try {
                store.user = await post(mode === 'register' ? '/api/auth/register' : '/api/auth/login', data);
                await loadContacts().catch(() => []);
                toast(mode === 'register' ? `Welcome, ${firstName(store.user.name)}!` : `Welcome back, ${firstName(store.user.name)}.`, 'success');
                navigate(mode === 'register' || !(store.contacts || []).length ? '/setup' : '/plan');
            } catch (error) {
                button.classList.remove('loading');
                if (error instanceof ApiError && Object.keys(error.fieldErrors).length) {
                    Object.entries(error.fieldErrors).forEach(([field, message]) => {
                        const box = form.querySelector(`[data-error="${field}"]`);
                        if (box) box.textContent = message;
                        form.elements[field]?.classList.add('invalid');
                    });
                } else {
                    form.querySelector('[data-error="form"]').textContent = error.message;
                    form.classList.add('shake');
                    setTimeout(() => form.classList.remove('shake'), 450);
                }
            }
        }

        draw();
    },
};
