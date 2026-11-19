// Fake incoming call: a realistic ringing screen that gives a reason to leave an uncomfortable situation.
import { icon } from './icons.js';
import { el, html, initials, vibrate, avatarStyle } from './ui.js';

let audio = null;

function startRingtone() {
    try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        const gain = ctx.createGain();
        gain.gain.value = 0;
        gain.connect(ctx.destination);
        [440, 480].forEach(f => {
            const osc = ctx.createOscillator();
            osc.frequency.value = f;
            osc.connect(gain);
            osc.start();
        });
        // Classic ring cadence: 2 s on, 4 s off.
        const schedule = () => {
            const t = ctx.currentTime;
            gain.gain.setValueAtTime(0.08, t);
            gain.gain.setValueAtTime(0, t + 2);
        };
        schedule();
        const timer = setInterval(schedule, 6000);
        audio = { ctx, timer };
    } catch { audio = null; }
    vibrate([800, 400, 800, 400, 800]);
}

function stopRingtone() {
    if (audio) {
        clearInterval(audio.timer);
        audio.ctx.close();
        audio = null;
    }
    vibrate(0);
}

export function fakeCall(callerName = 'Mom', delaySeconds = 0) {
    setTimeout(() => {
        const screen = el(html`<div class="fake-call" role="dialog" aria-label="Incoming call">
            <div class="caller">
                <p class="small" style="opacity:.7;letter-spacing:.1em">INCOMING CALL</p>
                <div class="avatar" style="${avatarStyle(callerName)}">${initials(callerName)}</div>
                <h2>${callerName}</h2><p style="opacity:.75" data-status>mobile</p>
            </div>
            <div class="call-buttons">
                <button type="button" class="decline" aria-label="Decline">${icon('phone-off', 'icon-lg')}</button>
                <button type="button" class="accept" aria-label="Accept">${icon('phone', 'icon-lg')}</button>
            </div></div>`);
        document.body.append(screen);
        startRingtone();
        const end = () => { stopRingtone(); screen.remove(); };
        screen.querySelector('.decline').addEventListener('click', end);
        screen.querySelector('.accept').addEventListener('click', () => {
            stopRingtone();
            let seconds = 0;
            const status = screen.querySelector('[data-status]');
            const timer = setInterval(() => {
                seconds += 1;
                status.textContent = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
            }, 1000);
            status.textContent = '00:00';
            const buttons = screen.querySelector('.call-buttons');
            buttons.innerHTML = '';
            const hang = el(html`<button type="button" class="decline" aria-label="End call">${icon('phone-off', 'icon-lg')}</button>`);
            hang.addEventListener('click', () => { clearInterval(timer); screen.remove(); });
            buttons.append(hang);
        });
    }, delaySeconds * 1000);
}
