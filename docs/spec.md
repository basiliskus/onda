# Onda — product spec and prior research

Migrated from the dotfiles wishlist (researched 2026-07-22/23 during
the TuneIn → pyradio migration). This is the input for the
superpowers:brainstorming design phase, not its output — the design
sessions may refine anything here except the "decisions already made"
in CLAUDE.md.

## Why this app exists

Tried and rejected:

- **TuneIn**: account closed; exported everything.
- **Transistor** (the closest fit): imports M3U only via file-open
  intents that silently fail when the file manager sends generic MIME
  (`application/octet-stream`); Settings→Restore is backup-ZIP-only;
  one flat station collection — six playlists merge into a 200-station
  scroll and `group-title` is ignored. Confirmed 2026-07-23.
- **RadioDroid / Open Radio**: dated UI; Open Radio additionally has no
  playlist import at all (station-by-station manual entry).

## The product

1. **Watched playlists, no importing.** Reads every `.m3u` in the
   Synology-Drive-synced folder (SAF document tree the user picks
   once), hot-reloads on change, renders each playlist with its groups
   as real sections. See CLAUDE.md "Data contract".
2. **radio-browser.info discovery.** Browse by tag/country/votes trees
   (not just search — this is what RadioDroid has and Transistor
   lacks). The API is friendly JSON, no auth
   (`de1.api.radio-browser.info/json/stations/search?...` — probed and
   used extensively during research; also `order=votes&reverse=true` is
   the quality filter that works).
3. **Playlist editing round-trip.** Phone-side edits (star/targeted
   add, swipe-delete, move between groups, move between playlists)
   append identity-anchored ops
   to `ops.jsonl` in the synced folder; playlists render with the
   pending ops applied as an optimistic overlay (pending rows italic)
   until the desktop merge script consumes the journal into
   stations.csv (the source of truth) and re-exports the M3Us — the app
   never mutates the M3Us. The phone owns the journal (compacts its own
   unconsumed entries; undo removes the op); the desktop's only verbs
   are consume-everything-and-clear or don't-touch. Full contract:
   `docs/superpowers/specs/2026-07-23-ops-journal-design.md`.
4. **Song capture.** A like button (Now Playing sheet + media notification)
   appends the current ICY track title to a titles log in the same
   synced folder, for the desktop music harvester to ingest. Format
   contract below.
5. **Android Auto** via Media3 `MediaLibraryService` — near-free.
6. Catppuccin Frappé, dark/light.

## Synced-folder contract

The app touches three kinds of file in the SAF-granted synced folder,
and nothing else:

| Path | Access | Owner |
|------|--------|-------|
| `*.m3u` (six playlists) | read | desktop (`stations-to-m3u.py`) |
| `ops.jsonl` | read/write | phone (desktop consumes-and-clears) |
| `recordings/onda-titles.log` | append-only | phone |

`recordings/` also holds pyradio's own `pyradio-titles.log`. **Onda
never writes it.** One writer per file is what keeps Synology Drive
from producing conflict copies (`recordings/` already carries one for
pyradio's log, from two desktops writing it). The seam enforces this
structurally: `PlaylistsSource.appendTitlesLogLine(line)` takes no
filename, so no code path in the app can name another file to append
to. The subdirectory and the log file are created on first like.

### Cross-playlist moves

Moving a station into another playlist emits a `Delete` for the source
playlist and a targeted `Add` for the target one, from a **single**
journal write: one compaction pass, one generation bump, one undo
token, and a failed write rolls the whole pair back. Undo restores the
pre-move journal snapshot, so it removes both ops regardless of what
compaction did to them. The two ops address different CSVs, so their
order carries no meaning — compaction may separate or reorder them, and
no code may assume they stay adjacent. A target *inside* the station's
current playlist routes to a single `Move` op instead.

No new op type and no journal-contract change; the desktop
`stations-merge-ops.py` applies the pair unmodified. Verified live
against the real script and a copy of the six CSVs: 2 applied, 0
rejected, no `ops-rejected.jsonl`.

**Known loss.** The desktop applies a `Move` by re-inserting the
station's CSV line verbatim, but applies an `Add` by rebuilding the row
from name, url and icon. So a cross-playlist move blanks the `encoding`
column (which `format_row` always emits empty) and drops any pyradio
column past `icon`, and appends the row at the end of its target group
rather than a curated position — as does an A→B→A round trip, which
nets to a delete plus a re-add in A. One live row is affected today:
`KALX 90.7FM Berkeley` in `public-radio.csv`, which carries
`volume=100`. This is also why a same-playlist target routes to a
`Move` and never to the pair. Fixing it means teaching the desktop to
carry the old line when a delete and an add for one url arrive in the
same run; deliberately not attempted on the phone side.

### Titles-log line format

```
Jul 23 (Thu) 21:08 |     Artist - Title (LIKED)
```

Pinned byte-for-byte by the desktop consumer,
`music/scripts/radio-titles-to-json.py`, which globs the whole log
directory, splits each line on `" | "`, and keeps messages ending in
`" (LIKED)"`:

- Timestamp `MMM dd (EEE) HH:mm`, **English locale forced** for the
  month and weekday names (`MMM`, `EEE`) — the desktop parses with `%b
  %d (%a) %H:%M` regardless of the phone's locale. The locale does
  *not* affect the digits: `DateTimeFormatter.ofPattern` always uses
  `DecimalStyle.STANDARD`, so `HH:mm` is ASCII with or without it, and
  only an explicit `withDecimalStyle` could change that. Zero-padded
  day, to match pyradio's own lines byte-for-byte.
- **No year.** The consumer infers it from the file's mtime, so a log
  that is never rotated will mis-year entries older than a year;
  rotation belongs to the desktop side.
- Separator `" | "`, then **four more spaces** of liked-line indent —
  five spaces between the pipe and the title.
- The title passes through **raw** (the consumer strips it), and the
  line ends at `" (LIKED)"` with nothing after it.
- Local wall-clock time, as pyradio writes it.
- When the playing item names a station, the liked line is preceded
  by a `>>> Station: <name>` line in pyradio's shape (same timestamp,
  no liked-line indent — the indent is the title marker), written
  together as ONE append so the pair cannot
  tear. The consumer carries the station forward by file order, so each
  like is attributable on its own with no writer-side state; a null or
  blank station degrades to the liked line alone, the pre-v0.5.1 shape.
  (Until v0.5.1 Onda emitted no station lines and its likes landed
  without a `station` field — GitHub #51 records why that stopped being
  tolerable once the consumer's station-aware pass shipped.)

**Line-boundary neutralization.** An ICY `StreamTitle` — and the
station name, which reaches the same log — is untrusted remote input
landing in a file a desktop parser reads, so before formatting, every
character Python's `str.splitlines()` treats as a line boundary (LF,
VT, FF, CR, FS, GS, RS, NEL, U+2028, U+2029 — all reachable through
Media3's DOTALL `IcyDecoder`) is replaced with a space. Without it a
crafted title splits into several records, each
still able to satisfy `endswith(" (LIKED)")` or `startswith("Station:
")`, forging entries and dropping the genuine one. Framing only:
nothing is trimmed, decoded, or parsed.

## Stream-quirk knowledge (hard-won, bake into tests)

The user's 205 stations already span every awkward format:

- Playlist-indirection URLs: `.pls` and `.m3u` files that point at the
  real stream (somafm-style, kzsu, streamtheworld).
- HLS: BBC (akamaized `.m3u8`; per-station pool numbers that rotate —
  re-discover via radio-browser top-voted entries), KQED, iheart.
- Redirect services that must be followed at play time and are
  *better* kept unresolved: radio.garden listen redirects
  (`radio.garden/api/ara/content/listen/<id>/channel.mp3` — self-heal
  as stations move), RadioKing `/play/` URLs, `radioeins.de/livemp3`.
- Shoutcast v1 servers answering `ICY 200 OK` (breaks strict HTTP
  clients; the data repo's `radio/scripts/stations-check.py` treats it
  as alive).
- Plain icecast MP3/AAC (majority).

The data repo's radio tools (`radio/scripts/stations-to-m3u.py`,
`radio/scripts/stations-check.py` — moved from dotfiles by dotfiles#35)
are the reference implementations for parsing and probing these.

### Learned during MVP build (2026-07-23)

- FIP/Radio France endpoints (`icecast.radiofrance.fr/*`) send no
  `icy-metaint` — no ICY titles are available from them.
- The app manifest sets blanket `android:usesCleartextTraffic="true"`
  because plain-HTTP icecast is common across the station set
  (user-approved tradeoff).
- Connectivity auto-retry is foreground-reliable only: after a
  playback error Android 12+ blocks background FGS re-promotion and
  the process may freeze; accepted MVP limitation (2026-07-23).

## Sizing (from research)

Transistor is ~10k LOC Kotlin (44 files), mostly stream-quirk
hardening accumulated over years. Onda's target is ~3–5k: Media3
absorbs the service/notification/Auto layer; the killer features
(watched folder, groups, radio-browser trees) are each a few hundred
lines. Agent-driven MVP playing all six playlists ≈ a weekend;
daily-driver polish ≈ 2–3 weekends; the long tail is stream quirks and
yearly SDK-target bumps — which is why the OWNERSHIP phase runs on
pipeline-skills with retro→canon compounding.

## Name

"Onda" — Spanish for *wave* (what radio is) and colloquially *vibe*
(what the app curates). Package id: `dev.basiliskus.onda`.
