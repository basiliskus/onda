package dev.basiliskus.onda.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import dev.basiliskus.onda.core.radiobrowser.RbStation

/**
 * Identity of one playlist row for sheet state: url alone is not unique within a playlist
 * (the same stream can sit in two groups — the detail screen's LazyColumn key is built to
 * tolerate exactly that), so the pair pins the tapped instance and its sheet acts on the
 * right copy (PR #66 round 3). Saved as two strings — group titles may contain any
 * delimiter, so no single-string encoding is safe.
 */
internal data class StationKey(val group: String, val url: String)

internal val StationKeySaver: Saver<StationKey?, Any> = listSaver(
    save = { k -> if (k == null) emptyList() else listOf(k.group, k.url) },
    restore = { l -> StationKey(group = l[0] as String, url = l[1] as String) },
)

/**
 * Saver for the Discover targeted-add sheet's nullable [RbStation]
 * (`rememberSaveable(stateSaver = …)`), so the picker survives rotation and process death
 * mid-flow (GitHub #18). A Discover station has no phone-side identity to re-derive after
 * restore — unlike the playlist sheets, which persist a url and look the live Station up
 * from the current playlist — and an add is a deliberate act on the tapped snapshot, so
 * restoring that snapshot is the correct semantics.
 *
 * The field list is positional. The real hazard is reordering save's listOf without
 * matching restore's casts (a ClassCastException, or silently swapped same-typed fields);
 * adding a defaulted field without touching the saver just restores the default — edit
 * both lambdas together. A null value saves as an empty list, which listSaver persists as
 * nothing at all: restore is never invoked for it (the initializer's null stands), so
 * restore handles only the non-empty shape.
 */
internal val RbStationSaver: Saver<RbStation?, Any> = listSaver(
    save = { s ->
        if (s == null) emptyList()
        else listOf(
            s.stationuuid, s.name, s.url, s.favicon, s.tags,
            s.country, s.countrycode, s.codec, s.bitrate, s.votes,
        )
    },
    restore = { l ->
        RbStation(
            stationuuid = l[0] as String, name = l[1] as String, url = l[2] as String,
            favicon = l[3] as String, tags = l[4] as String, country = l[5] as String,
            countrycode = l[6] as String, codec = l[7] as String,
            bitrate = l[8] as Int, votes = l[9] as Int,
        )
    },
)
