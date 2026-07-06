package eu.kejml.nyx.kbot.storage

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException

const val BONUS_TABLE_NAME = "bonusPoints"

object BonusPoints {
    private val client = DynamoDbClient.builder().build()
    private val log = LoggerFactory.getLogger(this.javaClass)

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
        pointValues["questionIdPostId"] = AttributeValue.builder().s("${point.questionId}#${point.postId}").build()
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
}
