package eu.kejml.nyx.kbot.storage

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException

const val TABLE_NAME = "points"

@Serializable
data class Point(
    val discussionId: Long,
    val postId: Long,
    val givenTo: String?,
    val givenDateTime: LocalDateTime?,
    val questionId: Long?,
    val givenBy: String?,
)

@Serializable
data class DiscussionData(
    val discussionId: Long,
    val generatedAt: String,
    val points: List<Point>,
)

fun fromAttributeValues(input: Map<String, AttributeValue>): Point {
    println(input)
    return Point(
        discussionId = input["discussionId"]?.n()?.toLong() ?: throw IllegalArgumentException("Missing attribute discussionId"),
        postId = input["postId"]?.n()?.toLong() ?: throw IllegalArgumentException("Missing attribute id"),
        givenTo = input["givenTo"]?.s(),
        givenDateTime = input["givenDateTime"]?.s()?.toLocalDateTime(),
        questionId = input["questionId"]?.n()?.toLong(),
        givenBy = input["givenBy"]?.s(),
    )
}

object Points : PointsStorage {
    private val client = DynamoDbClient.builder().build()
    private val log = LoggerFactory.getLogger(this.javaClass)

    internal fun getLastPostId(discussionId: Long): Long? {
        val request = QueryRequest
            .builder()
            .tableName(TABLE_NAME)
            .indexName("lastId")
            .keyConditionExpression("discussionId = :discussionId")
            .expressionAttributeValues(
                mapOf(
                    ":discussionId" to AttributeValue.builder().n(discussionId.toString()).build(),
                ),
            )
            .scanIndexForward(false)
            .limit(1)
            .build()

        return client.query(request).items()?.let { item -> item.getOrNull(0)?.let { fromAttributeValues(it) }?.postId }
    }

    internal fun getFirstPostId(discussionId: Long): Long? {
        val request = QueryRequest
            .builder()
            .tableName(TABLE_NAME)
            .indexName("lastId")
            .keyConditionExpression("discussionId = :discussionId")
            .expressionAttributeValues(
                mapOf(
                    ":discussionId" to AttributeValue.builder().n(discussionId.toString()).build(),
                ),
            )
            .scanIndexForward(true)
            .limit(1)
            .build()

        return client.query(request).items()?.let { item -> item.getOrNull(0)?.let { fromAttributeValues(it) }?.postId }
    }

    internal fun addPoint(point: Point) {
        val pointValues = HashMap<String, AttributeValue>()

        pointValues["discussionId"] = AttributeValue.builder().n(point.discussionId.toString()).build()
        pointValues["postId"] = AttributeValue.builder().n(point.postId.toString()).build()
        pointValues["givenTo"] = AttributeValue.builder().s(point.givenTo).build()
        pointValues["givenDateTime"] = AttributeValue.builder().s(point.givenDateTime.toString()).build()
        pointValues["questionId"] = AttributeValue.builder().n(point.questionId.toString()).build()
        pointValues["givenBy"] = AttributeValue.builder().s(point.givenBy).build()

        val request = PutItemRequest
            .builder()
            .tableName(TABLE_NAME)
            .item(pointValues)
            .build()

        try {
            client.putItem(request)
        } catch (e: ResourceNotFoundException) {
            log.error("Error: The Amazon DynamoDB table '$TABLE_NAME' can't be found.", e)
        } catch (e: DynamoDbException) {
            log.error("Exception while persisting point", e)
        }
    }

    override fun removePoint(point: Point) {
        val keyToRemove = mapOf<String, AttributeValue>(
            "discussionId" to AttributeValue.builder().n(point.discussionId.toString()).build(),
            "questionId" to AttributeValue.builder().n(point.questionId.toString()).build(),
        )

        val request = DeleteItemRequest
            .builder()
            .tableName(TABLE_NAME)
            .key(keyToRemove)
            .build()

        try {
            client.deleteItem(request)
        } catch (e: ResourceNotFoundException) {
            log.error("Error: The Amazon DynamoDB table '$TABLE_NAME' can't be found.", e)
        } catch (e: DynamoDbException) {
            log.error("Exception while deleting point", e)
        }
    }

    internal fun getAPoint(id: Long): Point? {
        val keyToGet = HashMap<String, AttributeValue>()

        keyToGet["postId"] = AttributeValue.builder().n(id.toString()).build()

        val request = GetItemRequest
            .builder()
            .key(keyToGet)
            .tableName(TABLE_NAME)
            .build()

        val returnedItem: Map<String, AttributeValue>? = client.getItem(request).item()
        return if (returnedItem != null && returnedItem.isNotEmpty()) {
            fromAttributeValues(returnedItem)
        } else {
            System.out.format("No item found with the key %s!\n", id.toString())
            null
        }
    }

    internal fun getPointsFrom(discussionId: Long, from: LocalDateTime): List<Point> {
        log.info("Getting all points for discussion $discussionId from $from")

        val request = QueryRequest
            .builder()
            .tableName(TABLE_NAME)
            .keyConditionExpression("discussionId = :discussionId")
            .filterExpression("givenDateTime >= :from")
            .expressionAttributeValues(
                mapOf(
                    ":discussionId" to AttributeValue.builder().n(discussionId.toString()).build(),
                    ":from" to AttributeValue.builder().s(from.toString()).build(),
                ),
            ).build()

        return client
            .queryPaginator(request)
            .items()
            ?.let { item -> item.map { fromAttributeValues(it) } }
            ?.toList() ?: emptyList()
    }

    override fun getPointsBetween(
        discussionId: Long,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<Point> = client.queryPointsBetween(TABLE_NAME, discussionId, from, to)
}
