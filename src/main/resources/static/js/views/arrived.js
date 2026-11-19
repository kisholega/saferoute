// Step 4 of the trip flow: arrived safely.
import { api, post } from '../api.js';
import { icon } from '../icons.js';
import { navigate } from '../router.js';
import { store } from '../store.js';
import { clockTime, confetti, firstName, formatKm, formatMinutes, html, toast } from '../ui.js';

export default {
    layout: 'page',
    auth: true,
    title: 'Arrived safely',
    mount(root, { params }) {
        root.innerHTML = html`<div class="page page-narrow"><div class="skeleton" style="height:320px;margin-top:2rem"></div></div>`;
        api(`/api/trips/${params.id}`).then(trip => {
            const minutes = trip.endedAt ? (new Date(trip.endedAt) - new Date(trip.startedAt)) / 60000 : trip.expectedMinutes;
            root.innerHTML = html`<div class="page page-narrow celebrate">
                <div class="check-burst"><svg viewBox="0 0 60 60"><path d="M16 31l9 9 19-20"/></svg></div>
                <p class="eyebrow">Trip complete</p>
                <h1 class="display" style="font-size:2.6rem;margin:.4rem 0">You made it, ${firstName(store.user.name)}!</h1>
                <p class="muted">Arrived at <strong>${trip.destinationName}</strong> at ${clockTime(trip.endedAt || new Date())}.
                    ${trip.notifyContacts ? 'Your contacts have been told you arrived safely.' : ''}</p>
                <div class="stat-tiles stagger" style="margin:1.8rem 0;text-align:left">
                    <div class="stat" style="--i:0"><div class="v num">${formatMinutes(Math.max(1, minutes))}</div><div class="k">Travel time</div></div>
                    <div class="stat" style="--i:1"><div class="v num">${formatKm(trip.distanceKm)}</div><div class="k">Distance</div></div>
                    <div class="stat" style="--i:2"><div class="v num">${trip.safetyScore != null ? Math.round(trip.safetyScore) : '–'}</div><div class="k">Route safety score</div></div>
                    <div class="stat" style="--i:3"><div class="v">${trip.notifyContacts ? icon('check', 'icon-lg') : '–'}</div><div class="k">Contacts updated</div></div>
                </div>
                <div class="card" style="text-align:left">
                    <div class="card-title">${icon('heart')} How did this route feel?</div>
                    <p class="card-sub">Your feedback becomes a community report that helps score routes for everyone.</p>
                    <div class="chips" style="margin-top:.9rem" data-feedback>
                        <button type="button" class="chip" data-f="SAFE_SPOT">${icon('heart')} Felt safe</button>
                        <button type="button" class="chip" data-f="POOR_LIGHTING">${icon('lamp')} Poorly lit</button>
                        <button type="button" class="chip" data-f="ISOLATED">${icon('people')} Too isolated</button>
                        <button type="button" class="chip" data-f="HARASSMENT">${icon('alert')} I was harassed</button>
                    </div>
                </div>
                <div class="row" style="justify-content:center;margin-top:1.6rem;gap:.6rem">
                    <button type="button" class="btn btn-primary btn-lg" data-again>${icon('navigate')} Plan another trip</button>
                    <a class="btn btn-ghost btn-lg" href="#/explore">${icon('compass')} Safety map</a>
                </div></div>`;
            confetti();
            store.tripContext = null;
            store.results = null;
            store.save();
            root.querySelector('[data-again]').addEventListener('click', () => { store.draft = null; store.save(); navigate('/plan'); });
            const geometry = trip.geometry;
            root.querySelectorAll('[data-f]').forEach(b => b.addEventListener('click', async () => {
                const point = geometry[Math.floor(geometry.length / 2)];
                try {
                    await post('/api/reports', { latitude: point[0], longitude: point[1], category: b.dataset.f, note: `After a trip to ${trip.destinationName}` });
                    root.querySelectorAll('[data-f]').forEach(c => { c.disabled = true; });
                    b.setAttribute('aria-pressed', 'true');
                    toast('Thank you. Your report helps keep others safe.', 'success');
                    if (b.dataset.f === 'HARASSMENT') toast('If you need support, call the women helpline 181 or 112.', 'info', 7000);
                } catch (error) {
                    toast(error.message, 'error');
                }
            }));
        }).catch(error => {
            toast(error.message, 'error');
            navigate('/plan', { replace: true });
        });
    },
};
