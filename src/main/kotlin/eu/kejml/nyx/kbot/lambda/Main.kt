package eu.kejml.nyx.kbot.lambda

import eu.kejml.nyx.kbot.api.Nyx
import eu.kejml.nyx.kbot.api.Params
import eu.kejml.nyx.kbot.data.Discussion
import eu.kejml.nyx.kbot.storage.Point
import eu.kejml.nyx.kbot.storage.Points
import eu.kejml.nyx.kbot.storage.tableName
import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.DynamoDBTable
import io.kotless.dsl.lang.http.Get
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
object Main {
    val client = HttpClient()
    val log = LoggerFactory.getLogger(this.javaClass)


    private val discussionId = 20310L

    @Get("/hello")
    fun root(): String {
        return "Hello world!"
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Get("/nyx-test")
    fun nyxTest(): String {

        return runBlocking {
            val data = Nyx.getDiscussion(discussionId, Params(null, 1))
            val discussion = json.decodeFromString<Discussion>(data)

            // TODO more points in one post? Point surrounded by text?
            discussion.posts.filter {
                it.content.contains(Regex("<(b|strong)>(<em.*>)?bod(</em>)?</(b|strong)>", RegexOption.IGNORE_CASE))
            }.toString()
            data
        }
    }

    // TODO more points in one post? Other text beside point
    private fun String.parsePointData(): Pair<Long, String> {
        //<a class=r data-id=54606485 data-discussion-id=20310 href='/discussion/20310/id/54606485'>DEVNOK</a>: <b>BOD</b>
        val questionId = this.split(" ").first { it.startsWith("data-id") }.substringAfter("=").toLong()
        val givenTo = this.substringBefore("</a>").substringAfterLast('>')
        return questionId to givenTo
    }

    @Get("/save-points")
    fun savePoints(): String = runBlocking {
        val fromId = Points.getLastPostId(discussionId) ?: 1L
        val data = Nyx.getDiscussion(discussionId, Params("bod", fromId))
        val discussion = json.decodeFromString<Discussion>(data)
        log.info(discussion.toString())
        val saved = mutableListOf<Long>();
        discussion.posts
            .filter { it.id > fromId }
            .filter {
                it.content.contains(
                    Regex(
                        "<(b|strong)>(<em.*>)?bod(</em>)?</(b|strong)>",
                        RegexOption.IGNORE_CASE
                    )
                )
            }
            .map {
                val (questionId, givenTo) = it.content.parsePointData()
                Point(discussionId, it.id, givenTo, it.insertedAt, questionId, it.username)
            }.forEach {
                saved.add(it.id)
                Points.addPoint(it)
            }
        val logMessage = "Done, latest index was $fromId, saved ${saved.size} new points (${saved.joinToString(", ")})"
        log.info(logMessage)
        logMessage
    }

    @Get("/get-point")
    fun getPoint(id: Long): String {
        return Points.getAPoint(id)?.toString() ?: "Not found!"
    }

    @Get("/get-points")
    fun getPoints(): String {
        log.info("Getting some data...")
        return Points.getPointsBetween(
            discussionId,
            LocalDateTime(2022, 2, 19, 17, 5),
            LocalDateTime(2022, 2, 19, 17, 6)
        ).joinToString("<br>\n\n")
    }
}
