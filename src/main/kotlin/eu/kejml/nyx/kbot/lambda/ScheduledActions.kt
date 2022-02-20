package eu.kejml.nyx.kbot.lambda

import eu.kejml.nyx.kbot.storage.postMonthlySummary
import eu.kejml.nyx.kbot.storage.postYearlySummary
import eu.kejml.nyx.kbot.storage.readPointsFromDiscussion
import io.kotless.dsl.lang.event.Scheduled
import io.kotless.dsl.lang.http.Get
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

object ScheduledActions {
    private val testDiscussionId = 20310L // SANDBOX
    private val discussionId = 11354L // PROD

    @Scheduled(Scheduled.everyHour)
    fun updatePointsInDb() {
        readPointsFromDiscussion(discussionId)
    }

    @Scheduled("10 01 1 2-12 ? *")
    fun monthlySummary() {
        val yesterday = Clock.System.now().minus(1.days).toLocalDateTime(TimeZone.UTC)
        postMonthlySummary(discussionId, yesterday.month, yesterday.year)
    }

    @Scheduled("10 01 1 1 ? *")
    fun yearlySummary() {
        val yesterday = Clock.System.now().minus(1.days).toLocalDateTime(TimeZone.UTC)
        postYearlySummary(discussionId, yesterday.year)
    }

    // Something is needed because kotless doesn't generate a valid deployment without rest endpoints
    @Get("/hello")
    fun test(): String {
        return Clock.System.now().toString()
    }
}