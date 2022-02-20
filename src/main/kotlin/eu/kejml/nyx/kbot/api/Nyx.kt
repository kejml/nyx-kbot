package eu.kejml.nyx.kbot.api

import eu.kejml.nyx.kbot.lambda.Main
import io.ktor.client.request.*
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.*

enum class DiscussionOrder(val urlString: String) {

    NEWER_THAN("newer_than"),
    OLDER_THAN("older_than"),
}

class Params(
    private val text: String? = null,
    private val fromId: Long? = null,
    val discussionOrder: DiscussionOrder = DiscussionOrder.NEWER_THAN
) {
    private fun isEmpty() = text == null && fromId == null

    fun toUrl(): String {
        if (isEmpty()) return ""

        return listOfNotNull(
            text?.let { "text=$it" },
            fromId?.let { "from_id=$it&order=${discussionOrder.urlString}" },
        ).joinToString(separator = "&", prefix = "?")

    }
}

object Nyx {
    val secretStream: InputStream? = this.javaClass.classLoader.getResourceAsStream("secret.properties")
    val props = Properties().apply { load(secretStream) }
    val nyxToken = props["nyx_token"]

    val log = LoggerFactory.getLogger(this.javaClass)

    suspend fun getDiscussion(id: Long, params: Params? = null): String {
        return nyxGet("discussion/$id${params?.toUrl() ?: ""}")
    }

    suspend fun nyxGet(endpoint: String): String {
        val urlString = "https://nyx.cz/api/$endpoint"
        log.info(urlString)
        return Main.client.get(urlString) {
            headers {
                this.append("Authorization", "Bearer $nyxToken")
            }
        }
    }
}