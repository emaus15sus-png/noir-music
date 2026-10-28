// Builds the Windows installer (desktop/dist/Noir Setup <version>.exe):
// the engine jar (Gradle), a trimmed Java runtime for it (jlink), adb for waking the Fire TV, then electron-builder.
// Needs JAVA_HOME pointing at a JDK 17+, and optionally the Android SDK's platform-tools (for adb).
import { execFileSync, execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const desktop = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.resolve(desktop, '..');
const toolchain = path.resolve(repo, '..', 'toolchain'); // the original author's layout; env vars win
const jdk = process.env.JAVA_HOME ?? path.join(toolchain, 'jdk', 'jdk-17.0.20.1+1');
if (!fs.existsSync(path.join(jdk, 'bin', 'jlink.exe'))) throw new Error(`Set JAVA_HOME to a JDK 17+ (tried ${jdk})`);
const env = { ...process.env, JAVA_HOME: jdk };
const out = path.join(desktop, 'dist-res');
fs.rmSync(out, { recursive: true, force: true });

console.log('> engine jar');
execSync(`"${path.join(repo, 'gradlew.bat')}" :engine:engineJar --console=plain -q`, { cwd: repo, env, stdio: 'inherit' });
fs.mkdirSync(path.join(out, 'engine'), { recursive: true });
fs.copyFileSync(path.join(desktop, 'engine', 'build', 'libs', 'engine.jar'), path.join(out, 'engine', 'engine.jar'));

console.log('> java runtime');
// From `jdeps --print-module-deps engine.jar`, plus jdk.crypto.ec for the TLS that YouTube uses
const modules = 'java.base,java.desktop,java.instrument,java.logging,java.net.http,java.scripting,jdk.dynalink,jdk.httpserver,jdk.unsupported,jdk.crypto.ec';
execFileSync(path.join(jdk, 'bin', 'jlink.exe'), ['--add-modules', modules, '--strip-debug', '--no-header-files', '--no-man-pages',
  '--compress=2', '--output', path.join(out, 'runtime')], { stdio: 'inherit' });
// jlink writes unsigned copies of java.exe and its DLLs, and Windows Smart App Control blocks unknown unsigned
// programs. The JDK's own copies are the same build but signed, so use those.
for (const f of fs.readdirSync(path.join(out, 'runtime', 'bin'), { recursive: true })) {
  const signed = path.join(jdk, 'bin', f);
  if (/\.(exe|dll)$/i.test(f) && fs.existsSync(signed)) fs.copyFileSync(signed, path.join(out, 'runtime', 'bin', f));
}

console.log('> adb');
const tools = [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT, path.join(process.env.LOCALAPPDATA ?? '', 'Android', 'Sdk'), path.join(toolchain, 'sdk')]
  .filter(Boolean).map(p => path.join(p, 'platform-tools')).find(p => fs.existsSync(path.join(p, 'adb.exe')));
if (tools) {
  fs.mkdirSync(path.join(out, 'adb'));
  for (const f of ['adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll']) fs.copyFileSync(path.join(tools, f), path.join(out, 'adb', f));
} else console.warn('  adb not found: the app will work, but can\'t wake the Fire TV by itself');

console.log('> installer');
execSync('npx electron-builder --win nsis', { cwd: desktop, stdio: 'inherit' });
