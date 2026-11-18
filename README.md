# BannerCloud

Xbox Cloud Gaming for Android handhelds, with frame generation.

- Microsoft sign-in, and your Xbox library: cloud games, owned games, Game Pass.
- Native player: Google's WebRTC library plus a Vulkan compositor, with Win-FG or WFG 2× frame
  generation. A web-view player and Microsoft's own web player are there as fallbacks.

Private test builds. Signed with the public AOSP testkey (v1 + v2 + v3).

## Where the code comes from

Most of the app is ported from [Bannerlator](https://github.com/The412Banner/Bannerlator) (GPL-3.0),
which took its Xbox store from [DroidDeck](https://github.com/Droid-Deck/DroidDeck) (GPL-3.0):

- Xbox backend: [Xodus](https://github.com/xodus-gaming/xodus) (GPL-3.0) via the Xodus Nexus fork
  [domsvoboda/nexus-backend](https://github.com/domsvoboda/nexus-backend), with DroidDeck's and
  Bannerlator's patches (`tools/xbox`), built for Android.
- Web-view player: Greenlight's player, (c) UnknownSKL (Jim Kroon), MIT (`assets/xcloud/player.js`).
- Cloud game lists: Microsoft's public catalog.gamepass.com lists, ids as used by redphx's Better xCloud (MIT).
- Region bypass (Settings): the cloud sign-in presents an address in the chosen country, as Better xCloud does
  (redphx/better-xcloud, MIT; countries and addresses from its `src/enums/bypass-servers.ts`).
- Native player: Google WebRTC (`io.github.webrtc-sdk:android`, BSD).
- Frame generation: [win-fg](https://github.com/The412Banner/win-fg) (MIT, with AMD FidelityFX optical flow
  notice) and WFG, the WinNative/Winlator Frame Generator by qwertypower / DEVAR Entertainment LLC (MIT).
- DXVK's DXBC translator (zlib), used by the renderer's LSFG engine.

Licence: GPL-3.0 (see LICENSE).
