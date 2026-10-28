# Noir Music (Fire TV)

Ad-free music player for Fire TV. The catalogue is YouTube Music (via NewPipeExtractor), black UI, autoplay radio and on-device recommendations.

- **Search** uses YouTube Music's "Songs" filter, so you get official tracks only and no fan uploads. Titles with remix, slowed/reverb, sped up, nightcore, lofi, 8D, mashup, cover, lyrics and similar words are dropped, unless you type that word in your search.
- **Autoplay:** when only 2 songs are left in the queue, the app adds 10 more from the current song's YouTube Music radio.
- **Library:** Liked songs plus your own playlists. Choose "New playlist" there, or use "Add to playlist" from any song's menu. To delete a playlist, open its menu in the Library.
- **Song menu (like Spotify's "…"):** hold OK or press ☰ on any song for Play next, Add to queue, Add to playlist, and Save to / Remove from Liked songs.
- **Queue:** in the full player, the queue button opens "Up next". Pick a song to jump to it. Hold OK or press ☰ on a song to move it up, move it down or remove it. The + button adds the current song to a playlist.
- **Mood DJ:** on Home, "How are you feeling?" takes what you type, or say with the Fire TV keyboard's mic. It reads a mood from it (English and Hinglish words, including "not happy" and "khush nahi"), builds a mix from your favourite artists in that mood, saves it to your Library and plays it. "Picked for your morning/evening…" guesses your mood from the time of day and learns from the moods you choose at that time. The Moods row plays a mix instantly. It all runs on the TV, with no cloud AI and no account.
- **Fast start:** a song's stream is looked up as soon as focus rests on it, and the next song in the queue is looked up ahead of time, so OK starts playback in well under a second. The player starts after 0.5 s of audio instead of 2.5 s.
- **Now Playing:** any new queue, started on the TV or from the PC, opens a full-screen player with a blurred backdrop from the cover, the clock and "Up next". The screen stays on while music plays, so no screensaver and no Fire TV sleep. Playback no longer pauses when the HDMI output changes, for example when you switch the monitor's input.
- **Remote over Wi-Fi:** the TV app serves a remote on port 8686. It has now playing, controls, seek, like, Up next (jump or remove), search and play, playlists, the Mood DJ, and "play this song from here" for Noir for PC. Any phone or PC browser can open `http://<TV-IP>:8686` while Noir is open or playing on the TV. Anyone on your Wi-Fi can use it, but other websites can't. Commands need an `X-Noir` header, and requests must address the TV by its IP, which blocks "DNS rebinding" tricks.

## Noir for PC (`desktop/`)
A Spotify-style Windows app with the same ad-free YouTube Music catalogue and Mood DJ. The Fire TV appears as a device.
- **Home:** quick picks, Mood DJ (type how you feel: it builds a mix, saves it to Your Library and plays it), the 7 mood cards, "Picked for your evening · Chill", "Made for you", "Recently played" and your playlists.
- **Search, Your Library** (Liked Songs and playlists: create, rename, delete, add and remove songs), and a **Queue** panel.
- **Player bar:** like, shuffle what's next, previous, play/pause, next, seek, volume. The space bar plays and pauses, and the keyboard's media keys and Windows' media controls work too.
- **Devices:** move what's playing to the Fire TV at the same spot (Spotify Connect-style), control it from the app, and bring it back to the PC. If Noir isn't running on the TV, **Wake TV and open Noir** starts it over ADB.
- **How it works:** the YouTube Music and Mood DJ code is the TV app's own (`Yt.kt`, `Song.kt`, `Mood.kt`), built into `desktop/engine` as a small local engine. It only listens on 127.0.0.1 and needs a per-launch secret. Your library is saved as `%APPDATA%\noir-desktop\library.json`.

Run from source: `cd desktop`, `npm install`, `node node_modules/electron/install.js`, `gradlew :engine:engineJar` (from the repo root), then `npm start`.
Build the app: `cd desktop` then `npm run dist`. It needs `JAVA_HOME` pointing at a JDK 17+, and optionally the Android SDK for adb. The ready-to-run app is in `desktop/dist/win-unpacked` (Noir.exe), and an installer is in `desktop/dist`. With Windows **Smart App Control** on, unsigned installers are blocked. The app itself still runs, because Noir.exe is Electron's own, unmodified program and the Java runtime uses the JDK's signed files. So copy `win-unpacked` to `%LOCALAPPDATA%\Programs\Noir` and make a shortcut to `Noir.exe`.
- **Top bar:** logo, a digits-only clock, and a Restart button that relaunches the app if anything gets stuck.
- **Logo:** `app/src/main/res/drawable-nodpi/` holds the icon and Fire TV banner. Regenerate them with `powershell -File tools\logo.ps1 app\src\main\res\drawable-nodpi`.
- **Recommendations:** an artist gets +1 when you finish a song, +3 when you like one, and −1 when you skip within 30 s. A song you skip twice is never suggested again. "Made for you" mixes the radios of your liked songs and recent listens, ranked by these scores.

## Build
You need JDK 17 (set `JAVA_HOME`) and the Android SDK (set `ANDROID_HOME`, or put `sdk.dir=<path>` in `local.properties`). The Gradle wrapper downloads the right Gradle version by itself.
```
gradlew assembleRelease
gradlew testReleaseUnitTest
```
On macOS or Linux, use `./gradlew`. The APK ends up in `app/build/outputs/apk/release/app-release.apk`. `YtTest.liveSearchRadioAndStream` talks to YouTube, so it needs internet.

## Install on Fire TV
1. Fire TV: Settings → My Fire TV → About → click the device name 7 times to unlock Developer options. Then turn on **ADB debugging** and **Apps from Unknown Sources**.
2. Find the TV's IP address under Settings → My Fire TV → About → Network.
3. On the PC, run `adb` from the Android SDK's `platform-tools` folder:
   ```
   adb connect <TV-IP>
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
   The first time, accept the "Allow USB debugging" prompt on the TV.

If YouTube changes something and songs stop loading, bump the `NewPipeExtractor` version in `app/build.gradle.kts` and rebuild.

## License
GPL-3.0, see [LICENSE](LICENSE). Noir uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), which is also GPL-3.0.
