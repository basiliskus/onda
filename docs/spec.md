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
   add, swipe-delete, move-between-groups) append identity-anchored ops
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

### Titles-log line format

```
Jul 23 (Wed) 21:08 |     Artist - Title (LIKED)
```

Pinned byte-for-byte by the desktop consumer,
`music/scripts/pyradio-to-json.py`, which globs the whole log
directory, splits each line on `" | "`, and keeps messages ending in
`" (LIKED)"`:

- Timestamp `MMM dd (EEE) HH:mm`, **English locale forced** (month and
  weekday names, and ASCII digits) — the desktop parses with `%b %d
  (%a) %H:%M` regardless of the phone's locale. Zero-padded day, to
  match pyradio's own lines byte-for-byte.
- **No year.** The consumer infers it from the file's mtime, so a log
  that is never rotated will mis-year entries older than a year;
  rotation belongs to the desktop side.
- Separator `" | "`, then **four more spaces** of liked-line indent —
  five spaces between the pipe and the title.
- The title passes through **raw** (the consumer strips it), and the
  line ends at `" (LIKED)"` with nothing after it.
- Local wall-clock time, as pyradio writes it.
- Onda emits no `Station: <name>` lines, so Onda-liked tracks land
  without a `station` field. The consumer already tolerates that.

**Line-boundary neutralization.** An ICY `StreamTitle` is untrusted
remote input landing in a file a desktop parser reads, so before
formatting, every character Python's `str.splitlines()` treats as a
line boundary (LF, VT, FF, CR, FS, GS, RS, NEL, U+2028, U+2029 — all
reachable through Media3's DOTALL `IcyDecoder`) is replaced with a
space. Without it a crafted title splits into several records, each
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
  clients; the dotfiles `stations-check.py` treats it as alive).
- Plain icecast MP3/AAC (majority).

The dotfiles tools (`stations-to-m3u.py`, `stations-check.py`) are the
reference implementations for parsing and probing these.

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
