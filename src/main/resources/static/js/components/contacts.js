// Emergency contacts: cards, add/edit form, test alerts, and "share alert setup" (QR + WhatsApp invite).
import { api, ApiError, del, post, put } from '../api.js';
import { icon } from '../icons.js';
import { store } from '../store.js';
import {
    $, avatarStyle, copyText, firstName, formatPhone, html, initials, loadScript, openModal, toast, waLink,
} from '../ui.js';

export async function loadContacts() {
    store.contacts = await api('/api/emergency-contacts');
    return store.contacts;
}

export function pushLink(contact) {
    const server = store.appInfo?.pushServer || 'https://ntfy.sh';
    return `${server}/${contact.pushTopic}`;
}

/**
 * Renders the contact grid into `host` and keeps it up to date.
 * @param onChange called after any change with the new list
 */
export function mountContactManager(host, { onChange } = {}) {
    let contacts = store.contacts || [];
    let confirmDelete = null;
    const max = store.appInfo?.maxContacts || 5;

    async function refresh() {
        try {
            contacts = await loadContacts();
        } catch (error) {
            toast(error.message, 'error');
        }
        draw();
        onChange?.(contacts);
    }

    function draw() {
        host.innerHTML = html`<div class="grid grid-3 stagger">
            ${contacts.map((c, i) => html`<article class="contact-card" style="--i:${i}" data-id="${c.id}">
                <div class="contact-head">
                    <div class="avatar" style="${avatarStyle(c.name)}">${initials(c.name)}</div>
                    <div style="min-width:0;flex:1">
                        <h3>${c.name} ${c.isPrimary ? html`<span class="badge tone-info">${icon('star')} Primary</span>` : ''}</h3>
                        <p class="sub">${c.relation} · <a href="tel:${c.phone}">${formatPhone(c.phone)}</a></p>
                        ${c.email ? html`<p class="sub" style="overflow:hidden;text-overflow:ellipsis">${c.email}</p>` : ''}
                    </div>
                </div>
                <div class="channels">
                    <span class="badge tone-good">${icon('bell')} Push</span>
                    <span class="badge ${c.email && store.appInfo?.alertChannels?.email ? 'tone-good' : 'tone-neutral'}">${icon('mail')} Email</span>
                    <span class="badge ${store.appInfo?.alertChannels?.sms ? 'tone-good' : 'tone-neutral'}">${icon('message')} SMS</span>
                </div>
                ${confirmDelete === c.id
                    ? html`<div class="contact-actions"><span class="small" style="align-self:center;flex:2">Remove ${firstName(c.name)}?</span>
                        <button type="button" class="btn btn-sm btn-danger" data-act="confirm-delete">Remove</button>
                        <button type="button" class="btn btn-sm btn-ghost" data-act="cancel-delete">Keep</button></div>`
                    : html`<div class="contact-actions">
                        <button type="button" class="btn btn-sm btn-soft" data-act="share">${icon('qr', 'icon-sm')} Set up alerts</button>
                        <button type="button" class="btn btn-sm btn-ghost" data-act="test">${icon('send', 'icon-sm')} Test</button>
                    </div>
                    <div class="contact-actions">
                        ${!c.isPrimary ? html`<button type="button" class="btn btn-sm btn-ghost" data-act="primary">${icon('star', 'icon-sm')} Make primary</button>` : html`<span class="tiny muted" style="flex:1;align-self:center">Called first in an emergency</span>`}
                        <button type="button" class="btn btn-sm btn-ghost icon-only" data-act="edit" aria-label="Edit ${c.name}" title="Edit">${icon('edit', 'icon-sm')}</button>
                        <button type="button" class="btn btn-sm btn-ghost icon-only" data-act="delete" aria-label="Remove ${c.name}" title="Remove">${icon('trash', 'icon-sm')}</button>
                    </div>`}
            </article>`)}
            ${contacts.length < max ? html`<button type="button" class="add-card" style="--i:${contacts.length}" data-act="add">
                <span class="plus">${icon('plus', 'icon-lg')}</span>
                <span>Add ${contacts.length ? 'another' : 'your first'} contact</span>
                <span class="tiny">${contacts.length} of ${max}</span></button>` : ''}
        </div>`;
    }

    host.addEventListener('click', async event => {
        const button = event.target.closest('[data-act]');
        if (!button) return;
        const card = button.closest('[data-id]');
        const contact = card ? contacts.find(c => c.id === Number(card.dataset.id)) : null;
        switch (button.dataset.act) {
            case 'add': openContactForm(null, refresh); break;
            case 'edit': openContactForm(contact, refresh); break;
            case 'share': openShare(contact); break;
            case 'test': await sendTest(contact, button); break;
            case 'delete': confirmDelete = contact.id; draw(); break;
            case 'cancel-delete': confirmDelete = null; draw(); break;
            case 'confirm-delete':
                try {
                    await del(`/api/emergency-contacts/${contact.id}`);
                    toast(`${contact.name} was removed.`, 'success');
                } catch (error) { toast(error.message, 'error'); }
                confirmDelete = null;
                await refresh();
                break;
            case 'primary':
                try {
                    await put(`/api/emergency-contacts/${contact.id}`, { ...contact, isPrimary: true });
                    toast(`${contact.name} is now your primary contact.`, 'success');
                } catch (error) { toast(error.message, 'error'); }
                await refresh();
                break;
            default: break;
        }
    });

    draw();
    refresh();
    return { refresh };
}

export function openContactForm(contact, onSaved) {
    const editing = Boolean(contact);
    const modal = openModal(html`
        <button type="button" class="icon-btn modal-close" data-close aria-label="Close">${icon('x')}</button>
        <p class="eyebrow">${editing ? 'Edit contact' : 'New emergency contact'}</p>
        <h2 class="display" style="margin:.3rem 0 1.2rem">${editing ? contact.name : 'Who should we alert?'}</h2>
        <form class="stack" novalidate>
            <div class="field"><label for="cName">Name</label>
                <div class="input-wrap">${icon('user')}<input class="input" id="cName" name="name" maxlength="80" required value="${contact?.name || ''}" placeholder="e.g. Amma"></div>
                <div class="field-error" data-error="name"></div></div>
            <div class="field"><label for="cPhone">Mobile number</label>
                <div class="input-wrap">${icon('phone')}<input class="input" id="cPhone" name="phone" type="tel" inputmode="tel" maxlength="25" required value="${contact ? formatPhone(contact.phone) : ''}" placeholder="98765 43210"></div>
                <div class="hint">Numbers without a country code are treated as Indian (+91).</div>
                <div class="field-error" data-error="phone"></div></div>
            <div class="field"><label for="cEmail">Email <span class="muted">(optional)</span></label>
                <div class="input-wrap">${icon('mail')}<input class="input" id="cEmail" name="email" type="email" maxlength="120" value="${contact?.email || ''}" placeholder="name@example.com"></div>
                <div class="field-error" data-error="email"></div></div>
            <div class="field"><label>Relationship</label>
                <div class="chips" data-relations>${['Mother', 'Father', 'Sister', 'Brother', 'Friend', 'Partner', 'Roommate'].map(r =>
                    html`<button type="button" class="chip" aria-pressed="${contact?.relation === r}" data-rel="${r}">${r}</button>`)}</div>
                <input class="input" name="relation" maxlength="40" required value="${contact?.relation || ''}" placeholder="Or type another relationship">
                <div class="field-error" data-error="relation"></div></div>
            ${!editing || !contact.isPrimary ? html`<label class="switch"><input type="checkbox" name="isPrimary"><span class="track"></span><span class="small"><strong>Primary contact</strong> · called first in an emergency</span></label>` : ''}
            <button type="submit" class="btn btn-primary btn-lg btn-block">${editing ? 'Save changes' : 'Save contact'} ${icon('arrow-right', 'arrow')}</button>
        </form>`);

    const form = $('form', modal.root);
    const relationInput = form.elements.relation;
    form.querySelectorAll('[data-rel]').forEach(chip => chip.addEventListener('click', () => {
        relationInput.value = chip.dataset.rel;
        form.querySelectorAll('[data-rel]').forEach(c => c.setAttribute('aria-pressed', String(c === chip)));
    }));
    relationInput.addEventListener('input', () => form.querySelectorAll('[data-rel]')
        .forEach(c => c.setAttribute('aria-pressed', String(c.dataset.rel === relationInput.value))));

    form.addEventListener('submit', async event => {
        event.preventDefault();
        form.querySelectorAll('[data-error]').forEach(e => { e.textContent = ''; });
        form.querySelectorAll('.invalid').forEach(e => e.classList.remove('invalid'));
        const body = {
            name: form.elements.name.value.trim(),
            phone: form.elements.phone.value.trim(),
            email: form.elements.email.value.trim() || null,
            relation: relationInput.value.trim(),
            isPrimary: form.elements.isPrimary?.checked || false,
        };
        const local = {};
        if (body.name.length < 2) local.name = 'Enter a name (at least 2 characters).';
        if (!body.phone) local.phone = 'Enter a mobile number.';
        if (!body.relation) local.relation = 'Pick or type the relationship.';
        if (Object.keys(local).length) { showErrors(form, local); return; }

        const submit = form.querySelector('[type="submit"]');
        submit.classList.add('loading');
        try {
            const saved = editing ? await put(`/api/emergency-contacts/${contact.id}`, body) : await post('/api/emergency-contacts', body);
            modal.close();
            toast(`${saved.name} saved.`, 'success');
            await onSaved?.(saved);
            if (!editing) openShare(saved, true);
        } catch (error) {
            submit.classList.remove('loading');
            if (error instanceof ApiError && Object.keys(error.fieldErrors).length) showErrors(form, error.fieldErrors);
            else toast(error.message, 'error');
        }
    });
}

function showErrors(form, errors) {
    Object.entries(errors).forEach(([field, message]) => {
        const box = form.querySelector(`[data-error="${field}"]`);
        if (box) box.textContent = message;
        form.elements[field]?.classList.add('invalid');
    });
    form.querySelector('.invalid')?.focus();
}

async function sendTest(contact, button) {
    button.classList.add('loading');
    try {
        let alert = await post(`/api/emergency-contacts/${contact.id}/test`);
        for (let i = 0; i < 10 && alert.deliveryInProgress; i++) {
            await new Promise(r => setTimeout(r, 700));
            alert = await api(`/api/alerts/${alert.id}`);
        }
        const sent = alert.deliveries.filter(d => d.status === 'SENT').map(d => d.channel.toLowerCase());
        const failed = alert.deliveries.filter(d => d.status === 'FAILED');
        if (sent.length) toast(`Test sent to ${contact.name} by ${sent.join(' & ')}. Ask them to check their phone.`, 'success', 5000);
        if (failed.length) toast(`${failed.map(f => f.channel.toLowerCase()).join(', ')} failed: ${failed[0].detail}`, 'error', 6000);
        if (!sent.length && !failed.length) toast('No channel could reach this contact yet.', 'warn');
    } catch (error) {
        toast(error.message, 'error');
    } finally {
        button.classList.remove('loading');
    }
}

/** How a contact starts receiving push alerts: install ntfy, subscribe via link / QR. */
export async function openShare(contact, justAdded = false) {
    const link = pushLink(contact);
    const me = firstName(store.user?.name) || 'I';
    const invite = `Hi ${firstName(contact.name)}, ${me} added you as an emergency contact on SafeRoute AI. `
        + `To receive my safety alerts instantly:\n1. Install the free "ntfy" app (Play Store / App Store)\n`
        + `2. Open this link, or in the app tap + and subscribe to: ${contact.pushTopic}\n${link}\nThank you 💜`;
    const modal = openModal(html`
        <button type="button" class="icon-btn modal-close" data-close aria-label="Close">${icon('x')}</button>
        <p class="eyebrow">${justAdded ? 'One more step' : 'Alert setup'}</p>
        <h2 class="display" style="margin:.3rem 0 .4rem">Let ${firstName(contact.name)} receive your alerts</h2>
        <p class="muted small">Alerts arrive as instant push notifications through <strong>ntfy</strong>, a free, open-source app. No account needed.</p>
        <div class="qr" data-qr><div class="skeleton" style="width:100%;height:100%"></div></div>
        <ol class="steps-list">
            <li><span>${firstName(contact.name)} installs the free <strong>ntfy</strong> app (Android / iOS), or opens the link in a browser.</span></li>
            <li><span>They scan this code or open the link, then tap <strong>Subscribe</strong>.</span></li>
            <li><span>Press <strong>Test</strong> on their card to check it works.</span></li>
        </ol>
        <p class="tiny muted" style="margin:.8rem 0 .3rem">Private topic: <span class="code">${contact.pushTopic}</span></p>
        <div class="grid grid-2" style="margin-top:1rem">
            <a class="btn btn-primary" href="${waLink(contact.phone, invite)}" target="_blank" rel="noopener">${icon('chat')} Send via WhatsApp</a>
            <button type="button" class="btn btn-ghost" data-copy>${icon('copy')} Copy invite</button>
        </div>`);
    modal.root.querySelector('[data-copy]').addEventListener('click', async () => {
        if (await copyText(invite)) toast('Invite copied. Paste it in any chat.', 'success');
    });
    try {
        await loadScript('https://cdn.jsdelivr.net/npm/qrcode-generator@1.4.4/qrcode.min.js');
        const qr = window.qrcode(0, 'M');
        qr.addData(link);
        qr.make();
        modal.root.querySelector('[data-qr]').innerHTML = qr.createSvgTag({ cellSize: 5, margin: 0, scalable: true });
    } catch {
        modal.root.querySelector('[data-qr]').innerHTML = html`<p class="small muted" style="padding:1rem;text-align:center">QR code unavailable offline. Share the link instead.</p>`.toString();
    }
}
