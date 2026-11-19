# Even Better Xcloud

**Xbox Cloud Gaming for Android handhelds, with frame generation.**

A standalone Android client for Xbox Cloud Gaming (xCloud) and Xbox Remote Play, built for
handhelds and tuned on the AYANEO Pocket FIT. It signs in with your Microsoft account, shows your
full Xbox library, and streams with a native WebRTC + Vulkan player that adds **Win-FG** or **WFG**
2× frame generation.

Package `com.banner.cloud`. Private test builds, signed with the public AOSP testkey (v1 + v2 + v3).

## Features

- **Microsoft sign-in** and your complete Xbox library — cloud games, **games you own**, and Game Pass.
- **Native player** — Google's WebRTC library with a Vulkan compositor, with **Win-FG or WFG 2×
  frame generation**. Microsoft's own web player and a local web-view player are built in as fallbacks.
- **Xbox Remote Play** — stream from your own console, not just the cloud.
- **Frame-rate and quality controls** — frame-generation mode, frame-rate target, low-latency decode,
  zero-copy picture path, and a "keep quality up" option for slower decoders.
- **Picture effects** — FXAA, CRT, NTSC and toon shaders, saved as reusable profiles.
- **Input** — on-screen touch controls, mouse & keyboard, local co-op (up to four pads), button
  remapping and A/B · X/Y swaps, and a microphone for voice chat.
- **Overlay** — an on-stream stats overlay (fps, ping, resolution, bitrate, decode time, packet loss),
  colour-coded by quality.
- **Region bypass** — present a sign-in address in a chosen country.
- **Screen fit** — fit, stretch or fill options for the handheld's display.
- **In-app updater** (from 0.1.1) — checks GitHub for a newer release and installs it in place.

## Device and title limits

Some titles are restricted by Microsoft to specific device types and **cannot be streamed to a
handheld no matter the client**. The clearest example is **GTA VI**, which Microsoft and Rockstar
limit to Xbox consoles only — Microsoft's own website refuses it on a handheld as well. Such a game
will still appear in your library if you own it, but it will not start a stream, and no setting
changes that. This is enforced on Microsoft's servers, not by this app.

## Install

Download the APK from the [latest release](../../releases/latest) and sideload it (arm64-v8a only).
The `.sha256` file next to it is the APK's checksum.

The app is signed with the **public AOSP testkey**, the same key Bannerlator's builds use. These are
private test builds, not Play Store releases, so Android will ask you to allow installs from this
source.

### Updating

From 0.1.1, the app can update itself: open **Settings → About** and tap **Check for updates**, or let
it check on launch. It downloads the newest release's APK and opens the installer. (Users on 0.1.0,
which shipped before the updater, update once by hand.)

## Building

Builds are produced by GitHub Actions (`.github/workflows/build.yml`): the Xbox backend is compiled
from source for Android, staged into the app, and the APK is assembled and signed. The result is
published as a workflow artifact and on the `apk-builds` branch.

To build locally you need the Android SDK (compileSdk 34, NDK 26 + 29, CMake 3.22.1) and a Rust
toolchain for the backend (`tools/xbox/build-android.sh`), then `./gradlew assembleRelease`.

## Project layout

- `app/src/main/java/com/banner/cloud/` — the app (Jetpack Compose UI, library, players, settings).
- `app/src/main/cpp/` — the native Vulkan compositor and frame-generation code.
- `tools/xbox/` — the recipe and patches that build the Xbox backend (the Xodus Nexus CLI) for Android.

## Credits

Most of this app is ported from **[Bannerlator](https://github.com/The412Banner/Bannerlator)**
(GPL-3.0), whose Xbox store in turn came from **[DroidDeck](https://github.com/Droid-Deck/DroidDeck)**
(GPL-3.0). With thanks to the projects and authors it builds on:

- **Xbox backend** — [Xodus](https://github.com/xodus-gaming/xodus) (GPL-3.0), via the Xodus Nexus fork
  [domsvoboda/nexus-backend](https://github.com/domsvoboda/nexus-backend), built for Android with
  DroidDeck's and Bannerlator's patches.
- **[Better xCloud](https://github.com/redphx/better-xcloud)** by redphx (MIT) — the cloud game-list
  ids and the region-bypass server list (`src/enums/bypass-servers.ts`).
- **Greenlight's player** © UnknownSKL (Jim Kroon) (MIT) — the web-view player
  (`assets/xcloud/player.js`).
- **[win-fg](https://github.com/The412Banner/win-fg)** (MIT, with the AMD FidelityFX optical-flow
  notice) and **WFG**, the WinNative/Winlator Frame Generator by qwertypower / DEVAR Entertainment LLC
  (MIT) — frame generation.
- **Google WebRTC** (`io.github.webrtc-sdk:android`, BSD) — the native player's transport.
- **DXVK's DXBC translator** (zlib) — used by the renderer's LSFG engine.

Request and session shapes follow Microsoft's xCloud web player and the MIT-licensed
`xbox-xcloud-player` project. Each bundled component keeps its own licence and copyright notice in the
source tree.

## Licence

GPL-3.0. See [LICENSE](LICENSE).
