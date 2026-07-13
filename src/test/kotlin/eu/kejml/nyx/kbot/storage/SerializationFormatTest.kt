package eu.kejml.nyx.kbot.storage

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class SerializationFormatTest {
    @Test
    fun `point serializes datetime as ISO string`() {
        val point = Point(1L, 2L, "USER", LocalDateTime(2024, 5, 1, 12, 30), 3L, "GIVER")

        expectThat(Json.encodeToString(Point.serializer(), point))
            .isEqualTo(
                """{"discussionId":1,"postId":2,"givenTo":"USER","givenDateTime":"2024-05-01T12:30","questionId":3,"givenBy":"GIVER"}""",
            )
    }
}