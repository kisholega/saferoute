// Small SVG charts for the "How it works" screen. Thin bars (<= 22px) with rounded data-ends, value at
// the tip, ink-coloured text, hover tooltips, and a data table for every chart.
import { escapeHtml, html, raw } from './ui.js';

const dark = () => document.documentElement.dataset.theme === 'dark';

let tip = null;
function showTip(event, text) {
    if (!tip) {
        tip = document.createElement('div');
        tip.className = 'chart-tip';
        document.body.append(tip);
    }
    tip.textContent = text;
    tip.style.left = `${event.clientX}px`;
    tip.style.top = `${event.clientY}px`;
    tip.style.display = 'block';
}
function hideTip() {
    if (tip) tip.style.display = 'none';
}

export function wireTooltips(root) {
    root.querySelectorAll('[data-tip]').forEach(node => {
        node.addEventListener('pointermove', e => showTip(e, node.dataset.tip));
        node.addEventListener('pointerleave', hideTip);
    });
}

/** Horizontal bars, one series. rows: [{label, value, note?}], value 0..max. */
export function barChart(rows, { max = 1, format = v => v.toFixed(2), labelWidth = 170, title = '' } = {}) {
    const rowH = 38;
    const barH = 18;
    const width = 560;
    const plotW = width - labelWidth - 74;
    const height = rows.length * rowH + 24;
    const ticks = [0, 0.25, 0.5, 0.75, 1].map(t => t * max);
    const bars = rows.map((row, i) => {
        const y = i * rowH + 8;
        const w = Math.max(2, plotW * Math.min(1, row.value / max));
        // rounded data-end only (square at the baseline)
        const r = Math.min(4, w / 2);
        const x0 = labelWidth;
        const path = `M${x0},${y} H${x0 + w - r} Q${x0 + w},${y} ${x0 + w},${y + r} V${y + barH - r} Q${x0 + w},${y + barH} ${x0 + w - r},${y + barH} H${x0} Z`;
        return `<g>
            <text x="${labelWidth - 12}" y="${y + barH / 2 + 4}" text-anchor="end" font-size="14" font-weight="600" class="t-ink">${escapeHtml(row.label)}</text>
            <path class="bar-rect" d="${path}" data-tip="${escapeHtml(`${row.label}: ${format(row.value)}${row.note ? ' · ' + row.note : ''}`)}">
                <animate attributeName="opacity" from="0" to="1" dur=".6s" begin="${i * 0.08}s" fill="freeze"/></path>
            <text x="${x0 + w + 8}" y="${y + barH / 2 + 4}" font-size="13.5" font-weight="700" class="t-ink" class="num">${escapeHtml(format(row.value))}</text>
        </g>`;
    }).join('');
    const grid = ticks.map(t => {
        const x = labelWidth + plotW * (t / max);
        return `<line x1="${x}" x2="${x}" y1="2" y2="${height - 18}" class="grid-line" stroke-width="1"/>
            <text x="${x}" y="${height - 4}" text-anchor="middle" font-size="11" class="t-muted">${escapeHtml(format(t))}</text>`;
    }).join('');
    return raw(`<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${escapeHtml(title)}">${grid}${bars}</svg>`);
}

/** Confusion-matrix heatmap with a single-hue sequential ramp (light = few, dark = many). */
export function heatmap(labels, matrix, { title = '' } = {}) {
    const n = labels.length;
    const cell = 92;
    const left = 120;
    const top = 40;
    const width = left + n * cell + 10;
    const height = top + n * cell + 34;
    const max = Math.max(...matrix.flat(), 1);
    // Sequential single-hue ramp, chosen separately for each theme: light = few, strong violet = many.
    const isDark = dark();
    const ramp = t => {
        const a = isDark ? [36, 27, 56] : [243, 238, 254];
        const b = isDark ? [196, 181, 253] : [76, 29, 149];
        return `rgb(${a.map((v, i) => Math.round(v + (b[i] - v) * t)).join(',')})`;
    };
    let cells = '';
    matrix.forEach((row, i) => row.forEach((value, j) => {
        const t = value / max;
        const x = left + j * cell;
        const y = top + i * cell;
        const textColor = isDark ? (t > 0.5 ? '#1a1426' : '#f2edfa') : (t > 0.45 ? '#ffffff' : '#1f1633');
        const rowTotal = row.reduce((s, v) => s + v, 0) || 1;
        cells += `<rect class="heat-cell" x="${x + 1}" y="${y + 1}" width="${cell - 2}" height="${cell - 2}" rx="8" fill="${ramp(t)}"
            data-tip="${escapeHtml(`Actual ${labels[i]} → predicted ${labels[j]}: ${value} (${Math.round(100 * value / rowTotal)}% of ${labels[i]})`)}"/>
            <text x="${x + cell / 2}" y="${y + cell / 2 + 5}" text-anchor="middle" font-size="16" font-weight="800" style="fill:${textColor}" pointer-events="none">${value}</text>`;
    }));
    const colLabels = labels.map((l, j) => `<text x="${left + j * cell + cell / 2}" y="${top - 12}" text-anchor="middle" font-size="12" font-weight="700" class="t-ink">${escapeHtml(l)}</text>`).join('');
    const rowLabels = labels.map((l, i) => `<text x="${left - 12}" y="${top + i * cell + cell / 2 + 4}" text-anchor="end" font-size="12" font-weight="700" class="t-ink">${escapeHtml(l)}</text>`).join('');
    return raw(`<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${escapeHtml(title)}">
        ${colLabels}${rowLabels}${cells}
        <text x="${left + (n * cell) / 2}" y="${height - 6}" text-anchor="middle" font-size="11.5" class="t-muted">Predicted class</text>
        <text x="14" y="${top + (n * cell) / 2}" text-anchor="middle" font-size="11.5" class="t-muted" transform="rotate(-90 14 ${top + (n * cell) / 2})">Actual class</text>
    </svg>`);
}

export function dataTable(headers, rows) {
    return html`<details class="data-table"><summary>Show data table</summary><table>
        <thead><tr>${headers.map(h => html`<th>${h}</th>`)}</tr></thead>
        <tbody>${rows.map(r => html`<tr>${r.map(c => html`<td>${c}</td>`)}</tr>`)}</tbody></table></details>`;
}
