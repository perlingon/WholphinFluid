# Nema brand files

| File | What it is |
|---|---|
| `nema-wordmark.svg` | The logo: gold gradient film n with "ema", transparent background |
| `nema-wordmark-plain.svg` | The same in gold without the film squares |
| `nema-wordmark-cream.svg` | The film version in a single light cream, for one-colour use |
| `nema-icon-n.svg` | The app icon mark: the n with a short tail, on the 108-unit adaptive icon grid |
| `nema-icon-1024.png` | The full app icon, 1024 px |
| `nema-poster.png` | The logo with its glow on the gradient background, 1920 × 1080 |
| `nema-readme-header.png` | Header image for the README |

## Colours

- Gold gradient, top left to bottom right: `#F6D98C` → `#DDA94A` → `#A86F22`, with a soft white sheen at the top of the arch
- Lettering: `#F7F2E8`
- Background: `#222222` at the top to `#000000` at the bottom, with fine film grain
- Glow: a soft bloom in the gold's own colours, kept out of the film squares

## In the app

- Launcher icon: `mipmap-*/ic_launcher_foreground.webp` (gold n with glow) on `drawable/ic_launcher_background.xml` (the gradient), with `drawable/ic_launcher_monochrome.xml` for themed icons. Legacy and round icons are in `mipmap-*/ic_launcher*.webp`
- TV banner: `mipmap-xhdpi/ic_banner.png` (320 × 180), with xxhdpi and xxxhdpi versions for 4K screens
- Play Store icon: `app/src/main/ic_launcher-playstore.png`

Typeface: [ABeeZee](https://fonts.google.com/specimen/ABeeZee) (SIL Open Font License). In the SVGs the letters are outlined, so no font is needed to open them.
