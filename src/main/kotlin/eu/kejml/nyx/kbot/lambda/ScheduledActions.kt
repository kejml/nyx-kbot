package eu.kejml.nyx.kbot.lambda

import eu.kejml.nyx.kbot.storage.postYearSummary
import eu.kejml.nyx.kbot.storage.readPointsFromDiscussion
import io.kotless.dsl.lang.event.Scheduled
import io.kotless.dsl.lang.http.Get

object ScheduledActions {
    private val discussionId = 20310L // SANDBOX
    //private val discussionId = 11354L // PROD

    @Scheduled(Scheduled.everyHour)
    fun updatePointsInDb() {
        readPointsFromDiscussion(discussionId)
    }

    @Scheduled("32 18 * * ? *")
    fun postSummary() {
        postYearSummary(discussionId, 2022)
    }

    // This is needed because kotless doesn't generate a valid deployment without rest endpoints
    @Get("/hello")
    fun test(): String {
        return "Hello"
    }
}