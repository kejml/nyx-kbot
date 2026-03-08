package eu.kejml.nyx.kbot.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import eu.kejml.nyx.kbot.storage.DiscussionData
import eu.kejml.nyx.kbot.storage.Points
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

class WebsiteDataGeneratorHandler : RequestHandler<ScheduledEvent, String> {
    private val discussionId = System.getenv("DISCUSSION_ID").toLong()
    private val bucketName = System.getenv("WEBSITE_BUCKET_NAME")
    private val s3Client = S3Client.builder().build()

    override fun handleRequest(
        input: ScheduledEvent,
        context: Context,
    ): String {
        context.logger.log("Generating website data for discussion $discussionId")

        val points = Points.getPointsFrom(discussionId, LocalDateTime(2022, 1, 1, 0, 0))

        val data = DiscussionData(
            discussionId = discussionId,
            generatedAt = Clock.System.now().toString(),
            points = points,
        )

        val json = Json.encodeToString(data)
        val key = "data/$discussionId.json"

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("application/json")
            .build()

        s3Client.putObject(request, RequestBody.fromString(json))
        context.logger.log("Uploaded $key to bucket $bucketName (${points.size} points)")

        return "Generated data for ${points.size} points"
    }
}
