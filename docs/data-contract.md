# Data contract

Onda renders playlists it doesn't own. This page is the contract
between the app and whatever produces and consumes the files in its
folder: what Onda reads, what it writes, and the invariants each side
pins with tests.

## Using your own folder

Any folder the phone can open through Android's folder picker works —
Synology Drive, Syncthing, Nextcloud, or plain files on the device.
The minimum is one or more `*.m3u` files; each file is a playlist:

```m3u
#EXTM3U
#EXTINF:-1 group-title="Jazz",Example Jazz Radio
https://example.com/stream.mp3
```

- `group-title` is the group a station renders under (`""` for none);
  `tvg-logo="<url>"` is an optional icon.
- Avoid double quotes inside attribute values — they have no escaping.
  The maintainer's exporter also replaces quotes in station names (see
  [M3U export](#m3u-export)).
- Onda writes two files next to the playlists: `ops.jsonl` (your
  phone-side edits) and `recordings/onda-titles.log` (liked tracks).
  Without a desktop consumer, edits still show in the app — it
  overlays the journal on the playlists — but never reach the M3U
  files.

The sections below are the full contract with the maintainer's
producer: the private `basiliskus/data` repo, whose
`radio/scripts/stations-merge-ops.py` applies the journal and
re-exports the M3Us (`stations-to-m3u.py`), and whose
`music/scripts/radio-titles-to-json.py` ingests the titles log.
"Data pins" name tests in that repo. Every invariant is pinned by a
test on each side — change either side only with its pair in view,
and update this page in the same change as any contract change (an
onda PR alongside the data change).

## Journal op schema

One JSON object per line in `ops.jsonl`: `op` (`add`/`delete`/`move`),
`id` (uuid — no whitespace, the consumed-ids ledger is
whitespace-split), `ts` (UTC ISO-8601, fixed-width milliseconds), plus
the type's fields. `playlist`/`group` spell absent as explicit `null`;
`batch` is different: a v0.5.1+ write stamps it on **every** op it
writes, single-op writes included, so **absent means the op predates
the field — no atomicity information, never a known-safe singleton**
(it may have been half of a group whose grouping is simply unknown;
onda PR #62 R1). The desktop gives such ops the legacy path:
independent application plus `partner_note` inference. Onda never
emits `"batch": null`; the desktop reads `null` the same as absent
(defense in depth) and rejects `""`/non-strings as malformed.
For journal semantics beyond this page, `docs/spec.md` and
the **Amendments** section of the journal-design doc
(`docs/superpowers/specs/2026-07-23-ops-journal-design.md`) are current; that
doc's §3 body is historical.

- Onda pins: `OpsJournalTest` — *discriminator is op and nulls are
  explicit*, *a null batch emits no batch key while playlist and group
  nulls stay explicit*, *a pre-batch journal line parses with a null
  batch*, *parse skips garbage line and counts it malformed*.
- Data pins: `test_null_batch_means_batchless`,
  `test_empty_batch_is_malformed`, `test_whitespace_in_op_id_is_rejected`,
  `test_malformed_lines_skipped`, `test_unhashable_batch_is_malformed`.

## Ordering

Within a batch, `ts` is strictly increasing in list order — `stamp()`
re-mints each op's ts from a base floored above every parseable prior ts
in the journal. **Scope: only among v0.5.1+ stamped ops, and the
comparison contract is parsed time, not byte order** — `parse_journal`
parses, so the desktop complies. The desktop sorts by `(ts, file
index)`: equal-ts ops apply in file order, batched ops need no
stable-tiebreak assumption. On the phone, file order is authoritative;
ts is informational.

Compaction guarantees residue order **within one compaction key only**;
a cross-playlist move pair spans two keys (delete keyed to the source
playlist, add to the target) and its order and adjacency carry no
meaning — `batch` plus all-or-nothing application is the replacement
(onda PR #62 R2). The desktop relies on cross-key order nowhere:
grouping is by batch uuid alone, and cross-key members of a batch
commute in application (different playlists, or different urls in one).

- Onda pins: `OpsJournalTest` — *stamp shares one batch across new ops
  with strictly increasing ts in list order*, *stamp floors its base
  above every parseable prior ts*, *stamped ts is fixed-width millis,
  byte-order consistent with its siblings*; `OpsCompactionTest` —
  *cross-playlist delete and add are distinct residues*, *residues keep
  first-appearance key order*.
- Data pins: `test_ops_apply_in_ts_order`,
  `test_same_ts_pair_keeps_file_order`.

## Batch atomicity

Every op from one journal write carries the same fresh `batch` uuid —
a property of the write itself, not of undo arming (`toggle` and
`addTargeted` arm no token and still stamp; onda PR #57). Undo
compaction preserves a survivor's batch. The desktop applies each batch
all-or-nothing at its first member's position: any member rejection
aborts the whole batch onto the rejection path, and the per-playlist
write/consume order makes crash windows replay into completion. A batch
that lost a member at parse aborts unapplied — **knowable only when the
dropped line still parsed to JSON carrying a string batch**; a line torn
beyond JSON parsing takes its batch id with it, and its surviving
partners apply as if whole (documented residual risk in
`parse_journal`).

- Onda pins: `OpsJournalTest` — *stamp shares one batch…*, *compaction
  keeps a surviving op's batch*, *compaction's move-rewrites-add keeps
  the add's batch and takes the move's ts*; `OpsRepositoryTest` —
  migration adds share the write's batch.
- Data pins: `test_batch_aborts_atomically`,
  `test_batch_applies_when_every_member_lands`,
  `test_discarded_member_aborts_its_batch`,
  `test_crash_between_playlist_writes_self_heals`,
  `test_write_order_is_per_batch_not_global`.

## M3U export

The desktop writes the `.m3u` files; **no `"` ever reaches an EXTINF
line** — `safe()` substitutes `'` in group, name, and icon (Onda's
attribute regex has no escape syntax, so one quote can forge an
attribute or empty a parsed name). Onda's parser is additionally
hardened against forged attributes (onda#39) — defense in depth, not a
license to regress the writer-side guarantee.

- Onda pins: `M3uParserTest` — *a name cannot forge tvg-logo*, *a name
  cannot forge group-title*, *comma inside a quoted group-title does not
  truncate…*.
- Data pins: `test_quotes_never_reach_the_exported_m3u`,
  `test_icon_cannot_forge_m3u_attributes`.

## Playlist folder: file ownership

Files in the folder picked in Onda (the maintainer's is
`~/Sync/Radio`, synced by Synology Drive):

| File | Writer | The other side |
|---|---|---|
| `ops.jsonl` | phone appends; compacts the **whole** journal on its own mutations (it has no consumed/unconsumed concept — the consumed-ids ledger is desktop-local and invisible to it) | desktop's only write is the all-or-nothing clear, guarded by a byte compare — a journal grown mid-run is left alone |
| `*.m3u` | desktop (stations-to-m3u.py) | phone reads |
| `ops-rejected.jsonl` | desktop appends | record only — synced, so it can be lost; the merge script's stdout log on the maintainer's desktop (`~/Library/Logs`) is the durable copy |
| `recordings/onda-titles.log` | phone appends (Onda) | desktop ingest reads |
| `recordings/pyradio-titles.log` | desktop appends (pyradio) | ingest reads |

The consumed-ids ledger (on the maintainer's desktop,
`~/.local/state/onda/applied-ids`) is desktop-owned and machine-local —
deliberately outside the synced folder.
The journal-restraint half of this table is the desktop's to pin — Onda
cannot test desktop behavior; its matching guarantee is tolerance of
the clear. The titles log's rotation/trim is likewise desktop-owned
(onda `docs/spec.md`). Field-verified 2026-08-07 (onda PR #62): the
conditional clear plus the ledger correctly discarded a real Synology
sync-echo replay of an already-consumed journal.

**Closed gap (data#19, onda's recommendation):** the phone compacts the
whole journal, so consumed-but-uncleared ops were raw material for a
phone compaction to fold against fresh ops — three swallowing shapes
(consumed add + fresh delete net to nothing; a fresh move folded into a
consumed add keeps the consumed id and is discarded as `already`; a
fresh delete dropped first-wins against a consumed one) — and the phone
write that wakes the next desktop run is itself the hazard, so the
window persisted until it struck. The desktop therefore clears the
journal **immediately after the ledger is complete, before the export**;
a failed export's retry rides the machine-local `needs-export` marker
beside the ledger (nothing new in the synced folder — the clear's own journal
write re-fires the watcher for the retry). Two residuals, both
irreducible from here: a crash inside the consumed-visible span — from
the FIRST per-playlist ledger append, through the remaining CSV writes
and the rejection-record appends, to the clear (typically sub-second,
but proportional to playlists touched and records written, not a fixed
operation count); and a phone
that syncs the journal *in* during that same pre-clear visibility — or
during the grown-journal path, where consumed ops linger until the
already-triggered next run — can still compose a mutation against a
stale copy. The reorder shrinks that to the sync-roundtrip minimum;
nothing on either side goes lower without the rejected watermark.
Data pins: `test_unwritable_output_clears_journal_and_marker_retries`,
`test_append_during_export_is_the_next_batch`.

- Data pins: `test_unwritable_output_clears_journal_and_marker_retries`,
  `test_append_during_export_is_the_next_batch`,
  `test_journal_grown_before_the_clear_is_left_alone`,
  `test_missing_and_empty_journal_are_quiet`,
  `test_double_run_is_a_noop`.
- Onda pins: `OpsCompactionTest` — compaction semantics (what survives a
  compact). The two residual windows above are pinned by no test on
  either side — they are crash-timing and sync-timing, not behavior.

## Titles journal format

Onda writes liked lines byte-identical to pyradio's writer — one-line
framed (every character Python's `splitlines()` breaks on collapses to
a space, closing the record-forgery door) — and since onda#51 precedes
each like with a `>>> Station: <name>` line in pyradio's own shape:
one space after the pipe (the four-space indent is the title marker),
sharing the liked line's timestamp, written as **one append** joined by
a single `\n` — a station line never appears without its liked line.
A like with no known station goes out alone, byte-identical to
pre-v0.5.1. `music/scripts/radio-titles-to-json.py`'s shared classifier
attributes likes from both apps with no app-specific code. Known bound
(onda PR #56): a stationless like inherits the previous station under
the desktop's carry-forward — unreachable from Onda's UI today.

- Onda pins: `TitlesLogTest` — *likedLine renders the pinned pyradio
  line*, *likedRecord with a station renders the pinned pair, station
  line first*, *a station name cannot forge a liked record*, *every
  character python splits lines on collapses to a space*;
  `SongCaptureRepositoryTest` — *capture with a station appends the
  pair as one record*, *a failed append with a station tears neither
  line into the log*.
- Data pins: the `_classify` / `_walk` / `_fields` doctests in
  `radio-titles-to-json.py` — real journal lines where a live journal
  has provided them; the liked-line shape itself is still inferred from
  pyradio's writer (no desktop like has landed yet), and the doctests
  say so.
