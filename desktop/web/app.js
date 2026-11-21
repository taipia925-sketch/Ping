'use strict';
const IC_PLAY = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 4.5v15l13-7.5z"/></svg>';
const IC_STOP = '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>';
// PingPro per PC – interfaccia. I dati arrivano dal motore locale (127.0.0.1) in tempo reale.

const TOKEN = document.querySelector('meta[name="pp-token"]').content;
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

async function api(path, opts = {}) {
  const r = await fetch(path, { ...opts, headers: { 'X-PingPro-Token': TOKEN, 'Content-Type': 'application/json', ...(opts.headers || {}) } });
  const ct = r.headers.get('content-type') || '';
  if (!r.ok) {
    let m = 'Errore';
    try { m = (await r.json()).error || m; } catch (_) { /* testo */ }
    throw new Error(m);
  }
  return ct.includes('json') ? r.json() : r.text();
}
const post = (p, body) => api(p, { method: 'POST', body: JSON.stringify(body ?? {}) });

// ---------- Formati ----------
const it = (s) => s.replace('.', ',');
function ms(v) { if (v == null) return '—'; return it(v < 1 ? v.toFixed(2) : v < 10 ? v.toFixed(1) : v.toFixed(0)); }
function pct(v) { return v === 0 || v === 100 ? v.toFixed(0) + '%' : it(v.toFixed(1)) + '%'; }
function hhmmss(t) { return new Date(t).toLocaleTimeString('it-IT'); }
function dt(t) { return new Date(t).toLocaleString('it-IT', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }); }
function clock(msv) { const s = Math.floor(msv / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60; const p = (n) => String(n).padStart(2, '0'); return h ? `${h}:${p(m)}:${p(x)}` : `${p(m)}:${p(x)}`; }
function dur(msv) { const s = Math.floor(msv / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60); return h ? `${h} h ${m} min` : m ? `${m} min ${s % 60} s` : `${s} s`; }
function latClass(v) { return v == null ? 'bad' : v < 20 ? 'ok' : v < 50 ? 'ok' : v < 100 ? 'warn' : 'warn'; }

function toast(msg) { const t = $('#toast'); t.textContent = msg; t.classList.add('on'); clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.remove('on'), 2400); }
async function copy(text, msg) { try { await navigator.clipboard.writeText(text); toast(msg || 'Copiato negli appunti'); } catch (_) { toast('Impossibile copiare'); } }
function download(name, text, type) { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], { type: type || 'text/plain;charset=utf-8' })); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 2000); }

// ---------- IPv4 e intervalli (come nell'app Android) ----------
function isIPv4(s) { const p = String(s).trim().split('.'); return p.length === 4 && p.every((x) => /^\d{1,3}$/.test(x) && +x <= 255); }
function ipNum(s) { return s.split('.').reduce((a, x) => a * 256 + +x, 0); }
function numIp(n) { return [n >>> 24 & 255, n >>> 16 & 255, n >>> 8 & 255, n & 255].join('.'); }
function validTarget(s) { s = String(s).trim(); if (!s || /\s/.test(s)) return false; if (isIPv4(s)) return true; if (/^[\d.]+$/.test(s)) return false; return /^[A-Za-z0-9._-]+$/.test(s); }
function parseTargets(text, max = 1000) {
  const out = []; const bad = []; const seen = new Set();
  for (const tok of String(text).split(/[\s,;]+/).filter(Boolean)) {
    const m = tok.match(/^(\d+\.\d+\.\d+\.\d+)-(\d+(?:\.\d+\.\d+\.\d+)?)$/);
    let list = null;
    if (m) {
      const a = m[1]; const b = m[2].includes('.') ? m[2] : a.slice(0, a.lastIndexOf('.') + 1) + m[2];
      if (isIPv4(a) && isIPv4(b) && ipNum(b) >= ipNum(a) && ipNum(b) - ipNum(a) < max) { list = []; for (let n = ipNum(a); n <= ipNum(b); n++) list.push(numIp(n)); }
    } else if (validTarget(tok)) list = [tok];
    if (!list) { bad.push(tok); continue; }
    for (const x of list) if (!seen.has(x.toLowerCase())) { seen.add(x.toLowerCase()); out.push(x); }
  }
  return { addresses: out.slice(0, max), invalid: bad };
}

// ---------- Stato e navigazione ----------
let S = null;          // stato dal motore
let page = 'test';
let devices = [];
let history = [];
let net = null;
const ui = { target: localStorage.getItem('pp.target') || '', continuous: true, count: 4, size: 32, timeoutMs: 4000, ttl: 128, multiContinuous: true, scanSel: new Set(), showAllIpconfig: false, promptView: false };

function nav(p) {
  page = p;
  $$('.nav').forEach((b) => b.classList.toggle('on', b.dataset.page === p));
  ({ test: pageTest, multi: pageMulti, net: pageNet, scan: pageScan, devices: pageDevices, history: pageHistory, guide: pageGuide })[p]();
  $('.main').scrollTop = 0;
}
$$('.nav').forEach((b) => b.addEventListener('click', () => nav(b.dataset.page)));

async function loadDevices() { devices = (await api('/api/devices')) || []; }
async function loadHistory() { history = (await api('/api/history')) || []; }
async function loadNet() { net = await api('/api/ipconfig'); }
const mainLan = () => (net?.adapters || []).find((a) => a.up && a.ipv4 && a.gateway) || (net?.adapters || []).find((a) => a.up && a.ipv4);
const devName = (addr) => devices.find((d) => d.address === addr)?.name || '';

// ---------- Allarmi ----------
let audio;
function beep(down) {
  try {
    audio = audio || new AudioContext();
    const o = audio.createOscillator(), g = audio.createGain();
    o.frequency.value = down ? 660 : 990; o.connect(g); g.connect(audio.destination);
    g.gain.setValueAtTime(0.0001, audio.currentTime); g.gain.exponentialRampToValueAtTime(0.25, audio.currentTime + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + (down ? 0.6 : 0.2));
    o.start(); o.stop(audio.currentTime + (down ? 0.6 : 0.2));
  } catch (_) { /* audio non disponibile */ }
}
function checkAlerts(prev, cur) {
  const sd = (x) => x?.single?.running && x.single.status === 'down';
  if (!sd(prev) && sd(cur)) { beep(true); toast('La scheda non risponde'); }
  if (sd(prev) && cur?.single?.status !== 'down' && cur?.single?.running) beep(false);
  const md = (x) => (x?.multi?.targets || []).filter((t) => t.running && t.status === 'down').length;
  if (cur?.multi?.running && md(cur) > md(prev)) { beep(true); toast('Una scheda non risponde'); }
  const downNow = sd(cur) || md(cur) > 0;
  document.title = downNow ? '⚠ NON RISPONDE – PingPro' : 'PingPro';
}

// ---------- Componenti ----------
function statusInfo(s) {
  if (!s) return { t: '', c: 'idle', sub: '' };
  const st = s.stats;
  switch (s.status) {
    case 'starting': return { t: 'AVVIO…', c: 'idle', sub: 'Preparazione del test…' };
    case 'online': return { t: 'ONLINE', c: 'ok', sub: 'La scheda risponde regolarmente' };
    case 'slow': return { t: 'LENTA', c: 'warn', sub: 'Risponde, ma con tempi elevati' };
    case 'unstable': return s.consecutiveLost > 0 ? { t: 'PACCHETTO PERSO', c: 'warn', sub: "Nessuna risposta all'ultima richiesta" } : { t: 'INSTABILE', c: 'warn', sub: 'Perdite di pacchetti negli ultimi secondi' };
    case 'down': return { t: 'NON RISPONDE', c: 'bad', sub: `${s.consecutiveLost} richieste consecutive senza risposta` };
    case 'error': return { t: 'ERRORE', c: 'bad', sub: s.error };
  }
  if (st.sent === 0) return { t: 'TEST INTERROTTO', c: 'idle', sub: 'Nessuna richiesta completata' };
  if (st.received === 0) return { t: 'NESSUNA RISPOSTA', c: 'bad', sub: `0 risposte su ${st.sent} richieste` };
  if (st.lost > 0) return { t: 'COMPLETATO CON PERDITE', c: 'warn', sub: `${st.received} risposte su ${st.sent} richieste` };
  return { t: 'COMPLETATO · OK', c: 'ok', sub: `${st.received} risposte su ${st.sent} richieste` };
}
function strip(samples, n = 40) {
  const v = samples.slice(-n); const pad = n - v.length;
  let h = `<div class="strip" style="grid-template-columns:repeat(${n},minmax(0,1fr))">`;
  for (let i = 0; i < pad; i++) h += '<span></span>';
  for (const s of v) h += `<span style="background:var(--${s.status !== 'ok' ? 'red' : s.rtt < 20 ? 'green' : s.rtt < 50 ? 'teal' : 'amber'})"></span>`;
  return h + '</div>';
}
function consoleLines(s) {
  const ip = s.ip || s.target;
  let out = `Esecuzione di Ping ${esc(s.target !== ip ? s.target + ' [' + ip + ']' : ip)} con 32 byte di dati:\n`;
  for (const p of s.samples) {
    if (p.status === 'ok') out += `Risposta da ${esc(ip)}: byte=${p.bytes} ${p.rtt < 1 ? 'durata<1ms' : 'durata=' + Math.round(p.rtt) + 'ms'}${p.ttl ? ' TTL=' + p.ttl : ''}\n`;
    else out += `<span class="l">${esc(p.msg || 'Richiesta scaduta.')}</span>\n`;
  }
  return out;
}
function drawChart(canvas, samples, avg) {
  const dpr = window.devicePixelRatio || 1; const w = canvas.clientWidth, h = canvas.clientHeight;
  canvas.width = w * dpr; canvas.height = h * dpr; const c = canvas.getContext('2d'); c.scale(dpr, dpr);
  const css = getComputedStyle(document.documentElement); const col = (n) => css.getPropertyValue(n).trim();
  const left = 40, bottom = 6, W = w - left, H = h - bottom;
  const vals = samples.filter((s) => s.rtt != null).map((s) => s.rtt);
  const maxV = Math.max(1, ...vals) * 1.15; const nice = [1, 2, 2.5, 5, 10]; const e = Math.pow(10, Math.floor(Math.log10(maxV)));
  const top = nice.map((x) => x * e).find((x) => x >= maxV) || 10 * e;
  const n = Math.max(2, samples.length); const x = (i) => left + (n <= 1 ? W / 2 : i * W / (n - 1)); const y = (v) => H - Math.min(1, v / top) * H;
  c.font = '11px PlexMono'; c.fillStyle = col('--faint'); c.strokeStyle = 'rgba(0,0,0,0.06)'; c.lineWidth = 1;
  for (let k = 0; k <= 4; k++) { const v = top * k / 4, yy = y(v); c.beginPath(); c.moveTo(left, yy); c.lineTo(w, yy); c.stroke(); c.fillText(ms(v), 0, Math.max(10, yy + 4)); }
  const bw = Math.max(2, W / n * 0.6);
  samples.forEach((s, i) => { if (s.status !== 'ok') { c.fillStyle = 'rgba(201,48,44,0.85)'; c.fillRect(x(i) - bw / 2, 0, bw, H); } });
  if (avg != null) { c.setLineDash([6, 5]); c.strokeStyle = col('--teal'); c.beginPath(); c.moveTo(left, y(avg)); c.lineTo(w, y(avg)); c.stroke(); c.setLineDash([]); }
  c.strokeStyle = col('--accent'); c.lineWidth = 2.4; c.lineJoin = 'round'; c.beginPath(); let on = false;
  samples.forEach((s, i) => { if (s.rtt == null) { on = false; return; } if (!on) { c.moveTo(x(i), y(s.rtt)); on = true; } else c.lineTo(x(i), y(s.rtt)); });
  c.stroke();
  if (samples.length <= 40) samples.forEach((s, i) => { if (s.rtt != null) { c.fillStyle = col('--accent'); c.beginPath(); c.arc(x(i), y(s.rtt), 3, 0, 7); c.fill(); } });
}
function eventsHtml(evs) {
  const colr = { start: 'accent', stop: 'accent', info: 'amber', loss: 'amber', outage_start: 'red', error: 'red', outage_end: 'green', spike: 'violet' };
  return '<div class="events">' + [...evs].reverse().slice(0, 60).map((e) => `<div class="ev"><i style="background:var(--${colr[e.kind] || 'dim'})"></i><div><b>${esc(e.title)}</b>${e.detail ? `<span>${esc(e.detail)}</span>` : ''}</div><div class="faint small">${hhmmss(e.t)}</div></div>`).join('') + '</div>';
}

// ---------- TEST ----------
function pageTest() {
  $('#page').innerHTML = `
  <div class="head"><div class="grow"><h1>Test</h1><p>Come <b>ping</b> e <b>ping -t</b> del Prompt dei comandi, con grafici e statistiche</p></div></div>
  <div class="cols">
    <div class="stack">
      <div class="card">
        <h2>Indirizzo della scheda <span class="r"><a href="#" id="toScan">Cerca dispositivi</a> · <a href="#" id="toNet">ipconfig</a></span></h2>
        <div class="field" id="tField"><input id="tTarget" placeholder="192.168.1.100" spellcheck="false" autocomplete="off"><button class="x" id="tSave" title="Salva come scheda">☆ Salva</button></div>
        <div class="small" id="tHint" style="margin-top:8px"></div>
        <div class="chips" id="tChips"></div>
        <label class="lbl">Modalità</label>
        <div class="seg" id="tMode"><button data-v="0">Singolo</button><button data-v="1">Continuo · -t</button></div>
        <div id="tCountRow"><label class="lbl">Numero di richieste (-n)</label><input class="input" id="tCount" type="number" min="1" max="100000"></div>
        <details style="margin-top:12px"><summary class="muted small" style="cursor:pointer">Opzioni avanzate (come in Windows)</summary>
          <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">
            <div><label class="lbl">Byte (-l)</label><input class="input" id="tSize" type="number" min="1" max="65500"></div>
            <div><label class="lbl">Timeout ms (-w)</label><input class="input" id="tTimeout" type="number" min="100" max="60000"></div>
            <div><label class="lbl">TTL (-i)</label><input class="input" id="tTtl" type="number" min="1" max="255"></div>
          </div></details>
        <div class="cmd" id="tCmd"></div>
        <div style="margin-top:14px"><button class="btn big primary" id="tGo"></button></div>
      </div>
      <div class="card" id="tEventsCard" style="display:none"><h2>Cronologia eventi</h2><div id="tEvents"></div></div>
    </div>
    <div class="stack" id="tRight"></div>
  </div>`;
  const t = $('#tTarget'); t.value = ui.target;
  $('#tCount').value = ui.count; $('#tSize').value = ui.size; $('#tTimeout').value = ui.timeoutMs; $('#tTtl').value = ui.ttl;
  const sync = () => { ui.target = t.value.trim(); ui.count = +$('#tCount').value || 4; ui.size = +$('#tSize').value || 32; ui.timeoutMs = +$('#tTimeout').value || 4000; ui.ttl = +$('#tTtl').value || 128; localStorage.setItem('pp.target', ui.target); testForm(); };
  ['#tTarget', '#tCount', '#tSize', '#tTimeout', '#tTtl'].forEach((s) => $(s).addEventListener('input', sync));
  t.addEventListener('keydown', (e) => { if (e.key === 'Enter') startStop(); });
  $$('#tMode button').forEach((b) => b.addEventListener('click', () => { ui.continuous = b.dataset.v === '1'; testForm(); }));
  $('#tGo').addEventListener('click', startStop);
  $('#tSave').addEventListener('click', () => { if (validTarget(ui.target)) editDevice({ address: ui.target, name: devName(ui.target) }); });
  $('#toScan').addEventListener('click', (e) => { e.preventDefault(); nav('scan'); });
  $('#toNet').addEventListener('click', (e) => { e.preventDefault(); nav('net'); });
  testForm(); renderTest();
  Promise.all([loadDevices(), loadHistory(), loadNet()]).then(() => { if (!ui.target && mainLan()?.gateway) { ui.target = mainLan().gateway; t.value = ui.target; } testForm(); }).catch(() => {});
}
function testForm() {
  if (page !== 'test') return;
  const running = S?.single?.running;
  const valid = validTarget(ui.target);
  $('#tField').classList.toggle('bad', !!ui.target && !valid);
  const name = devName(ui.target);
  $('#tHint').innerHTML = ui.target && !valid ? '<span class="bad">Indirizzo non valido (esempio: 192.168.1.100)</span>' : name ? `<span class="ok">● ${esc(name)}</span>` : '';
  $('#tTarget').disabled = !!running;
  $$('#tMode button').forEach((b) => { b.classList.toggle('on', (b.dataset.v === '1') === ui.continuous); b.disabled = !!running; });
  $('#tCountRow').style.display = ui.continuous ? 'none' : '';
  const gw = mainLan()?.gateway;
  const recent = [...new Set(history.map((h) => h.target))].filter((x) => !devices.some((d) => d.address === x) && x !== gw).slice(0, 5);
  $('#tChips').innerHTML = (gw ? `<button class="chip gw" data-a="${esc(gw)}">Gateway ${esc(gw)}</button>` : '') +
    devices.map((d) => `<button class="chip ${d.address === ui.target ? 'sel' : ''}" data-a="${esc(d.address)}">★ ${esc(d.name)}</button>`).join('') +
    recent.map((r) => `<button class="chip ${r === ui.target ? 'sel' : ''}" data-a="${esc(r)}">${esc(r)}</button>`).join('');
  $$('#tChips .chip').forEach((c) => c.addEventListener('click', () => { if (S?.single?.running) return; ui.target = c.dataset.a; $('#tTarget').value = ui.target; localStorage.setItem('pp.target', ui.target); testForm(); }));
  let cmd = 'ping' + (ui.continuous ? ' -t' : ui.count !== 4 ? ` -n ${ui.count}` : '') + (ui.size !== 32 ? ` -l ${ui.size}` : '') + (ui.timeoutMs !== 4000 ? ` -w ${ui.timeoutMs}` : '') + (ui.ttl !== 128 ? ` -i ${ui.ttl}` : '');
  $('#tCmd').textContent = 'C:\\> ' + cmd + ' ' + (ui.target || '<indirizzo>');
  const busyOther = S && S.busy && !running;
  const g = $('#tGo');
  g.className = 'btn big ' + (running ? 'danger' : 'primary');
  g.innerHTML = running ? IC_STOP + 'Interrompi test' : IC_PLAY + 'Avvia test';
  g.disabled = !running && (!valid || busyOther);
  g.title = busyOther ? "C'è già un altro test in corso" : '';
}
async function startStop() {
  try {
    if (S?.single?.running) await post('/api/test/stop');
    else if (validTarget(ui.target)) await post('/api/test/start', { target: ui.target, continuous: ui.continuous, count: ui.count, size: ui.size, timeoutMs: ui.timeoutMs, ttl: ui.ttl });
  } catch (e) { toast(e.message); }
}
function renderTest() {
  if (page !== 'test') return;
  testForm();
  const s = S?.single; const right = $('#tRight');
  if (!s) {
    right.innerHTML = `<div class="card"><h2>Come funziona</h2>
      <p class="muted">1. Collega il PC alla rete della scheda (cavo o Wi-Fi).<br>2. Scrivi l'indirizzo IP della scheda, oppure tocca <b>Gateway</b> o una scheda salvata.<br>3. Scegli Singolo (come <b>ping</b>) o Continuo (come <b>ping -t</b>).<br>4. Premi <b>Avvia test</b>: stato, grafico e statistiche si aggiornano in tempo reale.</p>
      <p class="faint small">PingPro usa la stessa funzione di Windows usata da ping.exe: stessi pacchetti (32 byte), stesso timeout (4 s), circa una richiesta al secondo.</p></div>`;
    $('#tEventsCard').style.display = 'none';
    return;
  }
  const si = statusInfo(s), st = s.stats, last = s.samples[s.samples.length - 1];
  const shown = s.running ? st.last : st.avg;
  const end = s.endedAt || Date.now();
  const since = s.status === 'down' ? s.samples[s.samples.length - s.consecutiveLost]?.t : null;
  right.innerHTML = `
    <div class="card hero ${s.status === 'down' ? 'down' : ''}">
      <div class="row"><span class="pill ${si.c}"><i></i>${si.t}</span><span class="grow"></span>
        <b class="muted">${clock(end - s.startedAt)} · ${s.continuous ? 'continuo' : `${st.sent} di ${s.count}`}</b></div>
      <div class="muted small" style="margin-top:14px;font-weight:700">${esc((s.name ? s.name + ' · ' : '') + (s.ip || s.target))} · ${s.running ? 'ultima risposta' : 'latenza media'}</div>
      <div><span class="big">${shown != null ? ms(shown) : last && last.status !== 'ok' ? '—' : '···'}</span>${shown != null ? '<span class="unit">ms</span>' : ''}</div>
      <div class="muted" style="font-size:15px;margin-top:6px">${esc(si.sub)}${s.running && st.avg != null && !(last && last.status !== 'ok') ? ' · media ' + ms(st.avg) + ' ms' : ''}${s.running && last && last.status !== 'ok' ? ' · ' + esc((last.msg || '').toLowerCase()) : ''}</div>
      ${strip(s.samples)}
      ${s.network ? `<div class="small" style="margin-top:12px"><span class="pill ${s.sameNet ? 'ok' : 'warn'}" style="font-size:12px">${esc(s.network)}</span></div>` : ''}
      ${since ? `<div class="banner bad">⚠ La scheda non risponde da ${dur(Date.now() - since)} (dalle ${hhmmss(since)})</div>` : ''}
    </div>
    <div class="tiles">
      <div class="tile"><span>Ricevuti</span><b>${st.received}<small class="muted" style="font-size:13px"> /${st.sent}</small></b></div>
      <div class="tile"><span>Persi</span><b class="${st.lost ? 'bad' : ''}">${st.lost}</b></div>
      <div class="tile"><span>Perdita</span><b class="${st.sent === 0 ? '' : st.lossPct === 0 ? 'ok' : st.lossPct < 5 ? 'warn' : 'bad'}">${pct(st.lossPct)}</b></div>
      <div class="tile"><span>Jitter</span><b>${ms(st.jitter)}<small class="muted" style="font-size:13px"> ms</small></b></div>
      <div class="tile"><span>Media</span><b>${ms(st.avg)}<small class="muted" style="font-size:13px"> ms</small></b></div>
      <div class="tile"><span>Minima</span><b>${ms(st.min)}<small class="muted" style="font-size:13px"> ms</small></b></div>
      <div class="tile"><span>Massima</span><b>${ms(st.max)}<small class="muted" style="font-size:13px"> ms</small></b></div>
      <div class="tile"><span>Interruzioni</span><b class="${s.outages ? 'bad' : ''}">${s.outages}</b></div>
    </div>
    <div class="card"><h2>Andamento <span class="r">ultimi ${Math.min(s.samples.length, 120)} pacchetti</span></h2><canvas class="chart" id="tChart"></canvas>
      <div class="legend"><span><i style="background:var(--accent)"></i>Latenza</span><span><i style="background:var(--teal)"></i>Media</span><span><i style="background:var(--red)"></i>Perso</span></div></div>
    <div class="card"><h2>Come nel Prompt <span class="r">${esc(s.command)}</span></h2><div class="console" id="tConsole">${consoleLines(s)}</div></div>
    ${!s.running && s.recordId ? `<div class="card"><h2>Risultati</h2><div class="row">
      <button class="btn" id="tCopy">Copia report</button><button class="btn ghost" id="tTxt">Salva .txt</button><button class="btn ghost" id="tCsv">Salva .csv (Excel)</button><button class="btn ghost" id="tAgain">Ripeti</button></div></div>` : ''}`;
  drawChart($('#tChart'), s.samples.slice(-120), st.avg);
  const con = $('#tConsole'); con.scrollTop = con.scrollHeight;
  $('#tEventsCard').style.display = ''; $('#tEvents').innerHTML = eventsHtml(s.events || []);
  if (!s.running && s.recordId) {
    $('#tCopy').onclick = async () => copy(await api(`/api/history/${s.recordId}/export`), 'Report copiato negli appunti');
    $('#tTxt').onclick = async () => download(`ping_${s.ip}.txt`, await api(`/api/history/${s.recordId}/export`));
    $('#tCsv').onclick = async () => download(`ping_${s.ip}.csv`, await api(`/api/history/${s.recordId}/export?format=csv`), 'text/csv;charset=utf-8');
    $('#tAgain').onclick = startStop;
  }
}

// ---------- PIÙ SCHEDE ----------
let multiList = [];
async function pageMulti() {
  $('#page').innerHTML = `
  <div class="head"><div class="grow"><h1>Più schede</h1><p>Un ping indipendente per ogni scheda, come tante finestre <b>ping -t</b> aperte insieme (fino a 100)</p></div></div>
  <div class="cols">
    <div class="stack">
      <div class="card"><h2>Schede da controllare <span class="r" id="mCount"></span></h2>
        <label class="lbl" style="margin-top:0">Scrivi o incolla indirizzi (anche intervalli: 192.168.1.10-20)</label>
        <textarea class="input" id="mText" placeholder="192.168.1.100&#10;192.168.1.101&#10;192.168.1.110-115"></textarea>
        <div class="row" style="margin-top:8px"><span class="small muted grow" id="mPrev"></span><button class="btn" id="mAdd">Aggiungi</button></div>
        <div id="mDevs"></div>
        <div id="mList" style="margin-top:10px"></div>
        <div class="row" style="margin-top:10px"><button class="btn ghost" id="mClear">Svuota elenco</button><span class="grow"></span><a href="#" id="mScan">Cerca dispositivi</a></div>
        <label class="lbl">Modalità</label>
        <div class="seg" id="mMode"><button data-v="0">Singolo · 4</button><button data-v="1">Continuo · -t</button></div>
        <div style="margin-top:14px"><button class="btn big primary" id="mGo"></button></div>
      </div>
    </div>
    <div class="stack" id="mRight"></div>
  </div>`;
  await Promise.all([loadDevices(), api('/api/multi/list').then((l) => { multiList = l || []; })]).catch(() => {});
  $('#mText').addEventListener('input', multiPreview);
  $('#mAdd').onclick = () => { const p = parseTargets($('#mText').value); addToMulti(p.addresses.map((a) => ({ address: a, name: devName(a) }))); $('#mText').value = ''; multiPreview(); };
  $('#mClear').onclick = () => saveMulti([]);
  $('#mScan').onclick = (e) => { e.preventDefault(); nav('scan'); };
  $$('#mMode button').forEach((b) => b.addEventListener('click', () => { ui.multiContinuous = b.dataset.v === '1'; multiForm(); }));
  $('#mGo').onclick = async () => {
    try {
      if (S?.multi?.running) await post('/api/multi/stop');
      else await post('/api/multi/start', { targets: multiList, name: '', config: { continuous: ui.multiContinuous } });
    } catch (e) { toast(e.message); }
  };
  multiForm(); multiPreview(); renderMulti();
}
function multiPreview() {
  const p = parseTargets($('#mText').value);
  const fresh = p.addresses.filter((a) => !multiList.some((t) => t.address === a));
  $('#mPrev').innerHTML = !$('#mText').value.trim() ? '' : (fresh.length ? `${fresh.length} da aggiungere` : 'Nessun indirizzo nuovo') + (p.invalid.length ? ` · <span class="bad">non validi: ${esc(p.invalid.slice(0, 3).join(', '))}</span>` : '');
}
async function saveMulti(list) { multiList = list.slice(0, 100); try { await api('/api/multi/list', { method: 'PUT', body: JSON.stringify(multiList) }); } catch (_) {} multiForm(); }
function addToMulti(items) {
  const before = multiList.length;
  const merged = [...multiList]; for (const it of items) if (!merged.some((t) => t.address === it.address)) merged.push(it);
  if (merged.length > 100) toast('Massimo 100 schede: aggiunte le prime');
  saveMulti(merged); toast(`Aggiunti ${Math.min(merged.length, 100) - before} indirizzi`);
}
function multiForm() {
  if (page !== 'multi') return;
  const running = S?.multi?.running;
  $('#mCount').textContent = `${multiList.length} / 100`;
  $('#mDevs').innerHTML = devices.length ? '<label class="lbl">Schede salvate</label>' + devices.map((d) => `<label class="row small" style="padding:4px 0;cursor:pointer"><input type="checkbox" data-a="${esc(d.address)}" ${multiList.some((t) => t.address === d.address) ? 'checked' : ''} ${running ? 'disabled' : ''}> <b>${esc(d.name)}</b> <span class="muted">${esc(d.address)}</span></label>`).join('') : '';
  $$('#mDevs input').forEach((c) => c.addEventListener('change', () => { const a = c.dataset.a; if (c.checked) addToMulti([{ address: a, name: devName(a) }]); else saveMulti(multiList.filter((t) => t.address !== a)); }));
  $('#mList').innerHTML = multiList.length ? `<table class="t"><tbody>${multiList.map((t, i) => `<tr><td>${esc(t.name || t.address)}${t.name ? `<div class="faint small">${esc(t.address)}</div>` : ''}</td><td class="num"><button class="x" data-i="${i}" ${running ? 'disabled' : ''}>✕</button></td></tr>`).join('')}</tbody></table>` : '<div class="faint small">Elenco vuoto: aggiungi indirizzi qui sopra o da «Cerca dispositivi».</div>';
  $$('#mList .x').forEach((b) => b.addEventListener('click', () => saveMulti(multiList.filter((_, i) => i !== +b.dataset.i))));
  $$('#mMode button').forEach((b) => { b.classList.toggle('on', (b.dataset.v === '1') === ui.multiContinuous); b.disabled = !!running; });
  const g = $('#mGo'); g.className = 'btn big ' + (running ? 'danger' : 'primary'); g.innerHTML = running ? IC_STOP + 'Interrompi test' : `${IC_PLAY}Avvia test (${multiList.length})`;
  g.disabled = !running && (!multiList.length || (S && S.busy));
}
function health(t) {
  if (t.error) return 'bad';
  if (t.stats.sent === 0) return 'idle';
  if (t.finished || !t.running) return t.stats.received === 0 ? 'bad' : t.stats.lost > 0 ? 'warn' : 'ok';
  return t.status === 'down' ? 'bad' : t.status === 'unstable' || t.status === 'slow' ? 'warn' : 'ok';
}
function renderMulti() {
  if (page !== 'multi') return;
  multiForm();
  const m = S?.multi; const right = $('#mRight');
  if (!m || !m.targets?.length) { right.innerHTML = '<div class="card empty">Componi l\'elenco e premi <b>Avvia test</b>. Qui vedrai lo stato di ogni scheda in tempo reale.</div>'; return; }
  const H = m.targets.map(health); const ok = H.filter((h) => h === 'ok').length, warn = H.filter((h) => h === 'warn').length, bad = H.filter((h) => h === 'bad').length;
  const tot = m.targets.length; const down = m.targets.filter((_, i) => H[i] === 'bad');
  const C = 2 * Math.PI * 46; const seg = (n, off, color) => n ? `<circle cx="55" cy="55" r="46" fill="none" stroke="${color}" stroke-width="12" stroke-dasharray="${C * n / tot} ${C}" stroke-dashoffset="${-C * off / tot}" transform="rotate(-90 55 55)"/>` : '';
  const order = { bad: 0, warn: 1, idle: 2, ok: 3 };
  const rows = m.targets.map((t, i) => ({ t, h: H[i] })).sort((a, b) => order[a.h] - order[b.h]);
  right.innerHTML = `
    <div class="card"><div class="row" style="gap:22px">
      <svg class="ring" viewBox="0 0 110 110"><circle cx="55" cy="55" r="46" fill="none" stroke="var(--surface3)" stroke-width="12"/>${seg(ok, 0, 'var(--green)')}${seg(warn, ok, 'var(--amber)')}${seg(bad, ok + warn, 'var(--red)')}<text x="55" y="62" text-anchor="middle" font-size="22" font-weight="800" fill="var(--text)">${ok}/${tot}</text></svg>
      <div class="grow"><div style="font-size:20px;font-weight:800">${esc(m.name)}</div><div class="muted">${m.running ? 'In corso' : 'Completato'} · ${clock((m.endedAt || Date.now()) - m.startedAt)}</div>
        <div style="margin-top:8px;font-weight:700"><span class="ok">● ${ok} ${ok === 1 ? 'risponde' : 'rispondono'}</span> &nbsp; <span class="warn">● ${warn} ${warn === 1 ? 'instabile' : 'instabili'}</span> &nbsp; <span class="bad">● ${bad} ${bad === 1 ? 'non risponde' : 'non rispondono'}</span></div></div>
      ${!m.running ? '<div class="row"><button class="btn" id="mCopy">Copia riepilogo</button><button class="btn ghost" id="mTxt">Salva .txt</button></div>' : ''}</div>
      ${down.length ? `<div class="banner bad">⚠ ${down.length === 1 ? 'Non risponde' : 'Non rispondono'}: ${esc(down.map((t) => t.name || t.target).join(', '))}</div>` : ''}
    </div>
    <div class="card" style="padding:8px 12px"><table class="t"><thead><tr><th>Scheda</th><th>Ultimi pacchetti</th><th class="num">Ultimo</th><th class="num">Media</th><th class="num">Ricevuti</th><th class="num">Perdita</th></tr></thead><tbody>
      ${rows.map(({ t, h }) => { const last = t.samples[t.samples.length - 1]; return `<tr class="${h === 'bad' ? 'down' : ''}"><td><span class="dotc" style="background:var(--${h === 'ok' ? 'green' : h === 'warn' ? 'amber' : h === 'bad' ? 'red' : 'surface3'})"></span><b>${esc(t.name || t.target)}</b>${t.name ? `<div class="faint small" style="margin-left:18px">${esc(t.target)}</div>` : ''}${t.error ? `<div class="bad small">${esc(t.error)}</div>` : ''}</td>
        <td style="width:180px">${strip(t.samples, 20)}</td>
        <td class="num ${last && last.status !== 'ok' ? 'bad' : ''}">${last ? (last.status === 'ok' ? ms(last.rtt) + ' ms' : (h === 'bad' ? 'Non risponde' : 'Perso')) : '···'}</td>
        <td class="num">${ms(t.stats.avg)}</td><td class="num">${t.stats.received}/${t.stats.sent}</td><td class="num ${t.stats.lost ? 'warn' : ''}">${pct(t.stats.lossPct)}</td></tr>`; }).join('')}
    </tbody></table></div>`;
  if (!m.running) {
    $('#mCopy').onclick = async () => copy(await api('/api/multi/report'), 'Riepilogo copiato negli appunti');
    $('#mTxt').onclick = async () => download('ping_piu_schede.txt', await api('/api/multi/report'));
  }
}

// ---------- RETE ----------
async function pageNet() {
  $('#page').innerHTML = `<div class="head"><div class="grow"><h1>Rete</h1><p>Configurazione IP del PC, come <b>ipconfig</b></p></div>
    <div class="seg" style="width:300px" id="nView"><button data-v="0">Vista grafica</button><button data-v="1">Vista Prompt</button></div><button class="btn ghost" id="nRefresh">Aggiorna</button></div><div id="nBody" class="stack"><div class="card empty">Lettura della configurazione…</div></div>`;
  $('#nRefresh').onclick = async () => { await loadNet(); renderNet(); toast('Configurazione aggiornata'); };
  $$('#nView button').forEach((b) => b.addEventListener('click', () => { ui.promptView = b.dataset.v === '1'; renderNet(); }));
  try { await loadNet(); } catch (e) { toast(e.message); }
  renderNet();
}
function renderNet() {
  if (page !== 'net' || !net) return;
  $$('#nView button').forEach((b) => b.classList.toggle('on', (b.dataset.v === '1') === ui.promptView));
  const body = $('#nBody');
  if (ui.promptView) {
    body.innerHTML = `<div class="card"><h2>C:\\&gt; ipconfig${ui.showAllIpconfig ? ' /all' : ''} <span class="r"><a href="#" id="nAll">${ui.showAllIpconfig ? 'Ridotto' : '/all'}</a> · <a href="#" id="nCopy">Copia</a></span></h2><div class="console" style="max-height:none">${esc(ui.showAllIpconfig ? net.textAll : net.text)}</div></div>`;
    $('#nAll').onclick = (e) => { e.preventDefault(); ui.showAllIpconfig = !ui.showAllIpconfig; renderNet(); };
    $('#nCopy').onclick = (e) => { e.preventDefault(); copy(ui.showAllIpconfig ? net.textAll : net.text, 'Configurazione copiata'); };
    return;
  }
  const lan = mainLan();
  const others = (net.adapters || []).filter((a) => a !== lan);
  const pingBtn = (addr) => `<button class="x" data-ping="${esc(addr)}" title="Ping ${esc(addr)}">Ping</button>`;
  body.innerHTML = (lan ? `
    <div class="card"><div class="row"><span class="pill idle">${esc(lan.type)} · ${esc(lan.name)}</span>${lan.speedMbps ? `<span class="pill idle">${lan.speedMbps >= 1000 ? lan.speedMbps / 1000 + ' Gbps' : lan.speedMbps + ' Mbps'}</span>` : ''}</div>
      <div class="muted" style="margin-top:14px;font-weight:700">Gateway predefinito</div>
      <div style="font-size:44px;font-weight:800;letter-spacing:-1.5px">${esc(lan.gateway || 'Non presente')}</div>
      <div class="muted">PC: ${esc(lan.ipv4)}/${lan.prefix}</div>
      ${lan.gateway ? `<div class="row" style="margin-top:14px"><button class="btn primary" data-ping="${esc(lan.gateway)}">Ping gateway</button><button class="btn" id="nUse">Usa nel test</button><button class="btn ghost" id="nScan">Cerca dispositivi su questa rete</button></div>` : ''}
    </div>
    <div class="card"><h2>Dettagli</h2><div class="kv">
      <div class="k">Indirizzo IPv4 del PC</div><div class="v">${esc(lan.ipv4)}</div><div></div>
      <div class="k">Subnet mask</div><div class="v">${esc(lan.mask)} (/${lan.prefix})</div><div></div>
      <div class="k">Gateway predefinito</div><div class="v">${esc(lan.gateway || '—')}</div><div>${lan.gateway ? pingBtn(lan.gateway) : ''}</div>
      <div class="k">Indirizzi della rete</div><div class="v">${esc(lan.rangeFrom)} – ${esc(lan.rangeTo)}</div><div></div>
      ${(lan.dns || []).map((d, i) => `<div class="k">${i ? '' : 'Server DNS'}</div><div class="v">${esc(d)}</div><div>${isIPv4(d) ? pingBtn(d) : ''}</div>`).join('')}
      ${lan.dhcp ? `<div class="k">Server DHCP</div><div class="v">${esc(lan.dhcp)}</div><div>${pingBtn(lan.dhcp)}</div>` : ''}
      <div class="k">Indirizzo fisico (MAC)</div><div class="v">${esc(lan.mac || '—')}</div><div></div>
      <div class="k">Descrizione</div><div class="v">${esc(lan.description)}</div><div></div>
    </div></div>` : '<div class="card empty">Nessuna scheda di rete collegata: collega il cavo o il Wi-Fi.</div>') +
    others.map((a) => `<div class="card"><div class="row"><b>${esc(a.type)} · ${esc(a.name)}</b><span class="pill ${a.up ? 'ok' : 'idle'}" style="font-size:12px">${a.up ? 'Collegata' : 'Supporto disconnesso'}</span></div>
      <div class="muted small" style="margin-top:6px">${esc(a.description)}${a.ipv4 ? ` · IPv4 ${esc(a.ipv4)}/${a.prefix}` : ''}${a.gateway ? ` · gateway ${esc(a.gateway)}` : ''}</div></div>`).join('');
  $$('#nBody [data-ping]').forEach((b) => b.addEventListener('click', () => quickPing(b.dataset.ping)));
  if ($('#nUse')) $('#nUse').onclick = () => { ui.target = lan.gateway; localStorage.setItem('pp.target', ui.target); nav('test'); };
  if ($('#nScan')) $('#nScan').onclick = () => nav('scan');
}
async function quickPing(addr) {
  if (S?.busy) { toast("C'è già un test in corso"); return; }
  ui.target = addr; localStorage.setItem('pp.target', addr); nav('test');
  try { await post('/api/test/start', { target: addr, continuous: ui.continuous, count: ui.count, size: ui.size, timeoutMs: ui.timeoutMs, ttl: ui.ttl }); } catch (e) { toast(e.message); }
}

// ---------- CERCA DISPOSITIVI ----------
async function pageScan() {
  $('#page').innerHTML = `<div class="head"><div class="grow"><h1>Cerca dispositivi</h1><p>Controlla una volta ogni indirizzo e mostra chi risponde (32 alla volta, 2 tentativi da 1 s)</p></div></div>
    <div class="card"><div class="row" style="align-items:flex-end">
      <div><label class="lbl" style="margin-top:0">Da</label><input class="input" id="sFrom" style="width:200px"></div>
      <div><label class="lbl" style="margin-top:0">A</label><input class="input" id="sTo" style="width:200px"></div>
      <button class="btn primary" id="sGo" style="height:44px"></button><span class="small muted" id="sInfo"></span></div></div>
    <div id="sBody" class="stack" style="margin-top:14px"></div>`;
  await Promise.all([loadNet(), loadDevices()]).catch(() => {});
  const lan = mainLan();
  $('#sFrom').value = S?.scan?.from || lan?.rangeFrom || ''; $('#sTo').value = S?.scan?.to || lan?.rangeTo || '';
  const info = () => { const a = $('#sFrom').value.trim(), b = $('#sTo').value.trim(); $('#sInfo').textContent = isIPv4(a) && isIPv4(b) && ipNum(b) >= ipNum(a) ? `${ipNum(b) - ipNum(a) + 1} indirizzi · al massimo circa ${Math.ceil((ipNum(b) - ipNum(a) + 1) / 32) * 2} s` : 'Scrivi due indirizzi validi'; };
  $('#sFrom').addEventListener('input', info); $('#sTo').addEventListener('input', info); info();
  $('#sGo').onclick = async () => { try { if (S?.scan?.running) await post('/api/scan/stop'); else { ui.scanSel.clear(); await post('/api/scan/start', { from: $('#sFrom').value.trim(), to: $('#sTo').value.trim() }); } } catch (e) { toast(e.message); } };
  renderScan();
}
function renderScan() {
  if (page !== 'scan') return;
  const sc = S?.scan; const running = sc?.running;
  const g = $('#sGo'); g.className = 'btn ' + (running ? 'danger' : 'primary'); g.innerHTML = running ? IC_STOP + 'Interrompi' : sc?.endedAt ? 'Cerca di nuovo' : 'Avvia ricerca'; g.disabled = !running && S?.busy;
  ['#sFrom', '#sTo'].forEach((s) => { $(s).disabled = !!running; });
  const body = $('#sBody');
  if (!sc || (!sc.running && !sc.endedAt)) { body.innerHTML = '<div class="card empty">L\'intervallo è già impostato sulla rete del PC. Premi <b>Avvia ricerca</b>.</div>'; return; }
  const found = sc.found || [];
  const sel = found.filter((f) => ui.scanSel.has(f.address));
  const toPing = (sel.length ? sel : found).filter((f) => !f.isSelf);
  body.innerHTML = `
    <div class="card"><div class="row"><span style="font-size:42px;font-weight:800">${found.length}</span><span class="muted" style="font-weight:700">${found.length === 1 ? 'dispositivo trovato' : 'dispositivi trovati'}</span><span class="grow"></span><span class="muted">${running ? `${sc.done} di ${sc.total} indirizzi controllati…` : sc.cancelled ? `Ricerca interrotta dopo ${sc.done} indirizzi` : `Controllati ${sc.total} indirizzi in ${dur(sc.endedAt - sc.startedAt)}`}</span></div>
      <div class="progress" style="margin-top:10px"><div style="width:${sc.total ? sc.done * 100 / sc.total : 0}%;${running ? '' : 'background:var(--green)'}"></div></div>
      ${!running && found.length ? `<div class="row" style="margin-top:14px"><button class="btn go" id="sPing" ${toPing.length && !S.busy ? '' : 'disabled'}>${IC_PLAY}${sel.length ? `Pinga i ${toPing.length} selezionati` : `Pinga tutti (${toPing.length})`}</button><button class="btn" id="sAdd">Aggiungi a Più schede</button><button class="btn ghost" id="sSave">Salva come schede</button><button class="btn ghost" id="sCopy">Copia ${(sel.length ? sel : found).length} indirizzi IP</button></div>` : ''}
    </div>
    ${found.length ? `<div class="card" style="padding:8px 12px"><table class="t"><thead><tr>${!running ? '<th style="width:30px"><input type="checkbox" id="sAll"></th>' : ''}<th>Dispositivo</th><th>Indirizzo</th><th class="num">Tempo</th><th class="num">TTL</th><th></th></tr></thead><tbody>
      ${found.map((f) => `<tr>${!running ? `<td><input type="checkbox" data-a="${esc(f.address)}" ${ui.scanSel.has(f.address) ? 'checked' : ''}></td>` : ''}<td><b>${esc(devName(f.address) || f.name || (f.isGateway ? 'Router / gateway' : f.isSelf ? 'Questo PC' : '—'))}</b>${f.isGateway ? ' <span class="pill idle" style="font-size:11px">gateway</span>' : ''}${f.isSelf ? ' <span class="pill idle" style="font-size:11px">questo PC</span>' : ''}</td><td>${esc(f.address)}</td><td class="num">${ms(f.rtt)} ms</td><td class="num">${f.ttl || ''}</td><td class="num"><button class="x" data-ping="${esc(f.address)}" ${S.busy ? 'disabled' : ''}>Ping</button></td></tr>`).join('')}
    </tbody></table></div>` : ''}
    <div class="faint small">Alcuni dispositivi (molti PC con firewall e telefoni) sono collegati ma non rispondono al ping, quindi non compaiono. La ricerca non viene salvata nello storico.</div>`;
  $$('#sBody [data-ping]').forEach((b) => b.addEventListener('click', () => quickPing(b.dataset.ping)));
  $$('#sBody input[data-a]').forEach((c) => c.addEventListener('change', () => { c.checked ? ui.scanSel.add(c.dataset.a) : ui.scanSel.delete(c.dataset.a); renderScan(); }));
  if ($('#sAll')) { $('#sAll').checked = found.length && sel.length === found.length; $('#sAll').onchange = (e) => { ui.scanSel = new Set(e.target.checked ? found.map((f) => f.address) : []); renderScan(); }; }
  if (!running && found.length) {
    const items = () => (sel.length ? sel : found).map((f) => ({ address: f.address, name: devName(f.address) || f.name || (f.isGateway ? 'Router / gateway' : '') }));
    $('#sPing').onclick = async () => {
      const list = toPing.map((f) => ({ address: f.address, name: devName(f.address) || f.name || (f.isGateway ? 'Router / gateway' : '') }));
      try { await api('/api/multi/list', { method: 'PUT', body: JSON.stringify(list) }); multiList = list; await post('/api/multi/start', { targets: list, name: 'Dispositivi trovati', config: { continuous: true } }); nav('multi'); } catch (e) { toast(e.message); }
    };
    $('#sAdd').onclick = async () => { multiList = (await api('/api/multi/list')) || []; addToMulti(items()); };
    $('#sSave').onclick = async () => { let n = 0; for (const it of items()) if (!devName(it.address)) { await post('/api/devices', { name: it.name || 'Dispositivo ' + it.address.split('.').pop(), address: it.address, note: 'Trovato con Cerca dispositivi' }); n++; } await loadDevices(); toast(n ? `Salvate ${n} schede` : 'Erano già tutte salvate'); renderScan(); };
    $('#sCopy').onclick = () => { const l = (sel.length ? sel : found).map((f) => f.address); copy(l.join('\r\n'), `${l.length} indirizzi IP copiati`); };
  }
}

// ---------- SCHEDE ----------
async function pageDevices() {
  $('#page').innerHTML = `<div class="head"><div class="grow"><h1>Schede</h1><p>Indirizzi salvati con un nome</p></div><button class="btn primary" id="dNew">+ Nuova scheda</button></div><div id="dBody"></div>`;
  $('#dNew').onclick = () => editDevice({});
  await loadDevices(); renderDevices();
}
function renderDevices() {
  if (page !== 'devices') return;
  $('#dBody').innerHTML = devices.length ? `<div class="card" style="padding:8px 12px"><table class="t"><thead><tr><th>Nome</th><th>Indirizzo</th><th>Note</th><th></th></tr></thead><tbody>
    ${devices.map((d) => `<tr><td><b>${esc(d.name)}</b></td><td>${esc(d.address)}</td><td class="muted">${esc(d.note)}</td><td class="num" style="white-space:nowrap"><button class="x" data-ping="${esc(d.address)}">Ping</button> <button class="x" data-edit="${esc(d.id)}">Modifica</button> <button class="x bad" data-del="${esc(d.id)}">Elimina</button></td></tr>`).join('')}
    </tbody></table></div>` : '<div class="card empty">Nessuna scheda salvata. Aggiungi le schede che controlli spesso, con un nome facile da ricordare.</div>';
  $$('#dBody [data-ping]').forEach((b) => b.addEventListener('click', () => quickPing(b.dataset.ping)));
  $$('#dBody [data-edit]').forEach((b) => b.addEventListener('click', () => editDevice(devices.find((d) => d.id === b.dataset.edit))));
  $$('#dBody [data-del]').forEach((b) => b.addEventListener('click', async () => { const d = devices.find((x) => x.id === b.dataset.del); if (confirm(`Eliminare «${d.name}»?`)) { await api('/api/devices/' + d.id, { method: 'DELETE' }); await loadDevices(); renderDevices(); } }));
}
function editDevice(d) {
  $('#modal').innerHTML = `<div class="modal"><div class="card" style="width:min(520px,100%)"><h2>${d.id ? 'Modifica scheda' : 'Salva scheda'}<span class="r"><button class="x" id="eX">✕</button></span></h2>
    <label class="lbl">Nome (es. Quadro linea 3)</label><input class="input" id="eName" value="${esc(d.name || '')}">
    <label class="lbl">Indirizzo IP</label><input class="input" id="eAddr" value="${esc(d.address || '')}">
    <label class="lbl">Note</label><input class="input" id="eNote" value="${esc(d.note || '')}">
    <div class="row" style="margin-top:16px;justify-content:flex-end"><button class="btn ghost" id="eCancel">Annulla</button><button class="btn primary" id="eOk">Salva</button></div></div></div>`;
  const close = () => { $('#modal').innerHTML = ''; };
  $('#eX').onclick = close; $('#eCancel').onclick = close;
  $('#eName').focus();
  $('#eOk').onclick = async () => {
    const name = $('#eName').value.trim(), address = $('#eAddr').value.trim();
    if (!name || !validTarget(address)) { toast('Scrivi un nome e un indirizzo valido'); return; }
    try { await post('/api/devices', { id: d.id || '', name, address, note: $('#eNote').value.trim() }); await loadDevices(); close(); toast(`Scheda «${name}» salvata`); if (page === 'devices') renderDevices(); if (page === 'test') testForm(); } catch (e) { toast(e.message); }
  };
}

// ---------- STORICO ----------
async function pageHistory() {
  $('#page').innerHTML = `<div class="head"><div class="grow"><h1>Storico</h1><p id="hCount"></p></div><button class="btn ghost" id="hClear">Svuota storico</button></div><div id="hBody"></div>`;
  $('#hClear').onclick = async () => { if (confirm('Eliminare tutti i test salvati?')) { await api('/api/history', { method: 'DELETE' }); await loadHistory(); renderHistory(); } };
  await loadHistory(); renderHistory();
}
function renderHistory() {
  if (page !== 'history') return;
  $('#hCount').textContent = `${history.length} test salvati`;
  if (!history.length) { $('#hBody').innerHTML = '<div class="card empty">Ogni test viene salvato qui automaticamente.</div>'; return; }
  const groups = []; const idx = {};
  for (const h of history) { if (h.batchId) { if (idx[h.batchId] == null) { idx[h.batchId] = groups.length; groups.push({ batch: h.batchId, items: [] }); } groups[idx[h.batchId]].items.push(h); } else groups.push({ items: [h] }); }
  const verdict = (h) => h.received === 0 ? 'bad' : h.sent > h.received ? 'warn' : 'ok';
  const row = (h, indent) => `<tr class="click" data-id="${esc(h.id)}"><td style="${indent ? 'padding-left:34px' : ''}"><span class="dotc" style="background:var(--${verdict(h) === 'ok' ? 'green' : verdict(h) === 'warn' ? 'amber' : 'red'})"></span><b>${esc(h.name || h.target)}</b>${h.name ? ` <span class="faint small">${esc(h.target)}</span>` : ''}</td><td class="muted">${dt(h.startedAt)}</td><td class="muted">${h.mode === 'continuous' ? 'continuo' : 'singolo'} · ${dur(h.endedAt - h.startedAt)}</td><td class="num">${ms(h.avg)} ms</td><td class="num">${h.received}/${h.sent}</td><td class="num ${h.sent > h.received ? 'warn' : ''}">${pct(h.sent ? (h.sent - h.received) * 100 / h.sent : 0)}</td></tr>`;
  $('#hBody').innerHTML = `<div class="card" style="padding:8px 12px"><table class="t"><thead><tr><th>Scheda</th><th>Data</th><th>Prova</th><th class="num">Media</th><th class="num">Ricevuti</th><th class="num">Perdita</th></tr></thead><tbody>
    ${groups.map((g) => g.batch ? `<tr><td colspan="6" style="background:var(--surface2)"><b>📁 ${esc(g.items[0].batchName || 'Più schede')}</b> <span class="muted small">· ${g.items.length} schede · ${g.items.filter((h) => verdict(h) === 'ok').length} OK · ${g.items.filter((h) => verdict(h) === 'warn').length} con perdite · ${g.items.filter((h) => verdict(h) === 'bad').length} senza risposta</span></td></tr>` + g.items.map((h) => row(h, true)).join('') : row(g.items[0], false)).join('')}
  </tbody></table></div>`;
  $$('#hBody tr[data-id]').forEach((r) => r.addEventListener('click', () => historyDetail(r.dataset.id)));
}
async function historyDetail(id) {
  const { summary: s, detail: d } = await api('/api/history/' + id);
  const text = await api(`/api/history/${id}/export`);
  $('#modal').innerHTML = `<div class="modal"><div class="card"><h2>${esc(s.name || s.target)} · ${dt(s.startedAt)}<span class="r"><button class="x" id="dX">✕ Chiudi</button></span></h2>
    <div class="tiles" style="margin-bottom:12px">
      <div class="tile"><span>Ricevuti</span><b>${s.received}/${s.sent}</b></div><div class="tile"><span>Perdita</span><b>${pct(s.sent ? (s.sent - s.received) * 100 / s.sent : 0)}</b></div>
      <div class="tile"><span>Media</span><b>${ms(s.avg)} ms</b></div><div class="tile"><span>Min / Max</span><b>${ms(s.min)} / ${ms(s.max)}</b></div></div>
    <canvas class="chart" id="dChart"></canvas>
    <div class="console" style="margin-top:12px">${esc(text)}</div>
    <div class="row" style="margin-top:14px"><button class="btn" id="dCopy">Copia report</button><button class="btn ghost" id="dTxt">Salva .txt</button><button class="btn ghost" id="dCsv">Salva .csv (Excel)</button><button class="btn ghost" id="dAgain">Ripeti</button><span class="grow"></span><button class="btn ghost bad" id="dDel">Elimina</button></div></div></div>`;
  const close = () => { $('#modal').innerHTML = ''; };
  $('#dX').onclick = close;
  drawChart($('#dChart'), d.samples.length > 600 ? d.samples.filter((_, i) => i % Math.ceil(d.samples.length / 600) === 0) : d.samples, s.avg);
  $('#dCopy').onclick = () => copy(text, 'Report copiato negli appunti');
  $('#dTxt').onclick = () => download(`ping_${s.ip}.txt`, text);
  $('#dCsv').onclick = async () => download(`ping_${s.ip}.csv`, await api(`/api/history/${id}/export?format=csv`), 'text/csv;charset=utf-8');
  $('#dAgain').onclick = () => { close(); ui.continuous = s.mode === 'continuous'; ui.count = s.count; quickPing(s.target); };
  $('#dDel').onclick = async () => { if (confirm('Eliminare questo test?')) { await api('/api/history/' + id, { method: 'DELETE' }); close(); await loadHistory(); renderHistory(); } };
}

// ---------- GUIDA ----------
function pageGuide() {
  $('#page').innerHTML = `<div class="head"><div class="grow"><h1>Guida</h1><p>Le cose essenziali, in parole semplici</p></div></div>
  <div class="card guide">
    <h3>Come eseguire un ping</h3><p>Collega il PC alla rete della scheda, scrivi il suo indirizzo IP (o tocca <b>Gateway</b> / una scheda salvata), scegli <b>Singolo</b> (4 richieste, come <code>ping</code>) o <b>Continuo</b> (come <code>ping -t</code>) e premi <b>Avvia test</b>.</p>
    <h3>È lo stesso ping di Windows?</h3><p>Sì: PingPro usa la stessa funzione di sistema di <code>ping.exe</code> (IcmpSendEcho): 32 byte di dati con lo stesso contenuto, timeout di 4 secondi, TTL 128, circa una richiesta al secondo. In più vedi i decimali (es. 0,42 ms) dove Windows scrive <code>durata&lt;1ms</code>. Il riquadro <b>Come nel Prompt</b> mostra le stesse righe che vedresti nella finestra nera.</p>
    <h3>Come leggere il risultato</h3><p><b class="ok">ONLINE</b>: risponde regolarmente. <b class="warn">INSTABILE / LENTA</b>: qualche pacchetto perso negli ultimi 10 o tempi oltre 100 ms. <b class="bad">NON RISPONDE</b>: 2 o più richieste di fila senza risposta (suona un avviso). Una rete locale via cavo risponde di solito in meno di 1–2 ms, via Wi-Fi in pochi millisecondi; 0% persi è l'obiettivo.</p>
    <h3>Gateway e ipconfig</h3><p>Nella sezione <b>Rete</b> trovi indirizzo del PC, subnet mask e gateway predefinito come in <code>ipconfig</code>, con il pulsante per pingarli. Se il gateway risponde ma la scheda no, cerca il problema dalla parte della scheda (alimentazione, cavo, indirizzo, configurazione). Alcuni apparecchi non rispondono al ping per scelta.</p>
    <h3>Più schede e Cerca dispositivi</h3><p><b>Cerca dispositivi</b> controlla una volta tutta la rete (es. da .1 a .254) e mostra chi risponde; con <b>Pinga tutti</b> li metti sotto controllo continuo in <b>Più schede</b>, dove le schede con problemi compaiono per prime.</p>
    <h3>Collegamento diretto PC → scheda</h3><p>Se colleghi il cavo direttamente alla scheda (senza switch o router) e la scheda ha un indirizzo fisso, il PC deve avere un indirizzo nella stessa rete (es. scheda 192.168.1.100 → PC 192.168.1.200, subnet 255.255.255.0). Si imposta in Impostazioni di Windows › Rete › Ethernet › Assegnazione IP › Manuale.</p>
    <h3>Dove sono i miei dati</h3><p class="small" id="gData"></p>
  </div>`;
  api('/api/info').then((i) => { $('#gData').textContent = `Schede e storico sono salvati in ${i.data}. PingPro ${i.version}.`; }).catch(() => {});
}

// ---------- Avvio e aggiornamenti in tempo reale ----------
function onState(prev, cur) {
  checkAlerts(prev, cur);
  $('#dotTest').style.background = cur.single?.running ? (cur.single.status === 'down' ? 'var(--red)' : 'var(--green)') : 'transparent';
  $('#dotMulti').style.background = cur.multi?.running ? ((cur.multi.targets || []).some((t) => t.status === 'down') ? 'var(--red)' : 'var(--green)') : 'transparent';
  $('#dotScan').style.background = cur.scan?.running ? 'var(--accent)' : 'transparent';
  const finishedSingle = prev?.single?.running && !cur.single?.running; const finishedMulti = prev?.multi?.running && !cur.multi?.running;
  if (finishedSingle || finishedMulti) loadHistory().catch(() => {});
  if (page === 'test') renderTest(); else if (page === 'multi') renderMulti(); else if (page === 'scan') renderScan();
}
function connect() {
  const es = new EventSource('/api/events?token=' + TOKEN);
  es.onmessage = (e) => { const prev = S; S = JSON.parse(e.data); onState(prev, S); };
  es.onerror = () => { es.close(); setTimeout(connect, 1500); };
}
api('/api/info').then((i) => { $('#foot').textContent = `PingPro ${i.version} per ${i.platform === 'windows' ? 'Windows' : i.platform}`; }).catch(() => {});
nav('test');
connect();
setInterval(() => { if (S?.single?.running && page === 'test') renderTest(); if (S?.multi?.running && page === 'multi') renderMulti(); }, 1000);
