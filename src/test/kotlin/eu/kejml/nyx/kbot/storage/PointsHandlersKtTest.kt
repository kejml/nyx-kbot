package eu.kejml.nyx.kbot.storage

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.assertions.*

internal class PointsHandlersKtTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            // Regular points
            """<a data-id=42 href="https://nyx.cz">UZIVATEL</a>: BOD""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: BOD""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: bod""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>BOD</b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>bod</b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <B>bod</b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <B>bod</B>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b><em>BOD</em></b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: Davam <B>bod</B>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: Davam <B>bod</B> a zadej""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <B>bod</B> a zadej""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <STRONG>bod</STRONG>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <strong>bod</strong>""",
            "Text pred\n<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>",
            "<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\n text po",
            "Text pred\n\n<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\ni text pod",
            // Common typos
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>BOD<b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <strong>BOD<strong>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b> BOD </b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>     BOD      </b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b> <em>BOD</em> </b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b><em> BOD </em></b>""",
        ]
    )
    fun `parsing single point`(postContent: String) {
        val pointData = postContent.parsePointData()

        expectThat(pointData).hasSize(1)
            .first()
            .isEqualTo(QuestionIdGivenTo(42L, "UZIVATEL"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Random text",
            "Random \nmultiline\n\ntext",
            "bod",
            "BOD",
            "Random BOD",
            "Random <b>BOD</b>",
            "<b>BOD</b>",
            "<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>:\n <b>BOD</b>",
            """<a href="https://nyx.cz">UZIVATEL</a>: <b>BOD</b>""",
            "UZIVATEL: <B>BOD</B>",
        ]
    )
    fun `not parsing invalid point`(postContent: String) {
        val pointData = postContent.parsePointData()

        expectThat(pointData)
            .isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\n<a href=\"https://nyx.cz\" data-id=43>UZIVATEL</a>: <b>BOD</b>",
            "<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\n\n<a href=\"https://nyx.cz\" data-id=43>UZIVATEL</a>: <b>BOD</b>",
            "Text before\n<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\n\n<a href=\"https://nyx.cz\" data-id=43>UZIVATEL</a>: <b>BOD</b>",
            "<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\n\n<a href=\"https://nyx.cz\" data-id=43>UZIVATEL</a>: <b>BOD</b>\nText after",
            "Text before\n<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\nText in the middle\n<a href=\"https://nyx.cz\" data-id=43>UZIVATEL</a>: <b>BOD</b>\ntext after",
        ]
    )
    fun `parsing multiple points`(postContent: String) {
        val pointData = postContent.parsePointData()

        expectThat(pointData)
            .hasSize(2)
            .any {
                isEqualTo(QuestionIdGivenTo(42L, "UZIVATEL"))
            }
            .any {
                isEqualTo(QuestionIdGivenTo(43L, "UZIVATEL"))
            }
    }
}