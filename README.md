# Biscuit Player

Biscuit Player is an open-source Android media player powered by libmpv, with a Jetpack Compose interface, customizable controls, and support for local and network media.

[Repository](https://github.com/jerry090900/Biscuit-player) · [Downloads](https://github.com/jerry090900/Biscuit-player/releases)

Biscuit Player screenshots and replacement artwork are not available yet. Inherited upstream screenshots are omitted from this README.

## Features

- Advanced video playback powered by libmpv, with customizable gestures, frame navigation, zoom, and pan.
- Browse local files, organize playlists, track watched videos, and enjoy Shorts mode.
- Stream media from SMB, WebDAV, FTP, direct URLs, and supported web platforms using the optional REX Ytdlp companion app.
- Customize the player with glass-themed controls, dynamic tabs, subtitle settings, and HDR-to-SDR tone mapping.
- Connect to Jellyfin and share files with nearby devices over your local network.

---

## Installation

Visit [Biscuit Player releases](https://github.com/jerry090900/Biscuit-player/releases) for published downloads when available. A release is not being published as part of the initial rebrand.

Developer build artifacts, when uploaded, can be downloaded from the repository's [GitHub Actions](https://github.com/jerry090900/Biscuit-player/actions) runs. Debug APKs are intended for testing.

---

## Translations

Translations can be managed using **[Droidlate](https://github.com/estiaksoyeb/Droidlate)** ([PyPI](https://pypi.org/project/droidlate/)) — a local, web-based UI designed for editing Android `strings.xml` translation files.

If you would like to contribute to translating Biscuit Player into your language, please refer to the [Translation Contribution Guide](CONTRIBUTING.md#translation-contributions) for step-by-step instructions on running Droidlate locally.

---

## Credits / Upstream

Biscuit Player is based on **[REX Player](https://github.com/mpvRex/REX-Player)**, which has its roots in **[mpvEx](https://github.com/marlboro-advance/mpvEx)**, which itself builds on **[mpv-android](https://github.com/mpv-android/mpv-android)**. We're grateful for the foundation they laid.

Additional inspiration and reference:
[mpvKt](https://github.com/abdallahmehiz/mpvKt) · [Next Player](https://github.com/anilbeesetti/nextplayer) · [Gramophone](https://github.com/FoedusProgramme/Gramophone)

---

## License

Distributed under the **Apache License 2.0**. See `LICENSE` for details.

Bundled screenshots and icons are inherited upstream assets pending replacement. Native mpv/FFmpeg and third-party components retain their respective licenses and notices.
