package eu.kejml.nyx.kbot.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class Discussion(val posts: List<Post>)

@Serializable
data class Post(val id: Long, val username: String, val content: String, @SerialName("inserted_at") val insertedAt: String)