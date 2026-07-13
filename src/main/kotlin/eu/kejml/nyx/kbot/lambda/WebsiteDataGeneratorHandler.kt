package eu.kejml.nyx.kbot.lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import eu.kejml.nyx.kbot.storage.BonusPoints
import eu.kejml.nyx.kbot.storage.DiscussionData
import eu.kejml.nyx.kbot.storage.Points
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import kotlin.time.Clock

class WebsiteDataGeneratorHandler : RequestHandler<Any?, String> {
    private val discussionId = System.getenv("DISCUSSION_ID").toLong()
    private val bucketName = System.getenv("R2_BUCKET_NAME")
    private val s3Client = S3Client.builder()
        .endpointOverride(URI.create(System.getenv("R2_ENDPOINT")))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    System.getenv("R2_ACCESS_KEY_ID"),
                    System.getenv("R2_SECRET_ACCESS_KEY"),
                ),
            ),
        )
        .region(Region.of("auto"))
        .build()

    override fun handleRequest(
        input: Any?,
        context: Context,
    ): String {
        context.logger.log("Generating website data for discussion $discussionId")

        val dataFrom = LocalDateTime(2022, 1, 1, 0, 0)
        val points = Points.getPointsFrom(discussionId, dataFrom)
        val bonusPoints = BonusPoints.getPointsFrom(discussionId, dataFrom)

        val data = DiscussionData(
            discussionId = discussionId,
            generatedAt = Clock.System.now().toString(),
            points = points,
            bonusPoints = bonusPoints,
        )

        val json = Json.encodeToString(data)
        val key = "data/$discussionId.json"

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("application/json")
            .build()

        s3Client.putObject(request, RequestBody.fromString(json))
        context.logger.log("Uploaded $key to bucket $bucketName (${points.size} points, ${bonusPoints.size} bonus points)")

        return "Generated data for ${points.size} points and ${bonusPoints.size} bonus points"
    }
}
