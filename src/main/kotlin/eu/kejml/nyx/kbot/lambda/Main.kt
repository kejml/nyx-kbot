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


    private val discussionId = 20310L // SANDBOX
    //private val discussionId = 11354L // PROD

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
        //<a href="/discussion/11354/id/47179434" class="r" data-discussion-id=11354 data-id=47179434>KOCMOC</a>: <b><em class='search-match'>BOD</em></b>
        val questionId = this.split(" ", ">").first { it.startsWith("data-id") }.substringAfter("=").toLong()
        val givenTo = this.substringBefore("</a>").substringAfterLast('>')
        return questionId to givenTo
    }

    @Get("/save-points")
    fun savePoints(): String = runBlocking {
        log.info("Saving posts")
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
            .mapNotNull {
                try {
                    val (questionId, givenTo) = it.content.parsePointData()
                    Point(discussionId, it.id, givenTo, it.insertedAt, questionId, it.username)
                } catch (ex: Exception) {
                    log.warn("Could not parse content:\n ${it.content}")
                    null
                }
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

    @Get("/post")
    fun post(year: Int) {
        val userToPoints = Points.getPointsBetween(
            11354L, // discussionId,
            LocalDateTime(year, 1, 1, 0, 0),
            LocalDateTime(year, 12, 31, 23, 59, 59, 999)
        ).groupBy { it.givenTo }.map { it.key to it.value.size }.sortedByDescending { it.second }
        val content = """
            <i>Testovací provoz!</i>
            
            Vyhodnocení bodování za rok <b>$year</b>:
            
            <table style="width: 300px">
            <tr><th>Pořadí</th><th>ID</th><th>Počet bodů</th></tr>
        """.trimIndent().plus(
            userToPoints.mapIndexed { index, pair -> "<tr><td>${index + 1}</td><td>${pair.first}</td><td>${pair.second}</td></tr>" }.joinToString("")
                .plus("</table>")
        )
        return runBlocking {
            Nyx.postDiscussion(discussionId, content)
        }
    }

}
