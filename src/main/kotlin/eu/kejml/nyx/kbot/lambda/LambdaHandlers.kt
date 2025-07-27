package eu.kejml.nyx.kbot.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import eu.kejml.nyx.kbot.storage.postMonthlySummary
import eu.kejml.nyx.kbot.storage.postYearlySummary
import eu.kejml.nyx.kbot.storage.readPointsFromDiscussion
import eu.kejml.nyx.kbot.storage.updateHome
import eu.kejml.nyx.kbot.support.nyxTest
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

class HourlyUpdateHandler : RequestHandler<ScheduledEvent, String> {
//    private val discussionId = 11354L // PROD

    // SANDBOX
    private val discussionId = 20310L
    private val contentId = 68692L

    override fun handleRequest(input: ScheduledEvent, context: Context): String {
        context.logger.log("Starting hourly points update")
        return runBlocking {
            val foundPoints = readPointsFromDiscussion(discussionId)
            val updateResult = if (foundPoints > 0) {
                val today = Clock.System.now().minus(1.days).toLocalDateTime(TimeZone.UTC)
                updateHome(discussionId, contentId, today.year)
            } else {
                null
            }

            "$foundPoints - $updateResult"
        }
    }
}

class MonthlySummaryHandler : RequestHandler<ScheduledEvent, String> {
//    private val discussionId = 11354L // PROD
    private val discussionId = 20310L // SANDBOX

    override fun handleRequest(input: ScheduledEvent, context: Context): String {
        context.logger.log("Starting monthly summary")
        return runBlocking {
            val yesterday = Clock.System.now().minus(1.days).toLocalDateTime(TimeZone.UTC)
            postMonthlySummary(discussionId, yesterday.month, yesterday.year)
            "Monthly summary posted"
        }
    }
}

class YearlySummaryHandler : RequestHandler<ScheduledEvent, String> {
//    private val discussionId = 11354L //
    private val discussionId = 20310L // SANDBOX// PROD

    override fun handleRequest(input: ScheduledEvent, context: Context): String {
        context.logger.log("Starting yearly summary")
        return runBlocking {
            val yesterday = Clock.System.now().minus(1.days).toLocalDateTime(TimeZone.UTC)
            postYearlySummary(discussionId, yesterday.year)
            "Yearly summary posted"
        }
    }
}

class HelloHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        return APIGatewayProxyResponseEvent().apply {
            statusCode = 200
            headers = mapOf("Content-Type" to "text/plain")
            body = Clock.System.now().toString()
        }
    }
}

class NyxTestHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        return runBlocking {
            try {
                val result = nyxTest()
                APIGatewayProxyResponseEvent().apply {
                    statusCode = 200
                    headers = mapOf("Content-Type" to "application/json")
                    body = result
                }
            } catch (e: Exception) {
                context.logger.log("Error in nyx-test: ${e.message}")
                APIGatewayProxyResponseEvent().apply {
                    statusCode = 500
                    headers = mapOf("Content-Type" to "text/plain")
                    body = "Error: ${e.message}"
                }
            }
        }
    }
}
