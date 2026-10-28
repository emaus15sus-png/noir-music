// Noir for PC: Spotify-style UI. Songs look like { id, t: title, a: artist, i: cover, d: seconds } everywhere,
// the same shape the engine and the TV's remote use.
const api = window.noir;
const $ = s => document.querySelector(s);

// --- Small DOM helpers. Text always goes in as text nodes, so song titles can never inject HTML. ---
const PATHS = {
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  library: 'M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 5h-3v5.5a2.5 2.5 0 1 1-2.5-2.5c.57 0 1.08.19 1.5.51V5h4v2zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  close: 'M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  heart: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
  heartOutline: 'M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z',
  shuffle: 'M10.59 9.17 5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z',
  prev: 'M6 6h2v12H6zm3.5 6 8.5 6V6z',
  next: 'M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z',
  play: 'M8 5v14l11-7z',
  pause: 'M6 19h4V5H6v14zm8-14v14h4V5h-4z',
  queue: 'M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18A2.94 2.94 0 0 0 16 14a3 3 0 1 0 3 3V8h3V6h-5z',
  devices: 'M4 6h18V4H4c-1.1 0-2 .9-2 2v11H0v3h14v-3H4V6zm19 2h-6c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h6c.55 0 1-.45 1-1V9c0-.55-.45-1-1-1zm-1 9h-4v-7h4v7z',
  tv: 'M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z',
  pc: 'M20 18c1.1 0 1.99-.9 1.99-2L22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 6h16v10H4V6z',
  volume: 'M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z',
  more: 'M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  sparkle: 'M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z',
};
const svg = n => `<svg viewBox="0 0 24 24"><path d="${PATHS[n]}"/></svg>`; // fixed icon markup only, never data
const icon = n => { const s = document.createElement('span'); s.className = 'ico'; s.innerHTML = svg(n); return s; };
const setIcon = (el, n) => { el.querySelector('.ico').innerHTML = svg(n); };
document.querySelectorAll('[data-ico]').forEach(e => { e.innerHTML = svg(e.dataset.ico); });

function h(tag, props = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k.startsWith('on')) e[k] = v;
    else if (k === 'class') e.className = v;
    else if (v !== false && v != null) e.setAttribute(k, v);
  }
  e.append(...kids.flat().filter(k => k != null && k !== false));
  return e;
}
function img(src, cls = '') {
  const i = h('img', { class: cls, src: src || 'logo.png', alt: '', loading: 'lazy' });
  i.onerror = () => { i.onerror = null; i.src = 'logo.png'; };
  return i;
}
const time = sec => `${Math.floor(sec / 60)}:${String(Math.floor(sec % 60)).padStart(2, '0')}`;
const count = n => `${n} song${n === 1 ? '' : 's'}`;

let toastTimer;
function toast(msg, ms = 2800) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('on');
  clearTimeout(toastTimer);
  if (ms) toastTimer = setTimeout(() => t.classList.remove('on'), ms);
}

/** Text prompt, or a yes/no question when [withInput] is false. Resolves to the text (or true), or null. */
function ask(title, value = '', withInput = true, ok = 'Save') {
  const m = $('#modal'), input = $('#modalInput'), form = m.querySelector('form');
  $('#modalTitle').textContent = title;
  input.hidden = !withInput; input.value = value;
  form.querySelector('button:not([type])').textContent = ok;
  m.hidden = false;
  if (withInput) input.focus();
  return new Promise(done => {
    const close = v => { m.hidden = true; form.onsubmit = null; $('#modalCancel').onclick = null; done(v); };
    form.onsubmit = e => { e.preventDefault(); close(withInput ? (input.value.trim() || null) : true); };
    $('#modalCancel').onclick = () => close(null);
  });
}

/** Right-click style menu at the mouse. Items: [label, action] or '-' for a divider. */
function menu(e, items) {
  e.preventDefault(); e.stopPropagation();
  const m = $('#menu');
  m.replaceChildren(...items.filter(Boolean).map(it => it === '-' ? h('hr')
    : h('button', { onclick: ev => { ev.stopPropagation(); m.hidden = true; it[1](ev); } }, it[0])));
  m.hidden = false;
  const r = m.getBoundingClientRect();
  m.style.left = `${Math.min(e.clientX, innerWidth - r.width - 8)}px`;
  m.style.top = `${Math.min(e.clientY, innerHeight - r.height - 8)}px`;
}
addEventListener('click', e => {
  if (!$('#menu').contains(e.target)) $('#menu').hidden = true;
  if (!$('#devices').contains(e.target) && !$('#devBtn').contains(e.target)) $('#devices').hidden = true;
});
addEventListener('keydown', e => {
  if (e.key === 'Escape') { $('#menu').hidden = true; $('#devices').hidden = true; }
  if (e.target.matches('input')) return;
  if (e.code === 'Space') { e.preventDefault(); toggle(); }
  if (e.ctrlKey && e.key.toLowerCase() === 'f') { e.preventDefault(); go('search'); }
});

// --- Library (saved by the main process to library.json) ---
let lib;
const save = k => api.save(k, lib[k]);
const artistKey = a => (a || '').toLowerCase().split(/,|&| feat| ft\.| x /)[0].trim();
const isLiked = id => lib.liked.some(s => s.id === id);
const topArtists = n => Object.entries(lib.scores).filter(([, v]) => v > 0).sort((a, b) => b[1] - a[1]).slice(0, n).map(([k]) => k);
const freeName = n => { let name = n, i = 2; while (lib.playlists[name]) name = `${n} ${i++}`; return name; };
const clean = s => ({ id: s.id, t: s.t, a: s.a, i: s.i, d: s.d || 0 }); // drop extra fields (like the TV's queue position)

/** Taste, like the TV's: +1 artist on a finished song, +3 on a like, -1 on a skip in the first 30 s. */
function bump(artist, d) { const k = artistKey(artist); if (k) { lib.scores[k] = (lib.scores[k] || 0) + d; save('scores'); } }
function record(s, completed, ms) {
  const skipped = !completed && ms < 30000;
  bump(s.a, completed ? 1 : skipped ? -1 : 0.3);
  if (skipped) { lib.skips[s.id] = (lib.skips[s.id] || 0) + 1; save('skips'); }
  else { lib.history = [clean(s), ...lib.history.filter(x => x.id !== s.id)].slice(0, 100); save('history'); }
}
function toggleLike(s) {
  const now = !isLiked(s.id);
  lib.liked = now ? [clean(s), ...lib.liked] : lib.liked.filter(x => x.id !== s.id);
  bump(s.a, now ? 3 : -3); save('liked');
  toast(now ? 'Added to Liked Songs' : 'Removed from Liked Songs');
  refresh();
}
function addTo(name, s) {
  const l = lib.playlists[name] ||= [];
  if (s && !l.some(x => x.id === s.id)) l.push(clean(s));
  save('playlists'); refresh();
  if (s) toast(`Added to ${name}`);
}
async function newPlaylist(s) {
  const name = await ask('Create playlist', freeName('My playlist'));
  if (!name) return;
  if (lib.playlists[name]) return toast('A playlist with that name already exists');
  addTo(name, s);
  if (!s) go('playlist', name);
}
async function renamePlaylist(name) {
  const to = await ask('Rename playlist', name);
  if (!to || to === name) return;
  if (lib.playlists[to]) return toast('A playlist with that name already exists');
  lib.playlists = Object.fromEntries(Object.entries(lib.playlists).map(([k, v]) => [k === name ? to : k, v])); // keeps the order
  save('playlists');
  if (view.arg === name) view.arg = to;
  refresh();
}
async function deletePlaylist(name) {
  if (!await ask(`Delete "${name}"? This can't be undone.`, '', false, 'Delete')) return;
  delete lib.playlists[name]; save('playlists');
  if (view.arg === name) go('home'); else refresh();
}

// --- Player: plays on this PC, or drives the Fire TV when it is the chosen device ---
const audio = new Audio();
let queue = [], index = -1, playToken = 0, extending = false, fails = 0, retried = false;
let device = 'pc', tv = null, tvTimer, polledAt = 0, dragging = false;
const current = () => device === 'tv' ? tv?.song : queue[index];

function playList(list, i = 0) {
  if (!list?.length) return;
  if (device === 'tv') return sendToTv(list, i, 0);
  queue = list.map(clean); index = i; start();
}
async function start() {
  const s = queue[index];
  if (!s) return;
  const t = ++playToken; retried = false; showNow();
  try {
    const { url } = await api.engine('/stream', { id: s.id });
    if (t !== playToken) return;
    audio.src = url;
    await audio.play();
    fails = 0;
  } catch {
    if (t !== playToken) return; // a newer song took over; its play() interrupted ours
    if (++fails < 4 && index + 1 < queue.length) { toast(`Couldn't play "${s.t}", skipping`); index++; return start(); }
    return toast(`Couldn't play "${s.t}". Check your internet.`);
  }
  if (queue[index + 1]) api.engine('/prefetch', { id: queue[index + 1].id }).catch(() => {});
  extend();
}
// A stream link can expire mid-song: get a fresh one once and carry on from the same spot
audio.onerror = async () => {
  const s = queue[index];
  if (!s || retried || !audio.src) return;
  retried = true;
  const at = audio.currentTime, t = playToken;
  try {
    const { url } = await api.engine('/stream', { id: s.id, fresh: 1 });
    if (t !== playToken) return;
    audio.src = url; audio.currentTime = at; await audio.play();
  } catch { toast(`"${s.t}" stopped. Check your internet.`); }
};
function move(i, completed = false) {
  const s = queue[index];
  if (s) record(s, completed, audio.currentTime * 1000);
  if (i < 0 || i >= queue.length) { audio.pause(); return showNow(); }
  index = i; start();
}
audio.onended = () => move(index + 1, true);
audio.onplay = audio.onpause = () => showNow();

/** Autoplay: with 2 songs left, add 10 from the current song's YouTube Music radio. */
async function extend() {
  const s = queue[index];
  if (!s || extending || queue.length - index > 2) return;
  extending = true;
  try {
    const have = new Set(queue.map(x => x.id));
    const more = (await api.engine('/radio', { id: s.id })).filter(x => !have.has(x.id) && (lib.skips[x.id] || 0) < 2).slice(0, 10);
    queue.push(...more); renderQueue();
  } catch { /* autoplay just stops; the queue is still fine */ } finally { extending = false; }
}

function toggle() {
  if (device === 'tv') return tvCmd('/toggle');
  if (!queue[index]) return;
  audio.paused ? audio.play() : audio.pause();
}
function next() { device === 'tv' ? tvCmd('/next') : move(index + 1); }
function prev() {
  if (device === 'tv') return tvCmd('/prev');
  if (audio.currentTime > 3 || index <= 0) audio.currentTime = 0; else move(index - 1);
}
function addToQueue(s, playNext = false) {
  if (device === 'tv') return tvCmd(`/add?${songQuery(s)}`).then(() => toast('Added to the TV queue'));
  if (!queue[index]) return playList([s]);
  queue.splice(playNext ? index + 1 : queue.length, 0, clean(s));
  renderQueue(); toast(playNext ? 'Playing next' : 'Added to queue');
}
function shuffleNext() {
  if (device === 'tv') return toast('Shuffle works on this computer');
  const rest = queue.slice(index + 1);
  for (let i = rest.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [rest[i], rest[j]] = [rest[j], rest[i]]; }
  queue.splice(index + 1, rest.length, ...rest); renderQueue(); toast('Shuffled what\'s next');
}

// --- Fire TV as a device ---
const songQuery = (s, extra = {}) => new URLSearchParams({ id: s.id, t: s.t, a: s.a || '', i: s.i || '', d: s.d || 0, ...extra }).toString();
async function pollTv() {
  try { tv = await api.tv('get', '/state'); polledAt = Date.now(); } catch { tv = null; }
  showNow();
}
async function tvCmd(route) {
  try { await api.tv('post', route); setTimeout(pollTv, 150); } catch { toast('Lost the Fire TV. Is Noir still open on it?'); }
}
/** Hands songs to the TV: the first plays (from [pos] ms), up to 9 more queue up, then the TV's autoplay carries on. */
async function sendToTv(list, i, pos) {
  try {
    await api.tv('post', `/play?${songQuery(list[i], { pos: Math.floor(pos) })}`);
    for (const s of list.slice(i + 1, i + 10)) await api.tv('post', `/add?${songQuery(s)}`);
    toast('Playing on Fire TV'); pollTv();
  } catch { toast("Couldn't reach the Fire TV"); }
}
async function useTv() {
  $('#devices').hidden = true;
  const s = queue[index], playing = s && !audio.paused;
  device = 'tv'; audio.pause();
  if (playing) await sendToTv(queue, index, audio.currentTime * 1000);
  clearInterval(tvTimer); tvTimer = setInterval(pollTv, 1000); pollTv();
}
async function usePc() {
  $('#devices').hidden = true;
  clearInterval(tvTimer);
  const st = tv; device = 'pc'; tv = null;
  if (st?.song && st.playing) {
    await api.tv('post', '/toggle').catch(() => {});
    queue = [st.song, ...st.queue].map(clean); index = 0;
    await start(); audio.currentTime = st.pos / 1000;
  }
  showNow();
}
async function openDevices() {
  const pop = $('#devices');
  pop.hidden = !pop.hidden;
  if (pop.hidden) return;
  const tvRow = h('button', { class: `dev${device === 'tv' ? ' on' : ''}` }, icon('tv'), h('div', {}, h('b', {}, 'Fire TV'), h('span', {}, 'Looking for Noir on your Wi-Fi…')));
  pop.replaceChildren(h('h3', {}, 'Connect to a device'),
    h('button', { class: `dev${device === 'pc' ? ' on' : ''}`, onclick: usePc }, icon('pc'), h('div', {}, h('b', {}, 'This computer'), h('span', {}, 'Play here'))),
    tvRow);
  const ip = await api.tv('find').catch(() => null);
  const status = tvRow.querySelector(':scope > div > span');
  if (ip) { status.textContent = `Noir on ${ip}`; tvRow.onclick = useTv; return; }
  status.textContent = 'Not found. Is Noir open on the TV?';
  const wake = h('button', { class: 'pill', onclick: async () => {
    wake.textContent = 'Waking the TV…'; wake.disabled = true;
    if (await api.tv('wake')) { pop.hidden = true; return openDevices(); }
    wake.textContent = "Couldn't wake it. Open Noir on the TV."; } }, 'Wake TV and open Noir');
  pop.append(wake);
}

// --- Now playing: player bar, media keys, highlighted rows ---
function showNow() {
  const s = current();
  const playing = device === 'tv' ? !!tv?.playing : !!s && !audio.paused;
  $('#npArt').src = s?.i || 'logo.png';
  $('#npTitle').textContent = s?.t || 'Nothing playing';
  $('#npArtist').textContent = s ? s.a : 'Pick a song or a mood';
  const liked = device === 'tv' ? !!tv?.liked : !!s && isLiked(s.id);
  $('#npLike').classList.toggle('on', liked); setIcon($('#npLike'), liked ? 'heart' : 'heartOutline');
  setIcon($('#play'), playing ? 'pause' : 'play');
  $('#devLabel').textContent = device === 'tv' ? (tv ? 'Playing on Fire TV' : 'Fire TV not responding') : '';
  $('#devBtn').classList.toggle('on', device === 'tv');
  document.title = s ? `${s.t} · ${s.a}` : 'Noir';
  document.querySelectorAll('.row[data-id]').forEach(r => r.classList.toggle('on', r.dataset.id === s?.id));
  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = s ? new MediaMetadata({ title: s.t, artist: s.a, artwork: s.i ? [{ src: s.i, sizes: '544x544' }] : [] }) : null;
  }
  renderQueue();
}
if ('mediaSession' in navigator) {
  for (const [a, f] of [['play', toggle], ['pause', toggle], ['nexttrack', next], ['previoustrack', prev]]) navigator.mediaSession.setActionHandler(a, f);
}
setInterval(() => {
  const [pos, dur] = device === 'tv'
    ? [tv ? (tv.pos + (tv.playing ? Date.now() - polledAt : 0)) / 1000 : 0, (tv?.dur || 0) / 1000]
    : [audio.currentTime || 0, audio.duration || queue[index]?.d || 0];
  if (!dragging) { $('#seek').value = dur ? Math.min(1000, (pos / dur) * 1000) : 0; $('#cur').textContent = time(Math.min(pos, dur || pos)); }
  $('#dur').textContent = time(dur);
  $('#seek').style.setProperty('--p', `${$('#seek').value / 10}%`);
}, 250);

$('#seek').oninput = () => {
  dragging = true;
  const dur = device === 'tv' ? (tv?.dur || 0) / 1000 : audio.duration || 0;
  $('#cur').textContent = time(dur * $('#seek').value / 1000);
  $('#seek').style.setProperty('--p', `${$('#seek').value / 10}%`);
};
$('#seek').onchange = () => {
  dragging = false;
  const f = $('#seek').value / 1000;
  if (device === 'tv') { if (tv?.dur) tvCmd(`/seek?ms=${Math.floor(tv.dur * f)}`); }
  else if (audio.duration) audio.currentTime = audio.duration * f;
};
$('#vol').oninput = () => {
  audio.volume = $('#vol').value / 100; lib.volume = audio.volume;
  $('#vol').style.setProperty('--p', `${$('#vol').value}%`);
};
$('#vol').onchange = () => save('volume');
$('#play').onclick = toggle;
$('#next').onclick = next;
$('#prev').onclick = prev;
$('#shuffle').onclick = shuffleNext;
$('#npLike').onclick = () => { const s = current(); if (!s) return; device === 'tv' ? tvCmd('/like') : toggleLike(s); };
$('#devBtn').onclick = openDevices;
$('#qBtn').onclick = () => { $('#queuePanel').hidden = !$('#queuePanel').hidden; $('#qBtn').classList.toggle('on', !$('#queuePanel').hidden); renderQueue(); };
$('#qClose').onclick = () => $('#qBtn').click();
$('#newList').onclick = () => newPlaylist();

// --- Song rows and menus ---
function songMenu(e, s, playlist) {
  menu(e, [
    ['Add to queue', () => addToQueue(s)],
    device === 'pc' && ['Play next', () => addToQueue(s, true)],
    ['Add to playlist…', ev => menu(ev, [['New playlist…', () => newPlaylist(s)], Object.keys(lib.playlists).length ? '-' : null,
      ...Object.keys(lib.playlists).map(n => [n, () => addTo(n, s)])])],
    [isLiked(s.id) ? 'Remove from Liked Songs' : 'Save to Liked Songs', () => toggleLike(s)],
    playlist && '-',
    playlist && ['Remove from this playlist', () => { lib.playlists[playlist] = lib.playlists[playlist].filter(x => x.id !== s.id); save('playlists'); refresh(); }],
  ]);
}
function songRow(s, n, onPlay, playlist) {
  const liked = isLiked(s.id);
  return h('div', { class: `row${current()?.id === s.id ? ' on' : ''}`, 'data-id': s.id, onclick: onPlay, oncontextmenu: e => songMenu(e, s, playlist) },
    h('span', { class: 'n' }, String(n)),
    h('div', { class: 'who' }, img(s.i), h('div', {}, h('b', {}, s.t), h('span', {}, s.a))),
    h('span', { class: 't' }, s.d ? time(s.d) : ''),
    h('button', { class: `round${liked ? ' on' : ''}`, title: liked ? 'Remove from Liked Songs' : 'Save to Liked Songs', onclick: e => { e.stopPropagation(); toggleLike(s); } }, icon(liked ? 'heart' : 'heartOutline')),
    h('button', { class: 'round', title: 'More', onclick: e => songMenu(e, s, playlist) }, icon('more')));
}
function card(title, sub, cover, onPlay, onOpen = onPlay) {
  return h('div', { class: 'tcard', onclick: onOpen },
    cover, h('b', {}, title), h('span', {}, sub),
    h('button', { class: 'fab', title: `Play ${title}`, onclick: e => { e.stopPropagation(); onPlay(); } }, icon('play')));
}
const shelf = songs => songs?.length
  ? h('div', { class: 'shelf' }, songs.map((s, i) => card(s.t, s.a, img(s.i), () => playList(songs, i))))
  : h('p', { class: 'empty' }, songs ? 'Nothing here yet.' : 'Loading…');
const likedTile = () => h('div', { class: 'tile' }, icon('heart'));
const listCover = (name, cls = '') => lib.playlists[name]?.[0] ? img(lib.playlists[name][0].i, cls) : h('div', { class: 'tile mood' }, icon('library'));

// --- Views ---
let view = { name: 'home' };
function go(name, arg) { view = { name, arg }; $('#main').scrollTop = 0; render(); }
function refresh() { renderLib(); if (view.name !== 'search') render(); showNow(); }
function render() {
  document.querySelectorAll('.nav').forEach(b => b.classList.toggle('on', b.dataset.view === view.name));
  const v = $('#view'); v.replaceChildren();
  ({ home: viewHome, search: viewSearch, liked: viewList, playlist: viewList })[view.name](v);
  renderLib();
}
document.querySelectorAll('.nav').forEach(b => { b.onclick = () => go(b.dataset.view); });

// Home: greeting, quick picks, Mood DJ, moods, and mixes built from your taste
let moods = [];
const home = { slot: null, auto: null, autoLabel: '', made: null };
const greeting = () => { const hr = new Date().getHours(); return hr < 5 ? 'Good night' : hr < 12 ? 'Good morning' : hr < 18 ? 'Good afternoon' : 'Good evening'; };
const moodParams = () => ({
  artists: topArtists(3).join('|'), exclude: lib.history.slice(0, 40).map(s => s.id).join(','), hour: new Date().getHours(),
  picks: Object.entries(lib.picks).map(([k, n]) => `${k}:${n}`).join(','),
});
/** Mood DJ: a mix for a typed feeling ([prompt], saved as a playlist) or a mood card, played right away. */
async function moodPlay(params, prompt) {
  toast(prompt ? 'Reading your mood…' : 'Making your mix…', 0);
  try {
    const r = await api.engine('/mix', { ...moodParams(), ...params });
    if (!r.songs.length) return toast("Couldn't load songs. Check your internet.");
    const k = `${r.slot}:${r.mood}`; lib.picks[k] = (lib.picks[k] || 0) + 1; save('picks');
    if (prompt) {
      const name = freeName(`${r.label} · ${prompt.slice(0, 24).trim()}`);
      lib.playlists[name] = r.songs; save('playlists'); refresh();
      toast(`Saved "${name}" to Your Library`);
    } else toast(`Playing your ${r.label.toLowerCase()} mix`);
    playList(r.songs, 0);
  } catch { toast("Couldn't make a mix. Check your internet."); }
}
async function loadHome() {
  const slot = Math.floor(new Date().getHours() / 6);
  if (home.slot === slot) return; // loaded, or loading, for this part of the day
  home.slot = slot;
  const [auto, made] = await Promise.all([
    api.engine('/mix', moodParams()).catch(() => null),
    madeForYou().catch(() => []),
  ]);
  home.auto = auto?.songs ?? []; home.autoLabel = auto ? `Picked for your ${auto.slotName} · ${auto.label}` : 'Picked for you';
  home.made = made;
  if (view.name === 'home') render();
}
/** Radios of a couple of liked songs and recent listens, merged and fresh. */
async function madeForYou() {
  const seeds = [...lib.liked.slice(0, 2), ...lib.history.slice(0, 2)].map(s => s.id).filter((v, i, a) => a.indexOf(v) === i);
  if (!seeds.length) return [];
  const radios = await Promise.all(seeds.map(id => api.engine('/radio', { id }).catch(() => [])));
  const recent = new Set(lib.history.slice(0, 40).map(s => s.id));
  const seen = new Set();
  return Array.from({ length: Math.max(...radios.map(r => r.length)) }, (_, i) => radios.map(r => r[i])).flat()
    .filter(s => s && !recent.has(s.id) && (lib.skips[s.id] || 0) < 2 && !seen.has(s.id) && seen.add(s.id)).slice(0, 25);
}
function viewHome(v) {
  const input = h('input', { placeholder: 'How are you feeling? Try "tired after work, want something calm"', maxlength: 200 });
  v.append(
    h('h1', {}, greeting()),
    h('div', { class: 'quick' },
      h('button', { onclick: () => go('liked') }, likedTile(), 'Liked Songs'),
      Object.keys(lib.playlists).slice(0, 5).map(n => h('button', { onclick: () => go('playlist', n) }, listCover(n), n))),
    h('h2', {}, 'Mood DJ', h('small', {}, 'Tell Noir how you feel: it builds a mix, saves it to Your Library and plays it')),
    h('form', { class: 'prompt', onsubmit: e => { e.preventDefault(); const t = input.value.trim(); if (t) { input.value = ''; moodPlay({ text: t }, t); } } },
      h('button', { class: 'fab', title: 'Make my mix' }, icon('sparkle')), input),
    moods.length ? h('div', { class: 'moods' }, moods.map(m => h('button', { style: `background: linear-gradient(135deg, ${m.from}, ${m.to})`, onclick: () => moodPlay({ mood: m.name }) }, m.label)))
      : h('p', { class: 'empty' }, 'Loading moods…'),
    h('h2', {}, home.autoLabel || 'Picked for you'), shelf(home.auto),
    h('h2', {}, 'Made for you'), lib.liked.length || lib.history.length ? shelf(home.made) : h('p', { class: 'empty' }, 'Like a few songs and your mixes show up here.'),
    h('h2', {}, 'Recently played'), shelf(lib.history.slice(0, 20)),
    Object.keys(lib.playlists).length ? [h('h2', {}, 'Your playlists'), h('div', { class: 'shelf' }, Object.entries(lib.playlists).map(([n, l]) =>
      card(n, `Playlist · ${count(l.length)}`, listCover(n), () => playList(l), () => go('playlist', n))))] : null,
  );
  loadHome();
}

// Search: results as you type
const search = { q: '', results: null };
function viewSearch(v) {
  const input = h('input', { placeholder: 'What do you want to listen to?', value: search.q, maxlength: 100 });
  const out = h('div');
  v.append(h('div', { class: 'searchBox' }, icon('search'), input), out);
  let timer;
  const show = () => {
    const r = search.results;
    if (!search.q) return out.replaceChildren(h('h2', {}, 'Browse moods'), h('div', { class: 'moods' }, moods.map(m =>
      h('button', { style: `background: linear-gradient(135deg, ${m.from}, ${m.to})`, onclick: () => moodPlay({ mood: m.name }) }, m.label))));
    if (!r) return out.replaceChildren(h('p', { class: 'empty' }, 'Searching…'));
    if (!r.length) return out.replaceChildren(h('p', { class: 'empty' }, `No songs found for "${search.q}".`));
    out.replaceChildren(h('h2', {}, 'Songs'), h('div', { class: 'rows' }, r.map((s, i) => songRow(s, i + 1, () => playList([s])))));
  };
  input.oninput = () => {
    clearTimeout(timer);
    timer = setTimeout(async () => {
      const q = input.value.trim();
      search.q = q; search.results = null; show();
      if (!q) return;
      const r = await api.engine('/search', { q }).catch(() => []);
      if (search.q !== q) return; // typed on; a newer search owns the results
      search.results = r; show();
    }, 350);
  };
  show();
  setTimeout(() => input.focus());
}

// Liked Songs and playlists
function viewList(v) {
  const liked = view.name === 'liked', name = view.arg;
  const songs = liked ? lib.liked : lib.playlists[name];
  if (!songs) return go('home');
  const mins = Math.round(songs.reduce((t, s) => t + (s.d || 0), 0) / 60);
  v.append(
    h('div', { class: 'hero' }, liked ? likedTile() : listCover(name),
      h('div', {}, h('small', {}, 'Playlist'), h('h1', {}, liked ? 'Liked Songs' : name), h('span', {}, `${count(songs.length)}${mins ? ` · about ${mins} min` : ''}`))),
    h('div', { class: 'actions' },
      h('button', { class: 'fab', title: 'Play', onclick: () => playList(songs) }, icon('play')),
      h('button', { class: 'round', title: 'Shuffle play', onclick: () => playList([...songs].sort(() => Math.random() - 0.5)) }, icon('shuffle')),
      !liked && h('button', { class: 'round', title: 'More', onclick: e => menu(e, [['Rename', () => renamePlaylist(name)], ['Delete', () => deletePlaylist(name)]]) }, icon('more'))),
    songs.length ? h('div', { class: 'rows' }, songs.map((s, i) => songRow(s, i + 1, () => playList(songs, i), liked ? null : name)))
      : h('p', { class: 'empty' }, liked ? 'Songs you like show up here. Tap the heart on any song.' : 'Right-click any song and choose "Add to playlist".'),
  );
}

// --- Sidebar library and queue ---
function renderLib() {
  const item = (title, sub, cover, on, onclick, oncontextmenu) =>
    h('button', { class: `libItem${on ? ' on' : ''}`, onclick, oncontextmenu }, cover, h('div', {}, h('b', {}, title), h('span', {}, sub)));
  $('#libList').replaceChildren(
    item('Liked Songs', `Playlist · ${count(lib.liked.length)}`, likedTile(), view.name === 'liked', () => go('liked')),
    ...Object.entries(lib.playlists).map(([n, l]) => item(n, `Playlist · ${count(l.length)}`, listCover(n), view.name === 'playlist' && view.arg === n,
      () => go('playlist', n), e => menu(e, [['Play', () => playList(l)], ['Rename', () => renamePlaylist(n)], ['Delete', () => deletePlaylist(n)]]))));
}
function renderQueue() {
  if ($('#queuePanel').hidden) return;
  const s = current();
  const rows = device === 'tv'
    ? (tv?.queue ?? []).map(x => h('div', { class: 'row', onclick: () => tvCmd(`/jump?i=${x.at}`) },
      h('div', { class: 'who' }, img(x.i), h('div', {}, h('b', {}, x.t), h('span', {}, x.a))),
      h('button', { class: 'round', title: 'Remove', onclick: e => { e.stopPropagation(); tvCmd(`/remove?i=${x.at}`); } }, icon('close'))))
    : queue.slice(index + 1).map((x, k) => h('div', { class: 'row', onclick: () => move(index + 1 + k) },
      h('div', { class: 'who' }, img(x.i), h('div', {}, h('b', {}, x.t), h('span', {}, x.a))),
      h('button', { class: 'round', title: 'Remove', onclick: e => { e.stopPropagation(); queue.splice(index + 1 + k, 1); renderQueue(); } }, icon('close'))));
  $('#queueList').replaceChildren(
    h('h3', {}, device === 'tv' ? 'Now playing on Fire TV' : 'Now playing'),
    s ? h('div', { class: 'row on' }, h('div', { class: 'who' }, img(s.i), h('div', {}, h('b', {}, s.t), h('span', {}, s.a)))) : h('p', { class: 'empty' }, 'Nothing playing'),
    h('h3', {}, 'Next up'),
    ...(rows.length ? rows : [h('p', { class: 'empty' }, 'Autoplay adds similar songs.')]));
}

// --- Start ---
$('#npArt').onerror = () => { $('#npArt').src = 'logo.png'; };
lib = await api.load();
audio.volume = lib.volume ?? 0.8;
$('#vol').value = Math.round(audio.volume * 100);
$('#vol').style.setProperty('--p', `${$('#vol').value}%`);
go('home');
moods = await api.engine('/moods').catch(() => []);
if (view.name !== 'playlist' && view.name !== 'liked') render();
