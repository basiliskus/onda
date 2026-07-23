# Onda — MVP design

Outcome of the 2026-07-23 brainstorming session. Input: `docs/spec.md`
and the fixed decisions in `CLAUDE.md`. Scope: all four product
features in one milestone (watched playlists, radio-browser discovery,
favorites round-trip, Android Auto), plus ICY now-playing metadata and
station logos. Explicitly out: sleep timer, rendering pending
favorites in-app.

## 1. Architecture

Two Gradle modules, manual constructor DI from a composition root
(`OndaApp`, ~20 lines). No Hilt, no feature-split modules — extract
later only if a package outgrows the shape.

```
┌─ :app (Android) ──────────────────────────────────────┐
│  ui/        Compose screens, Catppuccin theme          │
│  playback/  MediaLibraryService, quirk data sources    │
│  sync/      SAF folder access, playlist watcher,       │
│             pending.csv file I/O                       │
│  OndaApp    composition root                           │
└──────────────────────┬────────────────────────────────┘
                       │ depends on
┌─ :core (pure Kotlin/JVM) ─────────────────────────────┐
│  model/         Station, Playlist, Group, MediaId      │
│  m3u/           M3uParser + playlist-indirection       │
│                 parser (.pls/.m3u → stream URL)        │
│  radiobrowser/  RadioBrowserClient, browse trees       │
│  favorites/     PendingCsv (read/merge/serialize)      │
└───────────────────────────────────────────────────────┘
```

`:core` never touches Android, files, or SAF: strings/streams in, data
classes out. Everything holding a `Uri`, `Context`, or `Player` lives
in `:app`.

Dependencies (complete list): Media3 (exoplayer, session, HLS),
Compose, kotlinx.serialization, OkHttp (radio-browser API only, never
streams), Coil (logos).

## 2. Data layer — watched folder

- **Access:** one-time onboarding screen → SAF
  `ACTION_OPEN_DOCUMENT_TREE` → persisted permission. Lost permission
  or missing folder falls back to that screen, never an empty list.
- **Loading:** `PlaylistWatcher` (`:app`) lists `*.m3u` in the tree,
  reads each file, hands text to `M3uParser` (`:core`). Result:
  `StateFlow<List<Playlist>>` observed by both UI and media service.
  Playlist order: known names first (stations, nts, fip, bbc,
  public-radio, radio-garden), unexpected extras alphabetically after.
- **Hot reload:** SAF `ContentObserver` events are unreliable for
  third-party sync apps, so triggers are belt-and-suspenders: rescan
  on every app-to-foreground transition + `ContentObserver` on the
  tree + pull-to-refresh. No background polling. A scan re-reads all
  files (a few KB) and diffs by content hash, so redundant triggers
  are free.
- **Parser:** handles `#EXTM3U`/`#EXTINF` attributes (`group-title`,
  `tvg-logo`), tolerates BOM/CRLF/blank lines, skips malformed entries
  rather than failing the file. Stations keep playlist-file origin and
  group; stable identity = stream URL.

## 3. Playback — Media3 service and stream quirks

One `MediaLibraryService` hosting ExoPlayer; the UI talks to it only
through a `MediaController`, so notification, lock screen, Bluetooth,
and Android Auto share one state. Radio semantics: play/stop, not
pause. Audio focus / becoming-noisy: Media3 defaults.

**Media IDs** (stable across process death):
`playlist:<file>/<group>/<url-hash>` for M3U stations,
`rb:<stationuuid>` for radio-browser results. The service resolves ids
via the same `StateFlow` the UI reads.

**Quirk handling by layer:**

| Quirk | Handling |
|---|---|
| Playlist indirection (.pls/.m3u → real stream; somafm, kzsu, streamtheworld) | `ResolvingDataSource`: at play time, sniff by extension/content-type, fetch, parse with `:core` indirection parser, swap in first stream URL. Never persisted — re-resolves every play, so rotations self-heal. |
| HLS (BBC akamaized, KQED, iheart) | `media3-exoplayer-hls`; auto-detected. No custom code. |
| Redirect services (radio.garden, RadioKing, radioeins) | Stored URLs stay unresolved; HTTP data source follows redirects at play time, cross-protocol included. |
| Shoutcast v1 `ICY 200 OK` | Streams use Media3 `DefaultHttpDataSource` (HttpURLConnection-based, tolerates the status line). OkHttp-backed sources reject it — OkHttp is API-only. Claim verified against a live shoutcast v1 station during implementation. |
| ICY metadata | Media3 parses natively → `MediaMetadata` → UI + notification. HLS timed metadata where present; otherwise station name only. |

**Errors:** one automatic retry on connect failure or drop, then a
"station unreachable" state in the player UI. No infinite retry. On
network loss mid-play: Media3 default, plus the single retry when
connectivity returns while the session is active.

## 4. Discovery — radio-browser.info

`RadioBrowserClient` in `:core` (OkHttp + kotlinx.serialization).
Server selection: hardcoded mirror list (`de1`, `nl1`, `at1`) tried in
order, first responder remembered — no DNS round-robin machinery. All
station lists use `order=votes&reverse=true&hidebroken=true`.

Browse trees: **Tags** (top ~100 by station count → stations),
**Countries** (list → stations), **Top voted**, and free-text
**Search**. Drill-downs paginate (offset/limit; infinite scroll).
Rows show logo, name, codec/bitrate, votes. Tap plays immediately
(`rb:` media id, raw `url` field — not `url_resolved` — preserving
self-healing); star favorites into pending.csv.

## 5. Favorites round-trip — pending.csv

Write path only; the app never renders pending entries.

- **Write:** `PendingCsv` (`:core`) reads existing file, dedupes by
  stream URL, appends, returns full serialized text; `:app` writes
  atomically via SAF (temp file + rename). Read-modify-rewrite makes
  double-taps idempotent.
- **Schema:** stations.csv columns (confirm against the real file at
  plan time), then extra columns (`stationuuid`, `tags`, `country`,
  `votes`) the desktop merge may take or drop. `url` stays unresolved.
- **Un-favorite:** star toggles; removes the row. Star state on a
  Discover row = "is this URL currently in pending.csv". After the
  desktop merge consumes the file, the app has no memory of it.
- **Conflicts:** desktop merge should consume-and-clear; simultaneous
  writes fall to Synology's conflict-copy behavior. Single-user,
  seconds-apart workflow — no locking.

## 6. UI

Two bottom tabs + mini-player bar docked above the tab bar whenever a
session exists.

- **Playlists tab:** six playlist cards (name, count) → playlist
  screen: sticky `group-title` headers, rows with logo + name,
  playing row highlighted.
- **Discover tab:** search field, then Tags / Countries / Top voted
  segmented row → drill-downs per §4.
- **Now Playing:** mini-player expands to bottom sheet — large logo,
  station name, ICY title, play/stop. No queue, no seek bar.
- **Settings:** gear icon on Playlists top bar → re-pick folder,
  about. Theme follows system.

**Theme:** Catppuccin Latte (light) / Frappé (dark) as named semantic
Material 3 color schemes; no ad-hoc hex; dynamic color off.

**Logos:** Coil with disk cache; `tvg-logo` (M3U) / `favicon`
(radio-browser); deterministic letter-tile fallback on a Catppuccin
accent.

## 7. Android Auto

Same service exposes the browse tree: root → six playlists → groups →
stations. Discover deliberately absent from Auto. No Auto-specific UI
beyond the tree callback.

## 8. Testing

`:core` JVM tests lock in the stream-quirk knowledge:

- **M3uParser:** fixtures from the six real playlists (all ~205
  stations parse, groups correct) + synthetic malformed inputs.
- **Indirection parser:** captured `.pls`/`.m3u` bodies (somafm, kzsu,
  streamtheworld).
- **PendingCsv:** round-trip, dedupe, idempotent re-favorite,
  un-favorite.
- **RadioBrowserClient:** recorded JSON; mirror failover.

`:app` stays thin on automated tests. Play-time quirk claims (ICY 200
OK, redirects, HLS) get a manual smoke checklist — one named live
station per quirk class — run before each release.

## Out of scope (this milestone)

Sleep timer; pending favorites rendered in-app; Play Store anything;
background sync/polling; stream recording; equalizer.
