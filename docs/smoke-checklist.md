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
| 9 | Favorites round-trip | star + unstar in Discover | pending.csv correct on desktop |
| 10 | Android Auto | browse root → playlist → group → play | works on head unit/DHU |
| 11 | Rotation + dark mode | rotate; toggle system theme | no state loss, Latte↔Frappé |
| 12 | Kill + notification | swipe app away while playing | audio continues, notification controls work |
