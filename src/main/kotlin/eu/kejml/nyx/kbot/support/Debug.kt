package eu.kejml.nyx.kbot.support

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

suspend fun main() {
    val client = HttpClient()

    val response: String = client
        .post("https://nyx.cz/api/create_token/KBOT") {
            headers {
                append(HttpHeaders.UserAgent, "KBOT")
            }
        }.body()

    println(response)
}

// SANDBOX
private val discussionId = 20310L
private val contentId = 54996L

fun nyxTest(): String = runBlocking {
//        val discussion = NyxClient.getDiscussion(discussionId, DiscussionQueryParams(null, 1))

    val yesterday = Clock.System
        .now()
        .minus(1.days)
        .toLocalDateTime(TimeZone.UTC)
    // postYearlySummary(discussionId, yesterday.year)
    // val res = updateHomeHallOfFame(discussionId, contentId, yesterday.year)

    // TODO more points in one post? Point surrounded by text?
//        discussion.posts.filter {
//            it.content.contains(Regex("<(b|strong)>(<em.*>)?bod(</em>)?</(b|strong)>", RegexOption.IGNORE_CASE))
//        }.toString()
//        data
    "Yesterday was $yesterday"
}
