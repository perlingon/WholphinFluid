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

Labels describe what an issue is about. Releases are tracked with milestones (Alpha (Proof of concept), First official release, Future release), not labels.

- design: layout, visuals, typography, icons
- Video playback: video player, scaling, subtitles
- widgets: home screen panels, Google TV rows, Streamystats
- performance: speed and smoothness
- maintenance: cleanup, dependencies
- documentation: docs, README, release notes
- enhancement: a new capability
- needs-research: investigate before building
- accessibility
- bug: something that is broken
- crazy wishlist thinking: far-out ideas, not planned for any release

## Suggested workflow

1. New idea or issue goes to Backlog. Tickets that don't belong to the current release are parked in Future release wishlist.
2. When it is confirmed and scoped, move it to Planned.
3. When someone starts work, move it to In Progress.
4. When a PR is open, move it to Review.
5. When merged or otherwise completed, move it to Done.

## Prioritization

Priority follows the milestones: Alpha (Proof of concept) first, then First official release. Tickets that don't belong to the current release go to the Future release milestone and the Future release wishlist column.

## Review checklist

Before marking work as Done:

- code is reviewed and merged
- tests or validation are documented
- documentation updates are included when needed
- related issues are linked

## Notes

This board keeps planning simple and transparent. It is designed to fit a contributor-driven Android TV app without needing a heavy project process.
