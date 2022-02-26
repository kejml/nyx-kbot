package eu.kejml.nyx.kbot.storage

import io.kotless.PermissionLevel
import io.kotless.dsl.cloud.aws.DynamoDBTable
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*


const val tableName = "points"

data class Point(
    val discussionId: Long,
    val id: Long,
    val givenTo: String,
    val givenDateTime: LocalDateTime,
    val questionId: Long?,
    val givenBy: String?,
)

fun fromAttributeValues(input: Map<String, AttributeValue>): Point {
    println(input)
    return Point(
        discussionId = input["discussionId"]?.n()?.toLong() ?: throw IllegalArgumentException("Missing attribute discussionId"),
        id = input["id"]?.n()?.toLong() ?: throw IllegalArgumentException("Missing attribute id"),
        givenTo = input["givenTo"]?.s() ?: throw IllegalArgumentException("Missing attribute givenTo"),
        givenDateTime = input["givenDateTime"]?.s()?.toLocalDateTime() ?: throw IllegalArgumentException("Missing attribute givenDateTime"),
        questionId = input["questionId"]?.n()?.toLong(),
        givenBy = input["givenBy"]?.s(),
    )
}

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
object Points {
    private val client = DynamoDbClient.builder().build()
    private val log = LoggerFactory.getLogger(this.javaClass)

    internal fun getLastPostId(discussionId: Long): Long? {

        val request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("discussionId = :discussionId")
            .expressionAttributeValues(mapOf(
                ":discussionId" to AttributeValue.builder().n(discussionId.toString()).build()
            ))
            .scanIndexForward(false)
            .limit(1)
            .build()

        return client.query(request).items()?.let { item -> item.getOrNull(0)?.let { fromAttributeValues(it) }?.id }
    }

    internal fun addPoint(point: Point) {
        val pointValues = HashMap<String, AttributeValue>()

        pointValues["discussionId"] = AttributeValue.builder().n(point.discussionId.toString()).build()
        pointValues["id"] = AttributeValue.builder().n(point.id.toString()).build()
        pointValues["givenTo"] = AttributeValue.builder().s(point.givenTo).build()
        pointValues["givenDateTime"] = AttributeValue.builder().s(point.givenDateTime.toString()).build()
        pointValues["questionId"] = AttributeValue.builder().n(point.questionId.toString()).build()
        pointValues["givenBy"] = AttributeValue.builder().s(point.givenBy).build()

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(pointValues)
            .build()

        try {
            client.putItem(request)
        } catch (e: ResourceNotFoundException) {
            log.error("Error: The Amazon DynamoDB table '$tableName' can't be found.", e)
        } catch (e: DynamoDbException) {
            log.error("Exception while persisting point", e)
        }
    }

    internal fun getAPoint(id: Long): Point? {
        val keyToGet = HashMap<String, AttributeValue>()

        keyToGet["id"] = AttributeValue.builder()
            .n(id.toString()).build()

        val request = GetItemRequest.builder()
            .key(keyToGet)
            .tableName(tableName)
            .build()

        val returnedItem: Map<String, AttributeValue>? = client.getItem(request).item()
        return if (returnedItem != null && returnedItem.isNotEmpty()) {
            fromAttributeValues(returnedItem)
        } else {
            System.out.format("No item found with the key %s!\n", id.toString())
            null
        }
    }

    internal fun getPointsBetween(discussionId: Long, from: LocalDateTime, to: LocalDateTime): List<Point> {
        log.info("Getting points between $from and $to")

        val request = QueryRequest.builder()
            .tableName(tableName)
            .indexName("dateTimeIndex")
            .keyConditionExpression("discussionId = :discussionId AND givenDateTime BETWEEN :dateFrom AND :dateTo")
            .expressionAttributeValues(mapOf(
                ":discussionId" to AttributeValue.builder().n(discussionId.toString()).build(),
                ":dateFrom" to AttributeValue.builder().s(from.toString()).build(),
                ":dateTo" to AttributeValue.builder().s(to.toString()).build(),
            ))
            .build()

        return client.query(request).items()?.let { item -> item.map { fromAttributeValues(it) } }?.toList() ?: emptyList()
    }
}
