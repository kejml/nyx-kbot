package eu.kejml.nyx.kbot.lambda

import eu.kejml.nyx.kbot.api.Nyx
import eu.kejml.nyx.kbot.data.Discussion
import eu.kejml.nyx.kbot.storage.Point
import eu.kejml.nyx.kbot.storage.Points
import eu.kejml.nyx.kbot.storage.tableName
import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.DynamoDBTable
import io.kotless.dsl.lang.http.Get
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
object Main {
    val client = HttpClient()

    @Get("/hello")
    fun root(): String {
        return "Hello world!"
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Get("/nyx-test")
    fun nyxTest(): String = runBlocking {
        val data = Nyx.getDiscussion("20310")
        val discussion = json.decodeFromString<Discussion>(data)
        discussion.posts.filter { it.content.contains(Regex("<(b|strong)>bod</(b|strong)>", RegexOption.IGNORE_CASE)) }.toString()
    }


    fun String.parsePointData(): Pair<Long, String> {
        //<a class=r data-id=54606485 data-discussion-id=20310 href='/discussion/20310/id/54606485'>DEVNOK</a>: <b>BOD</b>
        val questionId = this.split(" ").first { it.startsWith("data-id") }.substringAfter("=").toLong()
        val givenTo = this.substringBefore("</a>").substringAfterLast('>')
        return questionId to givenTo
    }

    @Get("/save-points")
    fun savePoints(): String = runBlocking {
        val data = Nyx.getDiscussion("20310")
        val discussion = json.decodeFromString<Discussion>(data)
        discussion.posts
            .filter { it.content.contains(Regex("<(b|strong)>bod</(b|strong)>", RegexOption.IGNORE_CASE)) }
            .map {
                val (questionId, givenTo) = it.content.parsePointData()
                Point(it.id, givenTo, it.insertedAt, questionId, it.username)
            }
            .forEach { Points.addPoint(it) }
        "Done"
    }

    @Get("/get-point")
    fun getPoint(id: Long): String {
        return Points.getAPoint(id)?.toString() ?: "Not found!"
    }
}