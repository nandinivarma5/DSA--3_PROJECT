/* Dashboard rendering - plain JavaScript, no libraries needed */
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const COLORS = { positive: "#34d399", negative: "#f87171", neutral: "#94a3b8" };
const PALETTE = ["#2dd4bf", "#6366f1", "#f59e0b", "#ec4899", "#38bdf8"];
let current = null;
let filter = "all";

/* ---------- KPIs ---------- */
function renderKpis(d) {
  const s = d.sentiment, t = d.total || 1;
  const pct = (n) => Math.round((100 * n) / t) + "%";
  $("kpis").innerHTML = [
    ["", d.total, "Posts analysed"],
    ["pos", pct(s.positive), "Positive"],
    ["neg", pct(s.negative), "Negative"],
    ["neu", pct(s.neutral), "Neutral"],
    ["", (s.average > 0 ? "+" : "") + s.average.toFixed(2), "Avg. sentiment"],
  ].map(([c, v, l]) => `<div class="kpi ${c}"><div class="v">${v}</div><div class="l">${l}</div></div>`).join("");
}

/* ---------- Donut ---------- */
function renderDonut(d) {
  const s = d.sentiment, total = d.total || 1, r = 60, C = 2 * Math.PI * r;
  let offset = 0, segs = "";
  ["positive", "neutral", "negative"].forEach((k) => {
    const len = (s[k] / total) * C;
    segs += `<circle r="${r}" cx="80" cy="80" fill="none" stroke="${COLORS[k]}" stroke-width="22"
      stroke-dasharray="${len} ${C - len}" stroke-dashoffset="${-offset}" transform="rotate(-90 80 80)"/>`;
    offset += len;
  });
  $("donut").innerHTML = `
    <svg width="170" height="170" viewBox="0 0 160 160">${segs}
      <text x="80" y="78" text-anchor="middle" style="font-size:24px;font-weight:800;fill:#e8edf7">${d.total}</text>
      <text x="80" y="96" text-anchor="middle">posts</text></svg>
    <div class="donut-legend">${["positive", "neutral", "negative"].map((k) =>
      `<div><span class="dot" style="background:${COLORS[k]}"></span>${k[0].toUpperCase() + k.slice(1)} — <b>${s[k]}</b></div>`).join("")}</div>`;
}

/* ---------- Topics (stacked sentiment bars) ---------- */
function renderTopics(d) {
  const max = Math.max(...d.topics.map((t) => t.count), 1);
  $("topics").innerHTML = d.topics.map((t) => {
    const w = (n) => (100 * n) / t.count + "%";
    return `<div class="bar-row"><span>${esc(t.name)}</span>
      <div class="bar" style="width:${(100 * t.count) / max}%">
        <i style="width:${w(t.positive)};background:${COLORS.positive}"></i>
        <i style="width:${w(t.neutral)};background:${COLORS.neutral}"></i>
        <i style="width:${w(t.negative)};background:${COLORS.negative}"></i></div>
      <span class="n">${t.count}</span></div>`;
  }).join("") + `<p class="muted small">Bar colours: green = positive · grey = neutral · red = negative</p>`;
}

/* ---------- Line chart ---------- */
function renderLine(d) {
  const tl = d.timeline, names = Object.keys(tl.series);
  if (!d.has_dates || !tl.dates.length) {
    $("lineChart").innerHTML = `<p class="muted">Add dates to your lines (<code>YYYY-MM-DD | Source | text</code>) to see trends over time.</p>`;
    $("legend").innerHTML = ""; return;
  }
  const W = 760, H = 260, pl = 34, pr = 12, pt = 12, pb = 28, n = tl.dates.length;
  const maxY = Math.max(1, ...names.flatMap((k) => tl.series[k]));
  const x = (i) => (n === 1 ? W / 2 : pl + (i * (W - pl - pr)) / (n - 1));
  const y = (v) => pt + (1 - v / maxY) * (H - pt - pb);
  let g = "";
  for (let k = 0; k <= 4; k++) {
    const v = (maxY * k) / 4, yy = y(v);
    g += `<line x1="${pl}" x2="${W - pr}" y1="${yy}" y2="${yy}" stroke="#243253" stroke-dasharray="3 4"/>
          <text x="${pl - 6}" y="${yy + 4}" text-anchor="end">${Math.round(v)}</text>`;
  }
  const step = Math.ceil(n / 8);
  tl.dates.forEach((dt, i) => { if (i % step === 0) g += `<text x="${x(i)}" y="${H - 8}" text-anchor="middle">${dt.slice(5)}</text>`; });
  names.forEach((k, idx) => {
    const pts = tl.series[k].map((v, i) => `${x(i)},${y(v)}`).join(" ");
    g += `<polyline points="${pts}" fill="none" stroke="${PALETTE[idx % 5]}" stroke-width="2.5" stroke-linejoin="round"/>`;
    tl.series[k].forEach((v, i) => (g += `<circle cx="${x(i)}" cy="${y(v)}" r="3" fill="${PALETTE[idx % 5]}"><title>${esc(k)} · ${tl.dates[i]}: ${v}</title></circle>`));
  });
  $("lineChart").innerHTML = `<svg viewBox="0 0 ${W} ${H}" width="100%" role="img" aria-label="Topic volume over time">${g}</svg>`;
  $("legend").innerHTML = names.map((k, i) => `<span><i class="dot" style="background:${PALETTE[i % 5]}"></i>${esc(k)}</span>`).join("");
}

/* ---------- Keyword cloud ---------- */
function renderCloud(d) {
  const kws = d.keywords;
  if (!kws.length) { $("cloud").innerHTML = '<span class="muted">No keywords found.</span>'; return; }
  const min = Math.min(...kws.map((k) => k.count)), max = Math.max(...kws.map((k) => k.count));
  const shuffled = [...kws].sort((a, b) => a.term.localeCompare(b.term));
  $("cloud").innerHTML = shuffled.map((k) => {
    const size = 0.85 + (max === min ? 0.5 : (1.5 * (k.count - min)) / (max - min));
    const cls = k.sentiment >= 0.15 ? "positive" : k.sentiment <= -0.15 ? "negative" : "neutral";
    return `<span class="${cls}" style="font-size:${size.toFixed(2)}rem" title="${k.count} posts · sentiment ${k.sentiment}">${esc(k.term)}</span>`;
  }).join("");
}

/* ---------- Emerging + trending ---------- */
function renderTrends(d) {
  if (!d.has_dates) {
    $("emerging").innerHTML = `<p class="muted">Add dates to detect emerging discussions.</p>`;
    $("trending").innerHTML = ""; return;
  }
  $("emerging").innerHTML = d.emerging.length ? d.emerging.map((e) =>
    `<div class="em-row"><span><b>${esc(e.term)}</b> <span class="muted small">${e.earlier} → ${e.recent} posts</span></span>
     <span class="badge">×${e.growth}</span></div>`).join("")
    : `<p class="muted">No strongly emerging terms detected.</p>`;
  $("trending").innerHTML = d.trending.length ? d.trending.slice(0, 5).map((t) => {
    const up = t.change >= 0;
    return `<div class="em-row"><span>${esc(t.topic)} <span class="muted small">${t.share}% of recent posts</span></span>
      <span class="badge ${up ? "" : "down"}">${up ? "▲" : "▼"} ${Math.abs(t.change)} pts</span></div>`;
  }).join("") : "";
}

/* ---------- Feed ---------- */
function renderFeed(d) {
  const rows = d.items.filter((i) => filter === "all" || i.label === filter).slice(0, 120);
  $("feed").innerHTML = `<thead><tr><th>Date</th><th>Source</th><th>Text</th><th>Topic</th><th>Sentiment</th></tr></thead><tbody>` +
    (rows.map((i) => `<tr><td>${i.date || "—"}</td><td>${esc(i.source)}</td><td>${esc(i.text)}</td><td>${esc(i.topic)}</td>
      <td><span class="pill ${i.label}">${i.label} (${i.score > 0 ? "+" : ""}${i.score})</span></td></tr>`).join("") ||
      `<tr><td colspan="5" class="muted">No posts match this filter.</td></tr>`) + `</tbody>`;
}

function render(d, label) {
  current = d;
  $("datasetLabel").textContent = label;
  renderKpis(d); renderDonut(d); renderTopics(d); renderLine(d);
  renderCloud(d); renderTrends(d); renderFeed(d);
}

/* ---------- API helpers ---------- */
async function loadSample() {
  const res = await fetch("/api/sample");
  render(await res.json(), "Demo dataset · 14 days of simulated news & social posts");
}

async function runAnalysis() {
  const text = $("input").value;
  $("status").textContent = "Analyzing…";
  try {
    const res = await fetch("/api/analyze", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ text }) });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Request failed");
    render(data, `Your data · ${data.total} posts analysed`);
    $("status").textContent = "Done ✓";
    $("dashboard").scrollIntoView({ behavior: "smooth" });
  } catch (e) { $("status").textContent = "⚠ " + e.message; }
}

/* ---------- Events ---------- */
$("runBtn").onclick = runAnalysis;
$("reloadSample").onclick = loadSample;
$("clearBtn").onclick = () => { $("input").value = ""; $("status").textContent = ""; };
$("fillBtn").onclick = async () => {
  const r = await fetch("/api/sample-text"); $("input").value = (await r.json()).text;
  $("status").textContent = "Sample loaded — press Analyze.";
};
$("file").onchange = (e) => {
  const f = e.target.files[0]; if (!f) return;
  const rd = new FileReader();
  rd.onload = () => { $("input").value = rd.result; $("status").textContent = `Loaded ${f.name}`; };
  rd.readAsText(f);
};
$("filters").onclick = (e) => {
  if (e.target.tagName !== "BUTTON") return;
  filter = e.target.dataset.f;
  [...$("filters").children].forEach((b) => b.classList.toggle("on", b === e.target));
  if (current) renderFeed(current);
};

loadSample().catch(() => { $("datasetLabel").textContent = "⚠ Could not reach the server. Run Main.java (see README)"; });
