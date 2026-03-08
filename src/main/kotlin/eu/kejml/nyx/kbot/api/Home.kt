package eu.kejml.nyx.kbot.api

import kotlinx.serialization.Serializable

@Serializable
data class Home(
    val items: List<Item>,
)

@Serializable
data class Item(
    val id: Long,
    val content: String,
)

fun Home.getContentById(id: Long): Item? = items.find { it.id == id }
