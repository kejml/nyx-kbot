package eu.kejml.nyx.kbot.lambda

import eu.kejml.nyx.kbot.storage.postMonthlySummary
import eu.kejml.nyx.kbot.storage.postYearlySummary
import eu.kejml.nyx.kbot.storage.readPointsFromDiscussion
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

// Legacy object - functionality moved to individual Lambda handlers
@Deprecated("Use individual Lambda handlers instead")
object ScheduledActions {
    private val testDiscussionId = 20310L // SANDBOX
    private val discussionId = 11354L // PROD

    fun updatePointsInDb() {
        readPointsFromDiscussion(discussionId)
    }

    fun monthlySummary() {
        val yesterday = Clock.System
            .now()
            .minus(1.days)
            .toLocalDateTime(TimeZone.UTC)
        postMonthlySummary(discussionId, yesterday.month, yesterday.year)
    }

    fun yearlySummary() {
        val yesterday = Clock.System
            .now()
            .minus(1.days)
            .toLocalDateTime(TimeZone.UTC)
        postYearlySummary(discussionId, yesterday.year)
    }

    fun test(): String = Clock.System.now().toString()
}
