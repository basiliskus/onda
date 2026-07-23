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
3. **Favorites round-trip.** Favoriting in-app writes a pending file
   into the synced folder for the desktop side to merge into
   stations.csv (the source of truth) — the app never mutates the M3Us.
4. **Android Auto** via Media3 `MediaLibraryService` — near-free.
5. Catppuccin Frappé, dark/light.

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
