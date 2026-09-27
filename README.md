# Onda

Onda is a personal Android radio player: a lighter, purpose-fit
replacement for Transistor. It watches a Synology-Drive-synced folder
of M3U playlists, renders them as real groups, lets you browse and
favorite stations from radio-browser.info, and plays through Media3
for background playback, notification controls, and Android Auto.

## Screenshots

_TODO: add screenshots once the UI settles._

## Install

Onda isn't on the Play Store. Install it via
[Obtainium](https://github.com/ImranR98/Obtainium):

1. Add app → paste `https://github.com/basiliskus/onda`.
2. Obtainium tracks this repo's [releases](https://github.com/basiliskus/onda/releases)
   and offers updates as new tags ship.

## Data contract

Onda doesn't own the playlists it renders — it reads M3U files
(`stations`, `nts`, `fip`, `bbc`, `public-radio`, `radio-garden`) from a
folder synced by Synology Drive and hot-reloads on change; there's no
in-app import. Edits (star, targeted add, delete, move between groups
or playlists) append ops to
an `ops.jsonl` journal in that folder for the desktop side to merge,
rather than mutating the M3Us directly. Liking a track appends one
line to `recordings/onda-titles.log` there, in the same format
pyradio writes, for the desktop music harvester to pick up. Those
three — playlists in, ops journal out, titles log appended — are
everything the app touches in that folder. Full contract and
stream-quirk knowledge:
`AGENTS.md` and `docs/spec.md`.

## Contributing

This is a personal project fitted to one user's workflow and isn't
accepting contributions.
