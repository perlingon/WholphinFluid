# WholphinFluid project board guide

## GitHub project structure

Use a lightweight project board with the following columns:

- Future release wishlist: parked tickets that are not part of the current release
- Backlog
- Planned
- In Progress
- Review
- Done

## Issue labels

Every issue gets one or more **area** labels and one **phase** label, so the board can be filtered on both.

Area:

- design: layout, visuals, typography, icons
- playback: video player, scaling, subtitles
- widgets: home screen panels, Google TV rows, Streamystats
- performance: speed and smoothness
- maintenance: docs, cleanup, dependencies
- feature: an entirely new capability
- needs-research: investigate before building
- bug: something that is broken

Phase (the order doubles as priority):

- fas-1: clean up
- fas-2: playback
- fas-3: home screen and backdrop
- fas-4: identity
- fas-5: widgets and integrations
- next-release: not part of the current release

The phase can later move to a single-select field on the project board; labels then only describe what an issue is about.

## Suggested workflow

1. New idea or issue goes to Backlog. Tickets that don't belong to the current release are parked in Future release wishlist.
2. When it is confirmed and scoped, move it to Planned.
3. When someone starts work, move it to In Progress.
4. When a PR is open, move it to Review.
5. When merged or otherwise completed, move it to Done.

## Prioritization

Priority follows the phase labels: finish fas-1 before fas-2, and so on. Tickets that don't belong to the current release are parked in Future release wishlist.

## Review checklist

Before marking work as Done:

- code is reviewed and merged
- tests or validation are documented
- documentation updates are included when needed
- related issues are linked

## Notes

This board keeps planning simple and transparent. It is designed to fit a contributor-driven Android TV app without needing a heavy project process.
