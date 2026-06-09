package eu.kejml.nyx.kbot.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

enum class DiscussionOrder(
    val apiString: String,
) {
    NEWER_THAN("newer_than"),
    OLDER_THAN("older_than"),
}

enum class PostFormat(
    val apiString: String,
) {
    TEXT("text"),
    HTML("html"),
}

enum class RatingAction(
    val apiString: String,
) {
    POSITIVE("positive"),
    NEGATIVE("negative"),
    NEGATIVE_VISIBLE("negative_visible"),
    REMOVE("remove"),
    NONE("none"),
}

class DiscussionQueryParams(
    private val text: String? = null,
    private val fromId: Long? = null,
    private val discussionOrder: DiscussionOrder = DiscussionOrder.NEWER_THAN,
) {
    private fun isEmpty() = text == null && fromId == null

    fun toUrl(): String {
        if (isEmpty()) return ""

        return listOfNotNull(
            text?.let { "text=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" },
            fromId?.let { "from_id=$it&order=${discussionOrder.apiString}" },
        ).joinToString(separator = "&", prefix = "?")
    }
}

object NyxClient {
    private val secretStream: InputStream? = this.javaClass.classLoader.getResourceAsStream("secret.properties")
    private val props = Properties().apply { load(secretStream) }
    private val nyxToken = props["nyx_token"]
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val log = LoggerFactory.getLogger(this.javaClass)

    suspend fun getDiscussion(
        id: Long,
        params: DiscussionQueryParams? = null,
    ): String = nyxGet("discussion/$id${params?.toUrl() ?: ""}")

    suspend fun getHome(id: Long): String = nyxGet("discussion/$id/content/home")

    suspend fun updateHome(
        discussionId: Long,
        contentId: Long,
        content: String,
    ): String =
        nyxPost(
            endpoint = "discussion/$discussionId/content/$contentId/save",
            content = mapOf(
                "content" to content,
                "format" to PostFormat.HTML.apiString,
            ),
        )

    suspend fun postDiscussion(
        discussionId: Long,
        content: String,
        format: PostFormat = PostFormat.HTML,
    ): String = nyxPost(
        "discussion/$discussionId/send/text",
        mapOf(
            "content" to content,
            "format" to format.apiString,
        ),
    )

    suspend fun sendMail(recipient: String, message: String): String =
        nyxPost(
            endpoint = "mail/send",
            content = mapOf(
                "recipient" to recipient,
                "message" to message,
                "format" to PostFormat.HTML.apiString,
            ),
        )

    suspend fun ratePost(
        discussionId: Long,
        postId: Long,
        action: RatingAction = RatingAction.POSITIVE,
    ): String = nyxPost("discussion/$discussionId/rating/$postId/${action.apiString}")

    suspend fun getMyRating(discussionId: Long, postId: Long): RatingAction {
        val element = json.parseToJsonElement(nyxGet("discussion/$discussionId/rating/$postId"))
        val myRating = (element as? JsonObject)
            ?.get("my_rating")
            ?.jsonPrimitive
            ?.contentOrNull
        return RatingAction.entries.find { it.apiString == myRating } ?: RatingAction.NONE
    }

    private suspend fun nyxGet(endpoint: String): String {
        val urlString = "https://nyx.cz/api/$endpoint"
        log.info(urlString)
        val response = client.get(urlString) {
            headers {
                this.append("Authorization", "Bearer $nyxToken")
            }
        }
        return if (response.status.isSuccess()) {
            response.body()
        } else {
            throw IllegalStateException(
                "Unexpected response from nyx.cz: $response",
            )
        }
    }

    private suspend fun nyxPost(
        endpoint: String,
        content: Map<String, String> = emptyMap(),
    ): String {
        val urlString = "https://nyx.cz/api/$endpoint"
        log.info(urlString)
        val response = client.post(urlString) {
            headers {
                this.append("Authorization", "Bearer $nyxToken")
            }
            setBody(
                FormDataContent(
                    Parameters.build {
                        content.map { append(it.key, it.value) }
                    },
                ),
            )
        }
        return if (response.status.isSuccess()) {
            response.body()
        } else {
            throw IllegalStateException(
                "Unexpected response from nyx.cz: $response, ${response.body<String>()}",
            )
        }
    }
}
