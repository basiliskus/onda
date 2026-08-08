# Operations journal — playlist editing round-trip design

Outcome of the 2026-07-23 brainstorming session. Extends the favorites
round-trip into full phone-side playlist editing (add / delete /
move-between-groups) while preserving the architecture's core property:
single writer per file, desktop `stations.csv` as source of truth, the
app never mutates M3Us. Targeted as milestone v0.3.0 ("Playlist editing
round-trip"), run as a pipeline feature run plus a parallel dotfiles-side
deliverable.

## Decisions made in session

- Ops: **add**, **delete**, **move-between-groups**. No rename. No
  reorder — order ops are relationship-anchored (meaning depends on
  sequence state at apply time), which reintroduces silent two-writer
  conflicts on the one dimension both sides routinely touch; membership
  ops are identity-anchored (url+playlist) and conflict-free by
  construction. If intra-group ordering from the phone ever becomes
  real, the clean extension is a snapshot op ("group G is now exactly
  [urls…]") — roadmap candidate, not v1.
- **Both sides built**: Onda ships journal + UI; the dotfiles repo gets
  a matching merge-script + watcher spec so the loop closes.
- **Optimistic overlay**: playlists render with pending ops applied;
  reconciliation is free (journal consumed + regenerated M3Us arrived =
  overlay dissolves into truth).
- **`ops.jsonl` replaces `pending.csv`** (which has no consumer yet, so
  the swap is free; legacy rows migrate on first launch).
- UI gestures follow Material conventions: **swipe = delete** (with
  undo snackbar), **long-press = sheet** (Move to group… / Remove),
  star tap = quick add, star long-press = targeted add.
  Drag-to-adjacent-group logged as future gesture sugar; rejected for
  v1 because a drag implies a drop *position* that the op doesn't carry
  — the row would teleport to the group's true CSV position on
  reconcile.

## 1. Contract — `ops.jsonl`

One JSON Lines file in the synced folder. Each line one op, `ts`
(ISO-8601 UTC) ordered, `id` a UUID for idempotency:

```jsonl
{"op":"add","id":"…","ts":"…","station":{"name":"…","url":"…","icon":"…","stationuuid":"…","tags":"…","country":"…","votes":0},"playlist":null,"group":null}
{"op":"delete","id":"…","ts":"…","url":"http://…","playlist":"stations"}
{"op":"move","id":"…","ts":"…","url":"http://…","playlist":"stations","to_group":"Review ▲ Promising"}
```

Semantics:

- `add` with `playlist: null` = today's quick star ("desktop review
  ladder decides"; merge lands it in a configured intake group).
  Non-null playlist/group = targeted add.
- `delete` scoped by url + playlist (the same url can live in two
  playlists).
- `move` targets a group by name within a playlist; a nonexistent group
  name becomes a new group at export time.
- Ownership: the **phone owns `ops.jsonl`** — including compacting its
  own unconsumed entries (un-star nets the add away; undo removes the
  delete) via the same tmp/bak crash-safe rewrite choreography
  `pending.csv` uses today. The **desktop's only verbs** are
  consume-everything-and-clear or don't-touch.
- Star state in Discover = "an unconsumed add op for this url exists".

## 2. App side

`:core` (pure Kotlin, kotlinx.serialization):

- `OpsJournal`: parse / serialize / compact. Parsing skips malformed
  lines (same lenience philosophy as `M3uParser`). Compaction nets by
  (url, playlist) key: add+un-add → nothing; move-then-move → last
  wins; delete-then-re-add → nets correctly.
- `applyOps(playlists, ops): List<Playlist>` — the overlay as a pure
  function: deletes hide, moves re-group (pending station rendered at
  the target group's end, matching eventual CSV truth), targeted adds
  inject a station flagged pending. Null-playlist adds are invisible in
  playlists (Discover star shows them). Ops referencing vanished urls
  are rendering no-ops.
- `Station` gains a `pending: Boolean = false` field (default false, so
  existing construction sites and tests are untouched; identity —
  `MediaIds.station` — already excludes non-core fields the same way it
  excludes `logo`).

`:app`:

- `FavoritesRepository` evolves into `OpsRepository`: same mutex,
  tri-state `Result` reads, orphaned-bak recovery, folder-tick refresh
  — holding `List<Op>` instead of a URL set. File I/O generalizes the
  existing pending.csv functions in `PlaylistRepository` (rename to
  journal-agnostic names).
- `PlaylistRepository` exposes ONE derived flow: raw playlists combined
  with the journal through `applyOps`. Playlist UI, the media session
  station index, and the Android Auto tree ALL consume the derived flow
  — a deleted station disappears from phone and car simultaneously.
- UI:
  - Swipe a station row (playlist detail) → delete op + snackbar Undo
    (undo = compact the op away). `SwipeToDismissBox`, error-color
    reveal.
  - Long-press a row → bottom sheet: **Move to group…** (picker:
    existing groups + free-text new group) / **Remove from playlist**
    (TalkBack-reachable path for both actions).
  - Discover star tap = quick add / un-add (unchanged UX); star
    long-press = playlist → group picker for a targeted add.
  - Pending-op rows render with a subtle marker (italic name) that
    disappears on reconcile.

## 3. Desktop side (dotfiles deliverable, spec handed off)

- `stations-merge-ops.py`: read `~/Sync/Radio/ops.jsonl`; apply ops in
  `ts` order to the pyradio CSVs (playlist name ↔ CSV file mapping;
  null-playlist adds → configured intake group, e.g. "Review · New");
  run `stations-to-m3u.py`. Rules: add-that-exists (by url) → skip;
  delete/move url-not-found → append to `ops-rejected.jsonl` with
  reason, never die. Keep an `applied-ids` log so a crash between apply
  and clear cannot double-apply. Conditional clear: re-read the journal
  before deleting — if bytes changed since processing began (phone
  appended mid-run), leave the new tail for the next run.
- Watcher per dotfiles scheduling convention (launchd/cron), triggered
  on `ops.jsonl` change, debounced a few seconds for sync settling.

## 4. Edge cases

- Vanished-station op: merge rejects to log; app overlay no-ops;
  dissolves on journal clear. Nothing wedges.
- Half-landed round trip (journal cleared, M3Us not yet re-synced):
  brief reappearance of a deleted station — same eventual-consistency
  window today's stars have; accepted.
- Duplicate adds: compaction nets by url pre-write; merge skips by url
  as backstop.
- Journal corruption (partial line mid-sync): skip malformed lines,
  surface count via the existing error snackbar channel; never fail the
  file.
- File safety: `ops.jsonl` inherits the full pending.csv choreography
  (tmp/bak, orphan recovery, tri-state reads, mutex, folder ticks).

## 5. Testing

- `:core` JVM: journal round-trip + malformed-line lenience; compaction
  algebra; `applyOps` against the real playlist fixtures (delete hides,
  move re-groups, targeted add injects flagged, null-playlist add
  invisible).
- App E2E smoke rows: swipe-delete with undo; ladder promotion via
  picker; simulated desktop consume (clear journal + push edited M3U →
  overlay dissolves, marker gone).
- Merge script: fixture-CSV tests, in the dotfiles repo.

## 6. Rollout

- Migration: first launch with a legacy `pending.csv` converts rows to
  null-playlist add ops and deletes the file.
- Docs: rewrite the pending.csv contract in `docs/spec.md` and
  CLAUDE.md's data-contract section to ops.jsonl.
- Roadmap: milestone v0.3.0 "Playlist editing round-trip" — after the
  v0.2.0 polish queue unless reprioritized.
- Runs as Onda's first pipeline feature run; dotfiles spec handed off
  in parallel so both halves land close together.

## Out of scope (this design)

Rename op; reorder ops (snapshot-order noted as future candidate);
drag-to-adjacent-group gesture (future sugar); multi-device journal
merging (single phone assumed).

## Amendments

2026-07-24 (PR #17 review): Discover star scope narrowed to quick-star
(playlist:null) adds only — a lit star no longer reflects targeted
adds, and un-star never removes them; targeted adds are managed as
pending rows in the playlist UI. Supersedes the "an unconsumed add op
for this url exists" wording above.

2026-08-07 (GitHub #38): every op introduced by one journal write
carries a `"batch"` field — the write's fresh uuid, shared across the
group, uniform including single-op writes and the pending.csv
migration. An **absent** key (never `"batch":null` — unlike
playlist/group, whose explicit nulls the sample above pins) means
exactly one thing: the op predates the field. Within a batch, `ts` is
strictly increasing in write order (base + index ms); across batches
`ts` stays informational and file order authoritative on-phone, as
above. This is what lets the desktop merge treat a group (e.g. the
cross-playlist Delete+Add pair) as all-or-nothing instead of inferring
pairs by url. Two caveats the desktop's drop-batch must honor,
established at review: phone-side compaction can leave a pair spanning
two batches (a repeated identical move keeps the first delete's batch
while the replacing add carries the newer one), and a batch's members
can be non-contiguous with non-monotone ts after a rewrite transfers a
later write's ts onto a surviving op. Batch is a grouping hint with
one writer, not a completeness proof.
