# Pre-release smoke checklist

Run on a real phone before tagging. One station per quirk class.

| # | Quirk | Station | Pass criteria |
|---|-------|---------|---------------|
| 1 | Plain icecast AAC | KEXP 90.3 | audio < 5s, notification controls work |
| 2 | .m3u indirection | KZSU 90.1 | plays (URL ends .m3u) |
| 3 | .pls indirection | SomaFM (any, via Discover) | plays |
| 4 | HLS | any BBC station | plays |
| 5 | Redirect self-heal | any radio-garden station | plays |
| 6 | Shoutcast v1 ICY 200 | WXYC 89.3 (substituted for KFJC — see note) | plays (verify server is v1 via `curl -sI`) |
| 7 | ICY metadata | FIP | track title shows and updates |
| 8 | Hot reload | edit a .m3u on desktop, wait for sync, foreground the app | change appears |
| 9 | Favorites round-trip | star + unstar in Discover | pending.csv correct on desktop |
| 10 | Android Auto | browse root → playlist → group → play | works on head unit/DHU |
| 11 | Rotation + dark mode | rotate; toggle system theme | no state loss, Latte↔Frappé |
| 12 | Kill + notification | swipe app away while playing | audio continues, notification controls work |

Note on item 6: `curl -sI --max-time 5 http://netcast.kfjc.org/` does not answer
`ICY 200 OK` — KFJC's `netcast.kfjc.org` is nginx-fronted and returns a
standard `HTTP/1.1 200 OK`. Substituted WXYC
(`http://audio-mp3.ibiblio.org:8000/wxyc.mp3`), though as of writing it also
answers a standard `HTTP/1.0 200 OK` (Icecast 2.4.4) rather than the literal
legacy `ICY 200 OK` line — none of the six playlists' current stations were
found to emit that exact status line. Kept as the named station for this
quirk class regardless, since it's the closest plain-icecast/shoutcast
lineage stream in the set; re-verify with `curl -sI` before relying on the
"breaks strict HTTP clients" framing.
