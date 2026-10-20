# WholphinFluid roadmap

Each item is a GitHub issue. Labels give the **area** (`design`, `playback`, `widgets`, `performance`, `maintenance`, `feature`, `needs-research`) and the **phase** (`fas-1` … `fas-5`, `next-release`). Phase order is priority. See [PROJECT-BOARD.md](PROJECT-BOARD.md) for how the board works.

Board: https://github.com/users/perlingon/projects/1

## Phase 1: Clean up
- [x] Commit the header and logo work (`TitleOrLogo.kt`, `CoilTrimTransformation.kt` and others)
- [x] Merge `fluid` and the "Watch later" screensaver branch into `main`
- [ ] [#1](https://github.com/perlingon/WholphinFluid/issues/1) Update and commit CLAUDE.md (the box runs 1920x804, density 260)
- [ ] [#2](https://github.com/perlingon/WholphinFluid/issues/2) Build a release APK and compare performance against regular Wholphin

## Phase 2: Playback
- [ ] [#3](https://github.com/perlingon/WholphinFluid/issues/3) Test Crop and Fill width in WholphinFluid
- [ ] [#4](https://github.com/perlingon/WholphinFluid/issues/4) Panscan knob for playback (0 = Fit, 1 = Crop)
- [ ] [#5](https://github.com/perlingon/WholphinFluid/issues/5) Subtitles: styling and on-by-default setting

## Phase 3: Home screen and backdrop
- [ ] [#6](https://github.com/perlingon/WholphinFluid/issues/6) Height of the home screen header
- [ ] [#7](https://github.com/perlingon/WholphinFluid/issues/7) Move tuned backdrop values into `FluidTheme.kt`, per theme
- [ ] [#8](https://github.com/perlingon/WholphinFluid/issues/8) Headers on other pages
- [ ] [#9](https://github.com/perlingon/WholphinFluid/issues/9) Screensaver in ultrawide

## Phase 4: Identity
- [ ] [#10](https://github.com/perlingon/WholphinFluid/issues/10) App font
- [ ] [#11](https://github.com/perlingon/WholphinFluid/issues/11) App icon

## Phase 5: Widgets and integrations
- [ ] [#12](https://github.com/perlingon/WholphinFluid/issues/12) Widgets for new titles in collections
- [ ] [#13](https://github.com/perlingon/WholphinFluid/issues/13) Streamystats in widgets, with settings
- [ ] [#14](https://github.com/perlingon/WholphinFluid/issues/14) Research rows on the Google TV home screen, shared with in-app widgets

## Next release (future release wishlist)
- [ ] [#15](https://github.com/perlingon/WholphinFluid/issues/15) Picture-in-picture: browse while a video plays
- [ ] [#16](https://github.com/perlingon/WholphinFluid/issues/16) Cast from Jellyfin on the phone
