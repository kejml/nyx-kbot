package eu.kejml.nyx.kbot.api

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Discussion(val posts: List<Post>)

@Serializable
data class Post(
    val id: Long,
    val username: String,
    val content: String,
    @SerialName("inserted_at") val insertedAt: LocalDateTime,
)
