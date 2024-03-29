package eu.kejml.nyx.kbot.storage

import eu.kejml.nyx.kbot.api.Discussion
import eu.kejml.nyx.kbot.api.DiscussionOrder
import eu.kejml.nyx.kbot.api.DiscussionQueryParams
import eu.kejml.nyx.kbot.api.NyxClient
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.format.TextStyle
import java.util.*
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("PointsHandlers")
private val json = Json { ignoreUnknownKeys = true }

internal data class QuestionIdGivenTo(val questionId: Long, val givenTo: String)

internal fun String.parsePointData(): List<QuestionIdGivenTo> {
    // <a class=r data-id=54606485 data-discussion-id=20310 href='/discussion/20310/id/54606485'>DEVNOK</a>: <b>BOD</b>
    // <a href="/discussion/11354/id/47179434" class="r" data-discussion-id=11354 data-id=47179434>KOCMOC</a>: <b><em class='search-match'>BOD</em></b>
    return this.split("<br>", "<br/>", "\n")
        .filter {
            log.info("Running regex on $it")
            it.contains(
                Regex(
                    """^<a.*data-id.*>:.*<(b|strong)> *(<em.*>)? *bod *(</em>)? *</?(b|strong)>""",
                    RegexOption.IGNORE_CASE,
                ),
            ) || it.matches(Regex("""^<a.*data-id.*>: BOD$""", RegexOption.IGNORE_CASE))
        }
        .filter {
            log.info("Running second regex on $it")
            it.startsWith("<a")
        }
        .map {
            val questionId = it.split(" ", ">").first { it.startsWith("data-id") }.substringAfter("=").toLong()
            val givenTo = it.substringBefore("</a>").substringAfterLast('>')
            QuestionIdGivenTo(questionId, givenTo)
        }.toList()
}

fun readPointsFromDiscussion(discussionId: Long): String = runBlocking {
    log.info("Saving posts")
    val fromId = Points.getLastPostId(discussionId) ?: 1L
    val data = NyxClient.getDiscussion(discussionId, DiscussionQueryParams("bod -bodování", fromId))
    val discussion = json.decodeFromString<Discussion>(data)
    log.info(discussion.toString())
    val saved = mutableListOf<Long>()
    discussion.posts
        .filter { it.id > fromId }
        .map { post ->
            try {
                post.content.parsePointData().map {
                    Point(discussionId, post.id, it.givenTo, post.insertedAt, it.questionId, post.username)
                }.toList()
            } catch (ex: Exception) {
                log.warn("Could not parse content:\n ${post.content}")
                emptyList()
            }
        }
        .flatten()
        .forEach {
            saved.add(it.postId)
            Points.addPoint(it)
            NyxClient.ratePost(discussionId, it.postId)
        }
    val logMessage = "Done, latest index was $fromId, saved ${saved.size} new points (${saved.joinToString(", ")})"
    log.info(logMessage)
    logMessage
}

fun List<Point>.validatePointsAndRemoveInvalid(): List<Point> = runBlocking {
    filter { point ->
        val data = NyxClient.getDiscussion(point.discussionId, DiscussionQueryParams(fromId = point.postId + 1, discussionOrder = DiscussionOrder.OLDER_THAN))
        val posts = json.decodeFromString<Discussion>(data).posts
        val result = posts.first().id == point.postId
        if (!result) {
            log.info("Removing point $point - not found in the discussion anymore. (Found only posts with ids: ${posts.map { it.id }}")
            Points.removePoint(point)
        }
        result
    }
}

fun postYearlySummary(discussionId: Long, year: Int) {
    val pointsTable = renderPointsTable(
        discussionId,
        LocalDateTime(year, 1, 1, 0, 0),
        LocalDateTime(year, 12, 31, 23, 59, 59, 999),
    )
    postSummary(
        """
        Vyhodnocení bodování za rok <b>$year</b>:<br>
        <br>
        """.trimIndent().plus(
            pointsTable,
        ),
        discussionId,
    )
}

fun postMonthlySummary(discussionId: Long, month: Month, year: Int) {
    if (month == Month.DECEMBER) throw IllegalArgumentException("Send year summary in December!")

    val monthString = month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("cs"))

    val pointsTableMonth = renderPointsTable(
        discussionId,
        LocalDateTime(year, month, 1, 0, 0),
        LocalDateTime(year, month + 1, 1, 0, 0)
            .toInstant(TimeZone.UTC).minus(1.seconds).toLocalDateTime(TimeZone.UTC),
    )

    val pointsTableYear = renderPointsTable(
        discussionId,
        LocalDateTime(year, 1, 1, 0, 0),
        LocalDateTime(year, month + 1, 1, 0, 0)
            .toInstant(TimeZone.UTC).minus(1.seconds).toLocalDateTime(TimeZone.UTC),
        10,
    )

    postSummary(
        """
        Vyhodnocení bodování za měsíc <b>$monthString $year</b>:<br>
        <br>
        """.trimIndent()
            .plus(pointsTableMonth)
            .plus("<br>Top 10 průběžné pořadí za rok $year:<br><br>")
            .plus(pointsTableYear),
        discussionId,
    )
}

fun postSummary(body: String, discussionId: Long) {
    val content = """
            $body
            <br>
            <br>
            <small><i>Veškeré stížnosti a jinou zpětnou vazbu směřujte prosím na ID KEJML nebo na <a href="https://github.com/kejml/nyx-kbot">Github</a>.</i></small>
    """.trimIndent()
    return runBlocking {
        NyxClient.postDiscussion(discussionId, content)
    }
}

private fun renderPointsTable(
    discussionId: Long,
    from: LocalDateTime,
    to: LocalDateTime,
    limitDisplayedPlaces: Int = Int.MAX_VALUE,
): String {
    var globalOrder = 1 // Good enough now

    val pointsToUser = Points.getPointsBetween(
        discussionId,
        from,
        to,
    )
        .validatePointsAndRemoveInvalid()
        .filter { it.givenTo != null }
        .groupBy { it.givenTo!! }
        .map { it.key to it.value }
        .groupBy({ it.second.size }) { pair -> UserAndPoints(pair.first, pair.second) }
        .toSortedMap(reverseOrder())

    return pointsToUser.entries.joinToString("\n") { numToUserPointsMap ->
        if (globalOrder > limitDisplayedPlaces) return@joinToString ""
        val numberOfUsers = numToUserPointsMap.value.size
        val resultLine = numToUserPointsMap.value.sortedBy { it.userName }.joinToString("\n") { userAndPoints ->
            """
                ${determineOrder(numberOfUsers, globalOrder).padEndHtml(27)}
                ${userAndPoints.userName.padEndHtml(28)}
                ${numToUserPointsMap.key} ${userAndPoints.points.toPointLinks()}
                <br>
            """.trimIndent()
        }
        globalOrder += numberOfUsers
        resultLine
    }
}

data class UserAndPoints(val userName: String, val points: List<Point>)

fun List<Point>.toPointLinks(): String = joinToString("") { "{reply .|${it.postId}}" }

/**
 * Ugly hack to almost align columns - some less capable mobile clients don't fully support HTML in posts,
 * so simple table can't be used.
 */
private fun String.padEndHtml(length: Int): String {
    val count = this.chunked(2).count { it == "🥇" || it == "🥈" || it == "🥉" }
    return this.padEnd(length - count - (this.length / 1.2).toInt()).replace(" ", "&nbsp;")
}

private fun determineOrder(numberOfUsers: Int, globalOrder: Int) =
    if (numberOfUsers == 1) {
        "${addMedal(globalOrder)}$globalOrder."
    } else {
        "${addMedals(globalOrder, globalOrder + numberOfUsers - 1)}."
    }

private fun addMedal(order: Int): String {
    return when (order) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> " "
    }
}

private fun addMedals(from: Int, to: Int): String {
    return "${(from..to).intersect(1..3).joinToString("") { addMedal(it) }} $from.-$to"
}
