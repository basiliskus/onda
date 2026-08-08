package dev.basiliskus.onda.core.ops

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Op {
    val id: String
    val ts: String // ISO-8601 UTC, informational; file order is authoritative on-phone

    /**
     * Shared id of the journal write this op entered in (GitHub #38): every op stamped in one
     * write carries the same fresh uuid, so the desktop merge can treat the group as
     * all-or-nothing instead of inferring pairs by url. Null — and absent from the JSON, unlike
     * playlist/group whose explicit nulls the contract sample pins — means exactly one thing:
     * the op predates the field. Uniform on new writes, single-op ones included.
     */
    val batch: String?

    @Serializable @SerialName("add")
    data class Add @OptIn(ExperimentalSerializationApi::class) constructor(
        override val id: String,
        override val ts: String,
        val station: OpStation,
        val playlist: String? = null,
        val group: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val batch: String? = null,
    ) : Op

    @Serializable @SerialName("delete")
    data class Delete @OptIn(ExperimentalSerializationApi::class) constructor(
        override val id: String,
        override val ts: String,
        val url: String,
        val playlist: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val batch: String? = null,
    ) : Op

    @Serializable @SerialName("move")
    data class Move @OptIn(ExperimentalSerializationApi::class) constructor(
        override val id: String,
        override val ts: String,
        val url: String,
        val playlist: String,
        @SerialName("to_group") val toGroup: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val batch: String? = null,
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
