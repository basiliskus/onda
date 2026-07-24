package dev.basiliskus.onda.core.ops

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Op {
    val id: String
    val ts: String // ISO-8601 UTC, informational; file order is authoritative on-phone

    @Serializable @SerialName("add")
    data class Add(
        override val id: String,
        override val ts: String,
        val station: OpStation,
        val playlist: String? = null,
        val group: String? = null,
    ) : Op

    @Serializable @SerialName("delete")
    data class Delete(
        override val id: String,
        override val ts: String,
        val url: String,
        val playlist: String,
    ) : Op

    @Serializable @SerialName("move")
    data class Move(
        override val id: String,
        override val ts: String,
        val url: String,
        val playlist: String,
        @SerialName("to_group") val toGroup: String,
    ) : Op
}

@Serializable
data class OpStation(
    val name: String,
    val url: String,
    val icon: String = "",
    val stationuuid: String = "",
    val tags: String = "",
    val country: String = "",
    val votes: Int = 0,
)
