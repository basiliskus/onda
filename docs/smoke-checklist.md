# Release smoke checklist

Run on a real phone against the published release APK, following the
[release checklist](release.md). One station per quirk class. Record version,
device, date, pass/fail/skip(reason), and evidence for each applicable row.
Unverified behavior alone does not block a tag; a known must-not-ship defect
does, per [release policy](adr/0001-release-at-milestone-end.md).

While the pass runs, watch the synced artifacts from the desktop —
a loop that snapshots every content transition of `ops.jsonl` (and
`tail` on the titles log) turns UI observations into byte evidence.
v0.5.1's pass caught a silently-failed undo (#25) and a Synology
sync-echo replay this way; neither was visible on the phone screen.
Note the phone syncs manually, so the desktop sees coalesced final
states per sync, not intermediate ones.

| # | Quirk | Station | Pass criteria |
|---|-------|---------|---------------|
| 1 | Plain icecast AAC | KEXP 90.3 | audio < 5s, notification controls work |
| 2 | .m3u indirection | KZSU 90.1 | plays (URL ends .m3u) |
| 3 | .pls indirection | SomaFM (any, via Discover) | plays |
| 4 | HLS | any BBC station | plays |
| 5 | Redirect self-heal | any radio-garden station | plays |
| 6 | Shoutcast v1 ICY 200 | (none in current set) | No current station speaks shoutcast v1 (verified 2026-07-23). Row kept because DefaultHttpDataSource must keep tolerating ICY 200 OK; re-verify with a real v1 station when one enters the playlists. |
| 7 | ICY metadata | KEXP | track title shows and updates |
| 8 | Hot reload | edit a .m3u on desktop, wait for sync, foreground the app | change appears |
| 9 | Editing round-trip | star in Discover; swipe-delete + undo; long-press move | ops.jsonl correct (add/delete/move ops; undo compacts away); desktop merge consumes + clears; overlay dissolves after re-sync |
| 10 | Android Auto | browse root → playlist → group → play | works on head unit/DHU |
| 11 | Rotation + dark mode | rotate; toggle system theme | no state loss, Latte↔Frappé |
| 12 | Kill + notification | swipe app away while playing | audio continues, notification controls work |
| 13 | Resumption after process death | play KEXP, force-stop the app, then media button (or quick-settings media carousel) | last station resumes; a Discover-only station or empty folder must fail silently, no crash |
| 14 | TalkBack | enable TalkBack; playlist row long-press; Discover star | actions sheet reachable and announced ("Station actions"); star announces Star/Unstar; move + remove completable without swipe gestures |
| 15 | New-group IME | long-press move → New group field | keyboard doesn't cover the field or + button inside the sheet; typed name lands in the op |
| 16 | Like action tracks metadata | play KEXP, then FIP (no `icy-metaint`), then back | heart action present in the notification on KEXP, absent on FIP, back on return; no notification flicker as titles update; heart absent in Android Auto (deferred) |
| 17 | Titles log creation & format | (creation) rename `~/Sync/Radio/recordings` aside on the desktop, let sync propagate, like once from the notification. (format) run the real `radio-titles-to-json.py` over the log | (creation) `recordings/` recreated; file named exactly `onda-titles.log` (not `.log.txt`); Toast "Liked". If the provider mangles the name and the rename fails, the only correct outcome is the failure Toast and NO stray file left behind — never a "Liked" Toast with a sharded log (the JVM row covers the caller's half only; the SAF ladder is device-only). (format) every entry ingests with correct timestamps and, since v0.5.1, station attribution — the consumer is the authority `docs/spec.md` names, so ingestion IS the format proof. The pyradio byte-compare was dropped 2026-08-08 (#33): pyradio's log has no liked lines to compare against, and ingestion has passed twice (2026-07-26, 2026-08-07) |
| 18 | Append across likes | like three tracks in a row | three lines, earlier ones intact, file not truncated (only proof SAF honours `"wa"` append); `pyradio-titles.log` untouched; no `onda-titles_*_Conflict.log` after a sync cycle |
| 19 | Sheet like button | FIP vs KEXP in the Now Playing sheet | greyed and untappable on FIP, TalkBack announces disabled; enabled on KEXP, and a tap closes the sheet (by design — the snackbar draws where the sheet sits) then shows "Liked" |
| 20 | Cross-playlist move | any `stations` station → `nts` | long-press → "Move to playlist…" → pick playlist → pick group: both sheets close, the row leaves the source screen, snackbar "Moved … to … in …" with Undo; the target playlist shows it pending (italic) in the chosen group; Undo returns it to the source, unpending. The feature has no JVM-testable UI at all — this row is its only device evidence |
| 21 | New group in the target playlist | same station, target `nts` | pick "New group" at the second sheet: IME Done and the trailing + button both commit; the pending row lands under a new group header in the target (row 15 composed with the new flow) |
| 22 | Move flow dismissal | any station | back/scrim at the playlist picker, then repeat and dismiss at the group picker: the flow aborts at either step, no snackbar, the row is unchanged and `ops.jsonl` mtime unchanged |
| 23 | Rotation during the move flow | any station | rotate with either picker open: the same step survives, and completing or dismissing it still works (fixed by PR #66) |
| 24 | Move a url the target already holds | a station present in both `stations` and `nts` | the row leaves the source; the target shows exactly one row for it, unpending and in its existing group (no duplicate). The desktop merge reports it skipped, never rejected |
| 25 | Resumption after a move | play a `stations` station, move it to `nts`, force-stop the app, then media button | it resumes (row 13 covers the unmoved case; this row covers the id the move invalidated). Load-bearing twice over: the JVM rows cover only the *read* side, so a transposed `setLastPlayed(id, url)` would compile and pass all 185 of them — this row is the sole check on the write side, as well as the only proof Media3 replays what `onPlaybackResumption` hands back |
| 26 | Discover search rotation | enter search text, then rotate | text survives rotation |
| 27 | Double-tap navigation | double-tap a playlist or group, then press Back | one destination is pushed; Back returns to the preceding screen without a duplicate destination |
| 28 | Sheet survival | rotate with station actions, move or add-to-playlist sheet open | the active sheet survives and its actions still target the intended station; row 23 covers the cross-playlist picker steps |
| 29 | Voided undo feedback | swipe-delete one station, then a second while the first snackbar remains; tap Undo on the first snackbar | snackbar eventually says "Nothing to undo"; the two deletes remain applied |

## Outstanding verification carried from v0.5.2

The retired process records did not record results for PR #66's search-text
rotation, double-tap navigation/back-stack, sheet survival and voided-undo
snackbar checks, or for release playback. These remain unverified; use rows
26–29 (plus 23 for move sheets) and the playback rows above. Each requires a
**device**. Record the actual release tested; a later release pass doesn't establish a
historical v0.5.2 result.

R1 (fast-undo suppression under the journal watcher) is closed as not
hardware-observable: the synced final bytes cannot distinguish its paths.
`UndoIntegrityTest."an undo tapped before the mutate runs suppresses the mutation entirely"`
pins suppression with a write-count assertion. Don't reopen that carry.
Row 17's fresh-folder scenario remains a standing release check, not unpaid
v0.5.2 verification debt.
