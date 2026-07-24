package dev.basiliskus.onda.core.model

data class Station(
    val name: String,
    val url: String,
    val group: String,
    val playlist: String,
    val logo: String? = null,
    val pending: Boolean = false,
)

data class Group(val title: String, val stations: List<Station>)

data class Playlist(val name: String, val groups: List<Group>) {
    val stations: List<Station> get() = groups.flatMap { it.stations }
}
