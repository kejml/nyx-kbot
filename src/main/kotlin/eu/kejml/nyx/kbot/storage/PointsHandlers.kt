package eu.kejml.nyx.kbot.storage

import eu.kejml.nyx.kbot.api.Discussion
import eu.kejml.nyx.kbot.api.DiscussionQueryParams
import eu.kejml.nyx.kbot.api.NyxClient
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.format.TextStyle
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("PointsHandlers")
private val json = Json { ignoreUnknownKeys = true }

// TODO more points in one post? Other text beside point
private fun String.parsePointData(): Pair<Long, String> {
    //<a class=r data-id=54606485 data-discussion-id=20310 href='/discussion/20310/id/54606485'>DEVNOK</a>: <b>BOD</b>
    //<a href="/discussion/11354/id/47179434" class="r" data-discussion-id=11354 data-id=47179434>KOCMOC</a>: <b><em class='search-match'>BOD</em></b>
    val questionId = this.split(" ", ">").first { it.startsWith("data-id") }.substringAfter("=").toLong()
    val givenTo = this.substringBefore("</a>").substringAfterLast('>')
    return questionId to givenTo
}

fun readPointsFromDiscussion(discussionId: Long): String = runBlocking {
    log.info("Saving posts")
    val fromId = Points.getLastPostId(discussionId) ?: 1L
    val data = NyxClient.getDiscussion(discussionId, DiscussionQueryParams("bod", fromId))
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


fun postYearlySummary(discussionId: Long, year: Int) {
    postSummary(
        discussionId,
        "Vyhodnocení bodování za rok <b>$year</b>:",
        LocalDateTime(year, 1, 1, 0, 0),
        LocalDateTime(year, 12, 31, 23, 59, 59, 999),
    )
}

fun postMonthlySummary(discussionId: Long, month: Month, year: Int) {
    if (month == Month.DECEMBER) throw IllegalArgumentException("Send year summary in December!")

    val monthString = month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("cs"))

    postSummary(
        discussionId,
        "Vyhodnocení bodování za měsíc <b>$monthString $year</b>:",
        LocalDateTime(year, month, 1, 0, 0),
        LocalDateTime(year, month + 1, 1, 0, 0)
            .toInstant(TimeZone.UTC).minus(1.seconds).toLocalDateTime(TimeZone.UTC),
    )
}

fun postSummary(discussionId: Long, intro: String, from: LocalDateTime, to: LocalDateTime) {
    val pointsToUser = Points.getPointsBetween(
        discussionId,
        from,
        to
    ).groupBy { it.givenTo }
        .map { it.key to it.value.size }
        .groupBy({it.second}) { it.first }
    var globalOrder = 1 // Good enough now
    val content = """
            <i>Testovací provoz!</i>
            
            $intro
            
            <table style="width: 300px">
            <tr><th>Pořadí</th><th>ID</th><th>Počet bodů</th></tr>
        """.trimIndent().plus(
        pointsToUser.entries.joinToString("\n") { pair ->
            val numberOfUsers = pair.value.size
            val resultLine =
                pair.value.sorted().joinToString("\n") { user ->
                    "<tr><td>${determineOrder(numberOfUsers, globalOrder)}.</td><td>${user}</td><td>${pair.key}</td></tr>"
            }
            globalOrder += numberOfUsers
            resultLine
        }
            .plus("""
                </table>
                
                <small><i>Veškeré stížnosti a jinou zpětnou vazbu směřujte prosím na ID KEJML</i></small>
            """.trimIndent())
    )
    return runBlocking {
        NyxClient.postDiscussion(discussionId, content)
    }
}

private fun determineOrder(numberOfUsers: Int, globalOrder: Int) =
    if (numberOfUsers == 1) globalOrder else "$globalOrder.-${globalOrder + numberOfUsers - 1}"
