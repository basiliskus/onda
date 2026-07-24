package dev.basiliskus.onda.core.ops

import dev.basiliskus.onda.core.favorites.PendingCsv

object LegacyPendingMigration {
    /** ids and ts are caller-supplied so :core stays randomness- and clock-free. */
    fun toOps(csvText: String, ids: () -> String, ts: String): List<Op.Add> =
        PendingCsv.parse(csvText).map { s ->
            Op.Add(
                id = ids(), ts = ts,
                station = OpStation(
                    name = s.name, url = s.url, icon = s.icon,
                    stationuuid = s.stationuuid, tags = s.tags, country = s.country, votes = s.votes,
                ),
                playlist = null, group = null,
            )
        }
}
