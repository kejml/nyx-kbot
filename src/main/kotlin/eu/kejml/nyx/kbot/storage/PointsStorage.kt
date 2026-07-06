package eu.kejml.nyx.kbot.storage

import kotlinx.datetime.LocalDateTime
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

interface PointsStorage {
    fun getPointsBetween(
        discussionId: Long,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<Point>

    fun removePoint(point: Point)
}

private val queryLog = LoggerFactory.getLogger(PointsStorage::class.java)

internal fun DynamoDbClient.queryPointsBetween(
    tableName: String,
    discussionId: Long,
    from: LocalDateTime,
    to: LocalDateTime,
): List<Point> {
    queryLog.info("Getting points from $tableName between $from and $to")

    val request = QueryRequest
        .builder()
        .tableName(tableName)
        .indexName("dateTimeIndex")
        .keyConditionExpression("discussionId = :discussionId AND givenDateTime BETWEEN :dateFrom AND :dateTo")
        .expressionAttributeValues(
            mapOf(
                ":discussionId" to AttributeValue.builder().n(discussionId.toString()).build(),
                ":dateFrom" to AttributeValue.builder().s(from.toString()).build(),
                ":dateTo" to AttributeValue.builder().s(to.toString()).build(),
            ),
        ).build()

    return this
        .queryPaginator(request)
        .items()
        ?.let { item -> item.map { fromAttributeValues(it) } }
        ?.toList() ?: emptyList()
}
