# Phin brand files

| File | What it is |
|---|---|
| `phin-wordmark.svg` | The wordmark: italic serif with a play triangle for the dot of the i, transparent background |
| `phin-mark.svg` | The small mark: the P followed by the play triangle |
| `phin-icon-512.png` | The app icon as designed, 512 px |
| `phin-poster.png` | The wordmark on the end-card rings, 1920 × 1080 |
| `phin-poster-glow.png` | The same with the soft glow, as used in the app's banner |
| `phin-readme-header.png` | Header image for the README |
| `plate-gradient-512.png` | Background plate for icons: `#333333` at the top to `#050505` |
| `plate-rings-1920x1080.png` | Background plate for posters and banners: concentric end-card rings on near-black |

## Colours

- Logo: `#F2F2F2`, a single neutral white, so it never clashes with the accent colour picked in the app
- Icon background: `#333333` → `#050505`, top to bottom
- Glow: a soft white bloom around the logo, at two blur radii

## In the app

- Launcher icon: `mipmap-*/ic_launcher_foreground.webp` (the P mark with its glow) on `drawable/ic_launcher_background.xml` (the gradient), with `drawable/ic_launcher_monochrome.xml` for themed icons. Legacy and round icons are in `mipmap-*/ic_launcher*.webp`
- TV banner: `mipmap-xhdpi/ic_banner.png` (320 × 180), with xxhdpi and xxxhdpi versions for 4K screens, on the rings plate
- Play Store icon: `app/src/main/ic_launcher-playstore.png`

The letters in the SVGs are outlined, so no font is needed to open them. The wordmark is based on [Bodoni Moda](https://fonts.google.com/specimen/Bodoni+Moda) (SIL Open Font License).
