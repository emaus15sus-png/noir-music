// Noir for PC: the window, the YouTube Music engine (Kotlin, shared with the TV app), your library on disk,
// and the Fire TV as a Spotify Connect-style device.
import { app, BrowserWindow, ipcMain, shell } from 'electron';
import { spawn, execFile } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
// Packaged: everything sits in resources/. From source: the Gradle build output and the repo's toolchain.
const res = app.isPackaged ? process.resourcesPath : null;
const toolchain = path.join(here, '..', '..', 'toolchain');
const java = res ? path.join(res, 'runtime', 'bin', 'java.exe')
  : [process.env.NOIR_JAVA, path.join(toolchain, 'jdk', 'jdk-17.0.20.1+1', 'bin', 'java.exe')].find(p => p && fs.existsSync(p)) ?? 'java';
const jar = res ? path.join(res, 'engine', 'engine.jar') : path.join(here, 'engine', 'build', 'libs', 'engine.jar');
const adb = res ? path.join(res, 'adb', 'adb.exe') : path.join(toolchain, 'sdk', 'platform-tools', 'adb.exe');

app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required'); // next song starts on its own
app.setAppUserModelId('com.noir.desktop'); // own taskbar identity and icon, though the exe is Electron's, unmodified
if (!app.requestSingleInstanceLock()) app.quit();

// --- Engine: started once, talked to over 127.0.0.1 with a per-run secret ---
const token = randomBytes(24).toString('hex');
let engineProc;
const enginePort = new Promise((resolve, reject) => {
  engineProc = spawn(java, ['-Xmx256m', '-jar', jar], { env: { ...process.env, NOIR_TOKEN: token }, stdio: ['pipe', 'pipe', 'inherit'], windowsHide: true });
  engineProc.on('error', reject);
  engineProc.on('exit', code => reject(new Error(`engine stopped (${code})`)));
  let out = '';
  engineProc.stdout.on('data', d => { out += d; const m = out.match(/PORT (\d+)/); if (m) resolve(Number(m[1])); });
});

ipcMain.handle('engine', async (_e, route, params = {}) => {
  if (!/^\/[a-z]+$/.test(route)) throw new Error('bad route');
  const res = await fetch(`http://127.0.0.1:${await enginePort}${route}?${new URLSearchParams(params)}`, { headers: { 'X-Token': token } });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
});

// --- Library: one JSON file, written atomically so a crash mid-save can't corrupt it ---
const libFile = path.join(app.getPath('userData'), 'library.json');
const KEYS = ['liked', 'playlists', 'history', 'scores', 'picks', 'skips', 'volume', 'tvIp'];
const empty = { liked: [], playlists: {}, history: [], scores: {}, picks: {}, skips: {}, volume: 0.8, tvIp: null };
let lib = { ...empty };
try { lib = { ...empty, ...JSON.parse(fs.readFileSync(libFile, 'utf8')) }; } catch (e) {
  if (fs.existsSync(libFile)) fs.copyFileSync(libFile, `${libFile}.broken-${Date.now()}`); // keep a damaged file instead of overwriting it
}
let saveTimer;
function writeLib() {
  fs.mkdirSync(path.dirname(libFile), { recursive: true });
  fs.writeFileSync(`${libFile}.tmp`, JSON.stringify(lib));
  fs.renameSync(`${libFile}.tmp`, libFile);
}
function persist() { clearTimeout(saveTimer); saveTimer = setTimeout(writeLib, 300); }
ipcMain.handle('store:load', () => lib);
ipcMain.handle('store:save', (_e, key, value) => {
  if (!KEYS.includes(key)) throw new Error('bad key');
  lib[key] = value;
  persist();
});

// --- Fire TV: Noir's remote API on port 8686 (see app/.../Remote.kt) ---
const TV_PORT = 8686;
async function hello(ip) {
  try { return (await (await fetch(`http://${ip}:${TV_PORT}/hello`, { signal: AbortSignal.timeout(1500) })).json()).app === 'noir'; } catch { return false; }
}
function portOpen(ip, port, ms = 700) {
  return new Promise(done => {
    const s = net.connect({ host: ip, port });
    const end = ok => { s.destroy(); done(ok); };
    s.setTimeout(ms, () => end(false)).once('connect', () => end(true)).once('error', () => end(false));
  });
}
/** Addresses on this PC's /24 networks with [port] open. */
async function scan(port) {
  const nets = [...new Set(Object.values(os.networkInterfaces()).flat()
    .filter(a => a.family === 'IPv4' && !a.internal && !a.address.startsWith('169.254.'))
    .map(a => a.address.replace(/\.\d+$/, '')))];
  const ips = nets.flatMap(n => Array.from({ length: 254 }, (_, i) => `${n}.${i + 1}`));
  return (await Promise.all(ips.map(async ip => (await portOpen(ip, port)) && ip))).filter(Boolean);
}
function remember(ip) { lib.tvIp = ip; persist(); return ip; }
/** The saved TV if Noir answers there, else look around the network. */
async function findTv() {
  if (lib.tvIp && await hello(lib.tvIp)) return lib.tvIp;
  for (const ip of await scan(TV_PORT)) if (await hello(ip)) return remember(ip);
  return null;
}
/**
 * Noir's remote only runs while Noir is open or playing: wake the TV and start Noir over ADB (the debugging link
 * used to install the app). Without a saved TV, any device with ADB open that has Noir installed is the one.
 */
async function wakeTv() {
  if (!fs.existsSync(adb)) return false;
  const run = args => new Promise(done => execFile(adb, args, { windowsHide: true, timeout: 15000 }, (_e, out) => done(String(out ?? ''))));
  for (const ip of lib.tvIp ? [lib.tvIp] : await scan(5555)) {
    if (!await portOpen(ip, 5555, 1000)) continue;
    await run(['connect', `${ip}:5555`]);
    if (!(await run(['-s', `${ip}:5555`, 'shell', 'pm path com.noir.music'])).includes('package:')) continue;
    await run(['-s', `${ip}:5555`, 'shell', 'input keyevent KEYCODE_WAKEUP; am start -n com.noir.music/.MainActivity']);
    for (let i = 0; i < 20; i++) { if (await hello(ip)) return !!remember(ip); await new Promise(r => setTimeout(r, 500)); }
  }
  return false;
}
ipcMain.handle('tv', async (_e, action, route) => {
  if (action === 'find') return findTv();
  if (action === 'wake') return wakeTv();
  if (!lib.tvIp || !/^\/[a-z]+(\?[^#\s]*)?$/.test(route)) throw new Error('bad request');
  const post = action === 'post';
  const res = await fetch(`http://${lib.tvIp}:${TV_PORT}${route}`, {
    method: post ? 'POST' : 'GET', headers: post ? { 'X-Noir': '1' } : {}, signal: AbortSignal.timeout(route.startsWith('/mood') ? 60000 : 5000),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
});

// --- Window ---
function createWindow() {
  const win = new BrowserWindow({
    width: 1320, height: 840, minWidth: 980, minHeight: 640, backgroundColor: '#000000', title: 'Noir',
    icon: path.join(here, 'build', 'icon.ico'), show: false,
    titleBarStyle: 'hidden', titleBarOverlay: { color: '#00000000', symbolColor: '#b3b3b3', height: 40 },
    webPreferences: { preload: path.join(here, 'preload.cjs'), contextIsolation: true, sandbox: true },
  });
  win.removeMenu();
  win.once('ready-to-show', () => win.show());
  // The UI never navigates; outside links (none today) would open in the real browser
  win.webContents.setWindowOpenHandler(({ url }) => { if (url.startsWith('https://')) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (url !== win.webContents.getURL()) e.preventDefault(); }); // reload is fine
  win.loadFile(path.join(here, 'ui', 'index.html'));
  return win;
}

app.whenReady().then(() => {
  const win = createWindow();
  app.on('second-instance', () => { if (win.isMinimized()) win.restore(); win.focus(); });
});
app.on('window-all-closed', () => app.quit());
// Flush a save that is still waiting on its 300 ms timer, then stop the engine
app.on('will-quit', () => { clearTimeout(saveTimer); writeLib(); engineProc?.kill(); });
