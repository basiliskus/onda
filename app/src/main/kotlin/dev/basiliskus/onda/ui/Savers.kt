package dev.basiliskus.onda.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.radiobrowser.RbStation

/**
 * Savers for the nullable sheet-driving state (`rememberSaveable(stateSaver = …)`), so an
 * action or picker sheet survives rotation and process death mid-flow (GitHub #18) — the
 * restored value is the same snapshot the var held, with the same staleness window a
 * config change never widened. Field lists are positional: adding a field to either type
 * without touching its saver restores garbage, which is why each save/restore pair sits
 * here in one place. An empty list encodes null: listSaver saves nothing for an empty
 * list, so a null value restores as the initializer's own null and the non-empty
 * branches below are the only ones a real restore reaches.
 */
internal val StationSaver: Saver<Station?, Any> = listSaver(
    // logo rides as "" for null — a null element inside the saved list is not reliably
    // Bundle-saveable. The M3U parser CAN hand through an empty-but-present tvg-logo
    // verbatim, so restore collapses that edge to null: the one direction that is safe,
    // since null is what every consumer handles as logo-absent (StationRow via
    // isNullOrBlank, playableItem by skipping setArtworkUri's parse).
    save = { s -> if (s == null) emptyList() else listOf(s.name, s.url, s.group, s.playlist, s.logo.orEmpty(), s.pending) },
    restore = { l ->
        if (l.isEmpty()) null
        else Station(
            name = l[0] as String, url = l[1] as String, group = l[2] as String,
            playlist = l[3] as String, logo = (l[4] as String).ifEmpty { null }, pending = l[5] as Boolean,
        )
    },
)

internal val RbStationSaver: Saver<RbStation?, Any> = listSaver(
    save = { s ->
        if (s == null) emptyList()
        else listOf(
            s.stationuuid, s.name, s.url, s.favicon, s.tags,
            s.country, s.countrycode, s.codec, s.bitrate, s.votes,
        )
    },
    restore = { l ->
        if (l.isEmpty()) null
        else RbStation(
            stationuuid = l[0] as String, name = l[1] as String, url = l[2] as String,
            favicon = l[3] as String, tags = l[4] as String, country = l[5] as String,
            countrycode = l[6] as String, codec = l[7] as String,
            bitrate = l[8] as Int, votes = l[9] as Int,
        )
    },
)
