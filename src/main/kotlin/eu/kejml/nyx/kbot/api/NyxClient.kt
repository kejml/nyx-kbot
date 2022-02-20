package eu.kejml.nyx.kbot.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.*

enum class DiscussionOrder(val apiString: String) {
    NEWER_THAN("newer_than"),
    OLDER_THAN("older_than"),
}

enum class PostFormat(val apiString: String) {
    TEXT("text"),
    HTML("html"),
}

class DiscussionQueryParams(
    private val text: String? = null,
    private val fromId: Long? = null,
    private val discussionOrder: DiscussionOrder = DiscussionOrder.NEWER_THAN
) {
    private fun isEmpty() = text == null && fromId == null

    fun toUrl(): String {
        if (isEmpty()) return ""

        return listOfNotNull(
            text?.let { "text=$it" },
            fromId?.let { "from_id=$it&order=${discussionOrder.apiString}" },
        ).joinToString(separator = "&", prefix = "?")

    }
}

object NyxClient {
    private val secretStream: InputStream? = this.javaClass.classLoader.getResourceAsStream("secret.properties")
    private val props = Properties().apply { load(secretStream) }
    private val nyxToken = props["nyx_token"]
    private val client = HttpClient()

    private val log = LoggerFactory.getLogger(this.javaClass)

    suspend fun getDiscussion(id: Long, params: DiscussionQueryParams? = null): String {
        return nyxGet("discussion/$id${params?.toUrl() ?: ""}")
    }

    suspend fun postDiscussion(discussionId: Long, content: String, format: PostFormat = PostFormat.HTML): String {
        return nyxPost("discussion/$discussionId/send/text", mapOf(
            "content" to content,
            "format" to format.apiString,
        ))
    }

    private suspend fun nyxGet(endpoint: String): String {
        val urlString = "https://nyx.cz/api/$endpoint"
        log.info(urlString)
        return client.get(urlString) {
            headers {
                this.append("Authorization", "Bearer $nyxToken")
            }
        }
    }

    private suspend fun nyxPost(endpoint: String, content: Map<String, String>): String {
        val urlString = "https://nyx.cz/api/$endpoint"
        log.info(urlString)
        return client.post(urlString) {
            headers {
                this.append("Authorization", "Bearer $nyxToken")
            }
            body = FormDataContent(Parameters.build {
                content.map { append(it.key, it.value) }
            })
        }
    }
}