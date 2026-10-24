<p align="center">
<img alt="Phin" src="branding/phin-readme-header.png" width="800"/>
</p>

# Phin

Phin is a Jellyfin client for Android TV. It is a fork of [Wholphin](https://github.com/damontecres/Wholphin), the open-source Android TV client by damontecres, tuned for a calmer, more cinematic living-room experience.

The name joins three things: the end of *Wholphin*, the *fin* in Jellyfin, and the *Fin* that closed classic films.

> Phin was previously called Nema, and WholphinFluid before that. The repository URL still uses the oldest name.

## What's different from Wholphin

- **Themes:** four themes (Vivid, Muted, Dark and Darkest) plus a separate highlight colour for focus and accents, with gold as the default
- **Typography:** ABeeZee for the interface and for subtitles
- **Subtitles:** settings to ignore embedded subtitle colours and styling
- **Video adjust:** Kodi-style zoom, pixel ratio and position controls during playback, for ultrawide screens and odd aspect ratios
- **Player:** ExoPlayer only; the MPV option is hidden
- **Backdrops:** a blurred backdrop with colour glows and film grain behind the home screen
- **Installs next to Wholphin:** Phin has its own app ID, so both can be installed on the same box

Work in progress is tracked in the [roadmap](ROADMAP.md).

## Installation

Phin has no store listing yet. Build it from source with the release build type, see the [developer's guide](DEVELOPMENT.md). The release build installs as **Phin** alongside Wholphin; the `fast` build type installs as **Phin Fast** for quick testing on a device.

For Wholphin itself, see the [Wholphin releases](https://github.com/damontecres/Wholphin/releases/latest).

## Compatibility

Requires Android 6+ (or Fire TV OS 6+) and Jellyfin server `10.10.x` or `10.11.x`, as upstream Wholphin.

## Brand

The wordmark is set in a high-contrast italic serif based on Bodoni Moda, with a play triangle in place of the dot on the i. The small mark is the P followed by the same triangle. Everything is a single neutral white on dark, so the logo never competes with the accent colour you pick in the app.

| | |
|---|---|
| Logo | `#F2F2F2`, with a soft glow |
| Icon background | `#333333` → `#050505` |
| Posters and banners | Concentric end-card rings on near-black |
| Logo typeface | [Bodoni Moda](https://fonts.google.com/specimen/Bodoni+Moda), outlined |
| App typeface | [ABeeZee](https://fonts.google.com/specimen/ABeeZee) |

Logo files, the icon, plates and posters are in [`branding/`](branding).

## Contributions

Issues and pull requests are welcome. Please read the [contributing guide](CONTRIBUTING.md), which comes from upstream Wholphin and applies here too. Fixes that are not specific to Phin are best sent to [Wholphin](https://github.com/damontecres/Wholphin) first.

## Acknowledgements

- Thanks to damontecres and the Wholphin contributors. Phin is built on their work, and nearly everything good in it starts there
- Thanks to the Jellyfin team for creating and maintaining such a great open-source media server
- Thanks to the official Jellyfin Android TV client developers, some code for creating the device direct play profile is adapted from there
- Thanks to the Jellyfin Kotlin SDK developers for making it easier to interact with the Jellyfin server API
- Thanks to numerous other libraries that make app development even possible

## License

Phin is licensed under the GNU General Public License v2, as Wholphin is. See [LICENSE](LICENSE).
