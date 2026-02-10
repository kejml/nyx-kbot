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

fun readPointsFromDiscussion(discussionId: Long): Int = runBlocking {
    log.info("Saving posts")
    val fromId = Points.getLastPostId(discussionId) ?: 1L
    val data = NyxClient.getDiscussion(discussionId, DiscussionQueryParams("bod -bodování", fromId))
    val discussion = json.decodeFromString<Discussion>(data)
    log.info(discussion.toString())
    val saved = mutableListOf<Long>()
    val points = discussion.posts
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
        .map {
            saved.add(it.postId)
            Points.addPoint(it)
            NyxClient.ratePost(discussionId, it.postId)
        }
        .count()
    val logMessage = "Done, latest index was $fromId, saved ${saved.size} new points (${saved.joinToString(", ")})"
    log.info(logMessage)
    points
}

fun List<Point>.validatePointsAndRemoveInvalid(validatePoints: Boolean): List<Point> = if (validatePoints) {
    runBlocking {
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
} else {
    this
}

fun postYearlySummary(discussionId: Long, year: Int) {
    val pointsTable = renderPointsPost(
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

    val pointsTableMonth = renderPointsPost(
        discussionId,
        LocalDateTime(year, month, 1, 0, 0),
        LocalDateTime(year, month + 1, 1, 0, 0)
            .toInstant(TimeZone.UTC).minus(1.seconds).toLocalDateTime(TimeZone.UTC),
    )

    val pointsTableYear = renderPointsPost(
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

fun updateHome(discussionId: Long, contentId: Long, year: Int) {
    val pointsTable = renderPointsTable(
        discussionId = discussionId,
        from = LocalDateTime(year, 1, 1, 0, 0),
        to = LocalDateTime(year, 12, 31, 23, 59, 59, 999),
        validatePoints = false,
    )
    postHeader(
        body =
        """
            <h3>Průběžné bodování v roce $year</h3>
            <br>
        """.trimIndent()
            .plus(pointsTable),
        discussionId = discussionId,
        contentId = contentId,
    )
}

fun updateHomeHallOfFame(discussionId: Long, contentId: Long?, lastYear: Int) {
    if (contentId == null) {
        log.info("No content id provided, skipping hall of fame")
        return
    }
    log.info("Getting data for year $lastYear")
    val pointsTable = renderPointsTable(
        discussionId = discussionId,
        from = LocalDateTime(lastYear, 1, 1, 0, 0),
        to = LocalDateTime(lastYear, 12, 31, 23, 59, 59, 999),
        validatePoints = false,
    )

    val hallOfFame = (2022 until lastYear).reversed().map { y ->
        log.info("Getting data for year $y")
        y to renderPointsTable(
            discussionId = discussionId,
            from = LocalDateTime(y, 1, 1, 0, 0),
            to = LocalDateTime(y, 12, 31, 23, 59, 59, 999),
            limitDisplayedPlaces = 5,
            validatePoints = false,
        )
    }.joinToString("\n") {
        """
            <h3>${it.first}</h3>
            <br>
            ${it.second}
            <br>
        """.trimIndent()
    }

    log.info("Will post results")

    return postHeader(
        """
            <h2>Výsledky za rok $lastYear</h2>
            <br>
        """.trimIndent()
            .plus(pointsTable)
            .plus("<br>")
            .plus("<h2>Síň slávy</h2>")
            .plus("<br>")
            .plus(hallOfFame),
        discussionId,
        contentId,
    )
}

fun postHeader(body: String, discussionId: Long, contentId: Long) {
    return runBlocking {
        NyxClient.updateHome(discussionId, contentId, body)
    }
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

private fun getGroupedPoints(
    discussionId: Long,
    from: LocalDateTime,
    to: LocalDateTime,
    validatePoints: Boolean = true,
): Map<Int, List<UserAndPoints>> = Points.getPointsBetween(discussionId, from, to)
    .validatePointsAndRemoveInvalid(validatePoints)
    .filter { it.givenTo != null }
    .groupBy { it.givenTo!! }
    .map { it.key to it.value }
    .groupBy({ it.second.size }) { pair -> UserAndPoints(pair.first, pair.second) }
    .toSortedMap(reverseOrder())

private fun renderPointsPost(
    discussionId: Long,
    from: LocalDateTime,
    to: LocalDateTime,
    limitDisplayedPlaces: Int = Int.MAX_VALUE,
    validatePoints: Boolean = true,
): String {
    var globalOrder = 1
    val pointsToUser = getGroupedPoints(discussionId, from, to, validatePoints)

    return pointsToUser.entries.joinToString("\n") { numToUserPointsMap ->
        if (globalOrder > limitDisplayedPlaces) return@joinToString ""
        val numberOfUsers = numToUserPointsMap.value.size
        val resultLine = numToUserPointsMap.value.sortedBy { it.userName }.joinToString("\n") { userAndPoints ->
            """
                ${determineOrder(numberOfUsers, globalOrder).padEndHtml(27)}
                ${userAndPoints.userName.padEndHtml(28)}
                ${numToUserPointsMap.key}
                <br>
            """.trimIndent()
        }
        globalOrder += numberOfUsers
        resultLine
    }
}

private fun renderPointsTable(
    discussionId: Long,
    from: LocalDateTime,
    to: LocalDateTime,
    limitDisplayedPlaces: Int = Int.MAX_VALUE,
    validatePoints: Boolean = true,
): String {
    var globalOrder = 1
    var rowIndex = 0
    val pointsToUser = getGroupedPoints(discussionId, from, to, validatePoints)

    val cellStyle = "border: 1px solid #ddd; padding: 8px; text-align: left;"
    val tableStyle = "border-collapse: collapse; width: 300px;"
    val headerStyle = "$cellStyle background-color: rgba(0, 0, 0, 0.05);"
    val narrowHeaderStyle = "$cellStyle width: 1px; white-space: nowrap; background-color: rgba(0, 0, 0, 0.05);"

    val tableRows = pointsToUser.entries.joinToString("") { numToUserPointsMap ->
        if (globalOrder > limitDisplayedPlaces) return@joinToString ""
        val numberOfUsers = numToUserPointsMap.value.size
        val rows = numToUserPointsMap.value.sortedBy { it.userName }.joinToString("") { userAndPoints ->
            val orderDisplay = determineOrder(numberOfUsers, globalOrder)
            val rowStyle = if (rowIndex % 2 == 0) "" else "background-color: rgba(0, 0, 0, 0.08);"
            rowIndex++
            """
                <tr style="$rowStyle">
                    <td style="$cellStyle">$orderDisplay</td>
                    <td style="$cellStyle">${userAndPoints.userName}</td>
                    <td style="$cellStyle">${numToUserPointsMap.key}</td>
                </tr>
            """.trimIndent()
        }
        globalOrder += numberOfUsers
        rows
    }

    return """
        <table style="$tableStyle">
            <thead>
                <tr>
                    <th style="$narrowHeaderStyle">Pořadí</th>
                    <th style="$headerStyle">ID</th>
                    <th style="$narrowHeaderStyle">Body</th>
                </tr>
            </thead>
            <tbody>
                $tableRows
            </tbody>
        </table>
    """.trimIndent()
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
        "${medalsAndRanks(globalOrder, globalOrder + numberOfUsers - 1)}."
    }

private fun addMedal(order: Int): String {
    return when (order) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> " "
    }
}

private fun medalsAndRanks(from: Int, to: Int): String =
    addMedals(from, to) + "$from.-$to"

private fun addMedals(from: Int, to: Int): String =
    (from..to).intersect(1..3)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("&#8288;", postfix = "&nbsp;") { addMedal(it) }
        .orEmpty()
