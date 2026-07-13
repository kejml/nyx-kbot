package eu.kejml.nyx.kbot.storage

import kotlinx.datetime.LocalDateTime
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException

const val BONUS_TABLE_NAME = "bonusPoints"

object BonusPoints : PointsStorage {
    private val client = DynamoDbClient.builder().build()
    private val log = LoggerFactory.getLogger(this.javaClass)

    private fun Point.questionIdPostId() = "$questionId#$postId"

    internal fun getLastPostId(discussionId: Long): Long? {
        val request = QueryRequest
            .builder()
            .tableName(BONUS_TABLE_NAME)
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

        return client.query(request).items()?.getOrNull(0)?.get("postId")?.n()?.toLong()
    }

    internal fun addBonusPoint(point: Point) {
        val pointValues = HashMap<String, AttributeValue>()

        pointValues["discussionId"] = AttributeValue.builder().n(point.discussionId.toString()).build()
        pointValues["questionIdPostId"] = AttributeValue.builder().s(point.questionIdPostId()).build()
        pointValues["postId"] = AttributeValue.builder().n(point.postId.toString()).build()
        pointValues["questionId"] = AttributeValue.builder().n(point.questionId.toString()).build()
        pointValues["givenTo"] = AttributeValue.builder().s(point.givenTo).build()
        pointValues["givenDateTime"] = AttributeValue.builder().s(point.givenDateTime.toString()).build()
        pointValues["givenBy"] = AttributeValue.builder().s(point.givenBy).build()

        val request = PutItemRequest
            .builder()
            .tableName(BONUS_TABLE_NAME)
            .item(pointValues)
            .build()

        try {
            client.putItem(request)
        } catch (e: ResourceNotFoundException) {
            log.error("Error: The Amazon DynamoDB table '$BONUS_TABLE_NAME' can't be found.", e)
        } catch (e: DynamoDbException) {
            log.error("Exception while persisting bonus point", e)
        }
    }

    internal fun getPointsFrom(discussionId: Long, from: LocalDateTime): List<Point> =
        client.queryPointsFrom(BONUS_TABLE_NAME, discussionId, from)

    override fun getPointsBetween(
        discussionId: Long,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<Point> = client.queryPointsBetween(BONUS_TABLE_NAME, discussionId, from, to)

    override fun removePoint(point: Point) {
        val keyToRemove = mapOf<String, AttributeValue>(
            "discussionId" to AttributeValue.builder().n(point.discussionId.toString()).build(),
            "questionIdPostId" to AttributeValue.builder().s(point.questionIdPostId()).build(),
        )

        val request = DeleteItemRequest
            .builder()
            .tableName(BONUS_TABLE_NAME)
            .key(keyToRemove)
            .build()

        try {
            client.deleteItem(request)
        } catch (e: ResourceNotFoundException) {
            log.error("Error: The Amazon DynamoDB table '$BONUS_TABLE_NAME' can't be found.", e)
        } catch (e: DynamoDbException) {
            log.error("Exception while deleting bonus point", e)
        }
    }
}
