package eu.kejml.nyx.kbot.storage

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import kotlin.system.exitProcess


const val tableName = "points"

data class Point(
    val id: Long,
    val givenTo: String,
    val dateTime: LocalDateTime,
    val questionId: Long,
    val givenBy: String,
)

fun fromAttributeValues(input: Map<String, AttributeValue>): Point {
    println(input)
    return Point(
        id = input["id"]?.n()?.toLong() ?: throw IllegalArgumentException("Missing attribute id"),
        givenTo = input["givenTo"]?.s() ?: throw IllegalArgumentException("Missing attribute givenTo"),
        dateTime = input["dateTime"]?.s()?.toLocalDateTime() ?: throw IllegalArgumentException("Missing attribute dateTime"),
        questionId = input["questionId"]?.n()?.toLong() ?: throw IllegalArgumentException("Missing attribute questionId"),
        givenBy = input["givenBy"]?.s() ?: throw IllegalArgumentException("Missing attribute givenBy"),
    )
}

object Points {
    private val client = DynamoDbClient.builder().build()

    fun addPoint(point: Point) {
        val pointValues = HashMap<String, AttributeValue>()

        pointValues["id"] = AttributeValue.builder().n(point.id.toString()).build()
        pointValues["givenTo"] = AttributeValue.builder().s(point.givenTo).build()
        pointValues["dateTime"] = AttributeValue.builder().s(point.dateTime.toString()).build()
        pointValues["questionId"] = AttributeValue.builder().n(point.questionId.toString()).build()
        pointValues["givenBy"] = AttributeValue.builder().s(point.givenBy).build()

        val request = PutItemRequest.builder()
            .tableName(tableName)
            .item(pointValues)
            .build()

        try {
            client.putItem(request)
        } catch (e: ResourceNotFoundException) {
            System.err.format("Error: The Amazon DynamoDB table \"%s\" can't be found.\n", tableName)
            System.err.println("Be sure that it exists and that you've typed its name correctly!")
            exitProcess(1)
        } catch (e: DynamoDbException) {
            System.err.println(e.message)
            exitProcess(2)
        }
    }

    fun getAPoint(id: Long): Point? {
        val keyToGet = HashMap<String, AttributeValue>()

        keyToGet["id"] = AttributeValue.builder()
            .n(id.toString()).build()

        val request = GetItemRequest.builder()
            .key(keyToGet)
            .tableName(tableName)
            .build()

        return try {
            val returnedItem: Map<String, AttributeValue>? = client.getItem(request).item()
            if (returnedItem != null && returnedItem.isNotEmpty()) {
                fromAttributeValues(returnedItem)
            } else {
                System.out.format("No item found with the key %s!\n", id.toString())
                null
            }
        } catch (e: DynamoDbException) {
            System.err.println(e.message)
            exitProcess(3)
        }
    }
}
