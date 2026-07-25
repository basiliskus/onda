# Pre-release smoke checklist

Run on a real phone before tagging. One station per quirk class.

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
| 17 | Titles log creation | rename `~/Sync/Radio/recordings` aside on the desktop, let sync propagate, like once from the notification | `recordings/` recreated; file named exactly `onda-titles.log` (not `.log.txt`); Toast "Liked"; one correctly formatted line. If the provider mangles the name and the rename fails, the only correct outcome is the failure Toast and NO stray file left behind — never a "Liked" Toast with a sharded log (the JVM row covers the caller's half only; the SAF ladder is device-only) |
| 18 | Append across likes | like three tracks in a row | three lines, earlier ones intact, file not truncated (only proof SAF honours `"wa"` append); `pyradio-titles.log` untouched; no `onda-titles_*_Conflict.log` after a sync cycle |
| 19 | Sheet like button | FIP vs KEXP in the Now Playing sheet | greyed and untappable on FIP, TalkBack announces disabled; enabled on KEXP, and a tap closes the sheet (by design — the snackbar draws where the sheet sits) then shows "Liked" |
| 20 | Cross-playlist move | any `stations` station → `nts` | long-press → "Move to playlist…" → pick playlist → pick group: both sheets close, the row leaves the source screen, snackbar "Moved … to … in …" with Undo; the target playlist shows it pending (italic) in the chosen group; Undo returns it to the source, unpending. The feature has no JVM-testable UI at all — this row is its only pre-merge evidence |
| 21 | New group in the target playlist | same station, target `nts` | pick "New group" at the second sheet: IME Done and the trailing + button both commit; the pending row lands under a new group header in the target (row 15 composed with the new flow) |
| 22 | Move flow dismissal | any station | back/scrim at the playlist picker, then repeat and dismiss at the group picker: the flow aborts at either step, no snackbar, the row is unchanged and `ops.jsonl` mtime unchanged |
| 23 | Rotation during the move flow | any station | known debt, not a regression (GitHub #18): rotating with either sheet open drops the sheet and aborts the flow, exactly as the existing sheet flows do. Record as observed/expected |
| 24 | Move a url the target already holds | a station present in both `stations` and `nts` | the row leaves the source; the target shows exactly one row for it, unpending and in its existing group (no duplicate). The desktop merge reports it skipped, never rejected |
