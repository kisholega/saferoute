// "How the AI works": the scoring pipeline, model comparison, confusion matrix, feature importance.
import { api } from '../api.js';
import { barChart, dataTable, heatmap, wireTooltips } from '../charts.js';
import { icon } from '../icons.js';
import { store } from '../store.js';
import { countUp, html } from '../ui.js';

const FEATURE_NAMES = {
    time_of_travel: 'Time of travel', lighting_condition: 'Lighting', crowd_density: 'People around',
    incident_count: 'Reported incidents', road_isolation: 'Road isolation', police_station_km: 'Distance to police',
    hospital_km: 'Distance to hospital', public_space_available: 'Open public places',
};

const PIPELINE = [
    ['route', 'Real routes', 'OSRM walking / driving routes, plus safe detours'],
    ['layers', 'Street data', 'OpenStreetMap: lamps, shops, transit, police, hospitals'],
    ['target', 'Every 120 m', 'Each stretch gets 8 features, incl. time of day'],
    ['sparkles', 'Random Forest', '100 trees predict Low / Medium / High risk'],
    ['chart', 'Route score', '0.6 × average + 0.4 × worst-10% risk'],
    ['shield-check', 'Recommend', 'Safest practical route, with the reasons'],
];

export default {
    layout: 'page',
    title: 'How the AI works',
    back: '/plan',
    mount(root) {
        const model = store.appInfo?.model;
        root.innerHTML = html`<div class="page">
            <header class="page-head"><div class="grow">
                <p class="eyebrow">Explainable by design</p>
                <h1 class="display">How SafeRoute AI decides</h1>
                <p>A route is only as safe as its weakest stretch, so SafeRoute scores the whole route piece by piece, using real street data and a machine-learning model, and tells you why.</p>
            </div></header>
            <section class="pipeline">${PIPELINE.map(([ic, title, text], i) => html`<div class="pipe-step" style="--i:${i}">
                <div class="p-icon">${icon(ic)}</div><h4>${title}</h4><p>${text}</p></div>`)}</section>
            <section class="stat-tiles" style="margin:1.4rem 0">
                <div class="stat"><div class="v num" data-n="${model?.trainingRows || 0}">0</div><div class="k">Training examples</div></div>
                <div class="stat"><div class="v num" data-n="${store.appInfo?.placesIndexed || 0}">0</div><div class="k">City places indexed</div></div>
                <div class="stat"><div class="v num" data-acc>–</div><div class="k">Cross-validated accuracy</div></div>
                <div class="stat"><div class="v num">8</div><div class="k">Features per stretch</div></div>
            </section>
            <div data-metrics><div class="card"><div class="row">${icon('sparkles')}<span>Evaluating the models with cross-validation… this takes about a minute the first time.</span></div>
                <div class="skeleton" style="height:220px;margin-top:1rem"></div></div></div>
            <section class="card" style="margin-top:1rem">
                <div class="card-title">${icon('info')} Honest limitations</div>
                <ul class="small" style="margin:.6rem 0 0;padding-left:1.1rem;display:grid;gap:.35rem">
                    <li>The model is trained on <strong>synthetic</strong> data built from documented safety-audit rules (lighting, openness, people, security, transit). It shows the method; it is not measured crime data.</li>
                    <li>OpenStreetMap coverage varies: where lamps are not mapped, lighting is estimated from shops and road activity.</li>
                    <li>Risk zones are illustrative examples; community reports start empty and grow with use.</li>
                    <li>Scores are guidance, never a guarantee. Always trust your instincts and call 112 in an emergency.</li>
                </ul>
            </section></div>`;
        root.querySelectorAll('[data-n]').forEach(n => countUp(n, Number(n.dataset.n)));

        let timer = null;
        let disposed = false;
        async function load() {
            try {
                const metrics = await api('/api/model/metrics');
                if (disposed) return;
                if (metrics.status !== 'ready') {
                    timer = setTimeout(load, 4000);
                    return;
                }
                render(metrics);
            } catch (error) {
                root.querySelector('[data-metrics]').innerHTML = html`<div class="alert-box tone-warn">${icon('alert')}<div>${error.message}</div></div>`.toString();
            }
        }

        function render(m) {
            lastMetrics = m;
            const forest = m.models.find(x => x.usedInApp) || m.models[0];
            const acc = root.querySelector('[data-acc]');
            countUp(acc, forest.accuracy * 100, { decimals: 1 });
            setTimeout(() => { acc.textContent += '%'; }, 950);
            const pct = v => `${(v * 100).toFixed(1)}%`;
            const importance = m.importance.map(f => ({ label: FEATURE_NAMES[f.feature] || f.feature, value: f.importance }));
            const maxImp = Math.max(...importance.map(f => f.value), 0.01);
            root.querySelector('[data-metrics]').innerHTML = html`
                <div class="grid grid-2">
                    <section class="card">
                        <div class="card-title">${icon('chart')} Model comparison</div>
                        <p class="card-sub">Accuracy with ${m.folds}-fold cross-validation on ${m.trainingRows.toLocaleString()} examples. The Random Forest is used in the app.</p>
                        ${barChart(m.models.map(x => ({ label: x.name, value: x.accuracy, note: `macro F1 ${x.macroF1.toFixed(3)} · kappa ${x.kappa.toFixed(3)}` })),
                            { max: 1, format: pct, labelWidth: 190, title: 'Accuracy by model' })}
                        ${dataTable(['Model', 'Accuracy', 'Macro F1', "Cohen's kappa"], m.models.map(x => [x.name, pct(x.accuracy), x.macroF1.toFixed(3), x.kappa.toFixed(3)]))}
                    </section>
                    <section class="card">
                        <div class="card-title">${icon('target')} What the model relies on</div>
                        <p class="card-sub">Permutation importance: how much accuracy drops when a feature is scrambled.</p>
                        ${barChart(importance, { max: maxImp * 1.1, format: v => `${(v * 100).toFixed(1)} pts`, labelWidth: 170, title: 'Feature importance' })}
                        ${dataTable(['Feature', 'Accuracy drop'], importance.map(f => [f.label, `${(f.value * 100).toFixed(1)} points`]))}
                    </section>
                </div>
                <div class="grid grid-2" style="margin-top:1rem">
                    <section class="card">
                        <div class="card-title">${icon('layers')} Where the Random Forest gets it right</div>
                        <p class="card-sub">Confusion matrix from cross-validation. The diagonal is correct predictions; darker means more examples.</p>
                        ${heatmap(m.confusion.labels, m.confusion.matrix, { title: 'Confusion matrix' })}
                        ${dataTable(['Actual \\ Predicted', ...m.confusion.labels], m.confusion.labels.map((l, i) => [l, ...m.confusion.matrix[i]]))}
                    </section>
                    <section class="card">
                        <div class="card-title">${icon('sparkles')} The training data</div>
                        <p class="card-sub">Generated by <span class="code">tools/TrainingDataGenerator.java</span>: realistic area types × hours, labelled by a documented risk function with noise.</p>
                        <div class="stack" style="margin-top:1rem">${Object.entries(m.classDistribution).map(([label, count]) => html`
                            <div><div class="row between small"><strong>${label}</strong><span class="num">${count.toLocaleString()} examples</span></div>
                            <div class="progress" style="margin:.35rem 0 0"><div style="width:${(100 * count / m.trainingRows).toFixed(1)}%"></div></div></div>`)}</div>
                        <p class="small muted" style="margin-top:1rem">Features: ${Object.values(FEATURE_NAMES).join(', ')}.</p>
                    </section>
                </div>`.toString();
            wireTooltips(root);
        }

        let lastMetrics = null;
        const onTheme = () => { if (lastMetrics) render(lastMetrics); };
        window.addEventListener('themechange', onTheme);
        load();
        return () => { disposed = true; clearTimeout(timer); window.removeEventListener('themechange', onTheme); };
    },
};
