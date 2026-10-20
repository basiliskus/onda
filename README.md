# Onda

Onda is an Android radio player: a lighter, purpose-fit replacement
for Transistor. It watches a folder of M3U playlists — any folder the
phone can open, typically one kept in sync with a desktop — renders
them as real groups, lets you browse and favorite stations from
radio-browser.info, and plays through Media3 for background playback,
notification controls, and Android Auto.

## Screenshots

_TODO: add screenshots once the UI settles._

## Install

Onda isn't on the Play Store. Install it via
[Obtainium](https://github.com/ImranR98/Obtainium):

1. Add app → paste `https://github.com/basiliskus/onda`.
2. Obtainium tracks this repo's [releases](https://github.com/basiliskus/onda/releases)
   and offers updates as new tags ship.

## Quick start

1. Put one or more `.m3u` playlists in a folder on the phone, or in a
   folder your sync tool (Syncthing, Nextcloud, Synology Drive, …)
   mirrors to it. The playlists in
   [`core/src/test/resources/playlists/`](core/src/test/resources/playlists/)
   work as a starting set.
2. Open Onda and choose that folder. Every `*.m3u` file in it becomes a
   playlist; edits on the desktop hot-reload on the phone.

## Data contract

Onda doesn't own the playlists it renders — it reads `*.m3u` files from
the folder you pick and hot-reloads on change; there's no in-app
import. Edits (star, targeted add, delete, move between groups or
playlists) append ops to an `ops.jsonl` journal in that folder for a
desktop consumer to merge, rather than mutating the M3Us directly.
Liking a track appends one line to `recordings/onda-titles.log` there,
in the same format pyradio writes. Those three — playlists in, ops
journal out, titles log appended — are everything the app touches in
that folder. File formats and invariants:
[`docs/data-contract.md`](docs/data-contract.md); stream-quirk
knowledge: `docs/spec.md`.

## Maintainer's setup

The maintainer's playlists are pyradio CSVs in a private data repo,
exported to `~/Sync/Radio/*.m3u` and synced to the phone by Synology
Drive. A launchd agent merges `ops.jsonl` back into the CSVs, and a
desktop harvester ingests the titles log.

## Contributing

This is a personal project fitted to one user's workflow and isn't
accepting contributions.
