package eu.kejml.nyx.kbot.support

import eu.kejml.nyx.kbot.api.Discussion
import eu.kejml.nyx.kbot.api.DiscussionQueryParams
import eu.kejml.nyx.kbot.api.NyxClient
import io.kotless.dsl.lang.http.Get
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

suspend fun main() {
    val client = HttpClient()

    val response: String = client.post("https://nyx.cz/api/create_token/KBOT") {
        headers {
            append(HttpHeaders.UserAgent, "KBOT")
        }
    }

    println(response)
}

private val json = Json { ignoreUnknownKeys = true }

private val discussionId = 20310L // SANDBOX

@Get("/nyx-test")
fun nyxTest(): String {
    return runBlocking {
        val data = NyxClient.getDiscussion(discussionId, DiscussionQueryParams(null, 1))
        val discussion = json.decodeFromString<Discussion>(data)

        // TODO more points in one post? Point surrounded by text?
        discussion.posts.filter {
            it.content.contains(Regex("<(b|strong)>(<em.*>)?bod(</em>)?</(b|strong)>", RegexOption.IGNORE_CASE))
        }.toString()
        data
    }
}
