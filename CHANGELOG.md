# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.2.0] - 2026-07-24

### Added

- Playback resumption after process death: media-button and system media-resumption
  starts now replay the last played playlist station (`onPlaybackResumption` +
  persisted last-played id). Discover-only stations deliberately do not resume.
- Release pipeline guards: the release workflow refuses a tag that does not match
  `versionName` (before any build work or keystore decode), and CI now builds
  `main` after every merge.

### Fixed

- Discover: the "radio-browser unreachable" banner now clears once both browse
  lists load after a retry, and the Tags/Countries/Top selection survives
  back-navigation (Top no longer auto-reopens its list when restored).

## [0.1.0] - 2026-07-23

### Added

- Watched-playlist player: SAF folder onboarding, hot reload (foreground rescan +
  pull-to-refresh), six playlists with sticky group headers, letter-tile logos.
- Media3 playback with quirk-aware data sources: playlist indirection (.pls/.m3u)
  resolved at play time, HLS, cross-protocol redirect self-heal, shoutcast ICY
  tolerance, ICY now-playing metadata, single retry per item plus auto-retry on
  connectivity return (foreground-reliable).
- Discover: radio-browser tags/countries/top/search browse trees with votes
  ordering, infinite scroll, tap-to-play, mirror failover.
- Favorites round-trip: star ↔ pending.csv (pyradio-compatible schema), crash-safe
  atomic writes with orphaned-backup recovery, refresh on desktop consumption.
- Android Auto browse tree (playlists → groups → stations).
- Catppuccin Latte/Frappé theme, adaptive launcher icon.
- Tag-triggered signed-release workflow for Obtainium distribution.
