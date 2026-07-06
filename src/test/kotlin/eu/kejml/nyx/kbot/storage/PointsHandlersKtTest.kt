package eu.kejml.nyx.kbot.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

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
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a> <b>BOD</b>""",
        ],
    )
    fun `parsing single point`(postContent: String) {
        val pointData = postContent.parsePointData()

        expectThat(pointData)
            .hasSize(1)
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
        ],
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
        ],
    )
    fun `parsing multiple points`(postContent: String) {
        val pointData = postContent.parsePointData()

        expectThat(pointData)
            .hasSize(2)
            .any {
                isEqualTo(QuestionIdGivenTo(42L, "UZIVATEL"))
            }.any {
                isEqualTo(QuestionIdGivenTo(43L, "UZIVATEL"))
            }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """<a data-id=42 href="https://nyx.cz">UZIVATEL</a>: BONUS""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: bonus""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>BONUS</b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <B>Bonus</B>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b><em>BONUS</em></b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: Davam <B>bonus</B> a zadej""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <STRONG>bonus</STRONG>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>BONUS<b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b> BONUS </b>""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a> <b>BONUS</b>""",
            "Text pred\n<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BONUS</b>\ni text pod",
        ],
    )
    fun `parsing single bonus point`(postContent: String) {
        val pointData = postContent.parsePointData(PointType.BONUS)

        expectThat(pointData)
            .hasSize(1)
            .first()
            .isEqualTo(QuestionIdGivenTo(42L, "UZIVATEL"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "bonus",
            "Random bonus",
            "Random <b>BONUS</b>",
            "<b>BONUS</b>",
            """<a href="https://nyx.cz">UZIVATEL</a>: <b>BONUS</b>""",
            "UZIVATEL: <B>BONUS</B>",
            "<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>:\n <b>BONUS</b>",
        ],
    )
    fun `not parsing invalid bonus point`(postContent: String) {
        val pointData = postContent.parsePointData(PointType.BONUS)

        expectThat(pointData)
            .isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: BOD""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>BOD</b>""",
        ],
    )
    fun `not parsing regular point as bonus`(postContent: String) {
        val pointData = postContent.parsePointData(PointType.BONUS)

        expectThat(pointData)
            .isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: BONUS""",
            """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>BONUS</b>""",
        ],
    )
    fun `not parsing bonus point as regular`(postContent: String) {
        val pointData = postContent.parsePointData()

        expectThat(pointData)
            .isEmpty()
    }

    @Test
    fun `parsing only matching type from post with both point types`() {
        val postContent =
            "<a href=\"https://nyx.cz\" data-id=42>UZIVATEL</a>: <b>BOD</b>\n" +
                "<a href=\"https://nyx.cz\" data-id=43>UZIVATEL</a>: <b>BONUS</b>"

        expectThat(postContent.parsePointData())
            .hasSize(1)
            .first()
            .isEqualTo(QuestionIdGivenTo(42L, "UZIVATEL"))

        expectThat(postContent.parsePointData(PointType.BONUS))
            .hasSize(1)
            .first()
            .isEqualTo(QuestionIdGivenTo(43L, "UZIVATEL"))
    }

    @Test
    fun `line with both keywords counts for both types`() {
        val postContent = """<a href="https://nyx.cz" data-id=42>UZIVATEL</a>: <b>BOD</b> a <b>BONUS</b>"""

        expectThat(postContent.parsePointData())
            .hasSize(1)
            .first()
            .isEqualTo(QuestionIdGivenTo(42L, "UZIVATEL"))

        expectThat(postContent.parsePointData(PointType.BONUS))
            .hasSize(1)
            .first()
            .isEqualTo(QuestionIdGivenTo(42L, "UZIVATEL"))
    }

    @Test
    fun `search text is derived from keyword and excluded words`() {
        expectThat(PointType.BOD.searchText).isEqualTo("bod -bodování")
        expectThat(PointType.BONUS.searchText).isEqualTo("bonus")
    }

    @Test
    fun `rendering grouped post with medals awards top three`() {
        val grouped = sortedMapOf(
            reverseOrder(),
            9 to listOf(user("ALICE")),
            8 to listOf(user("BOB")),
            7 to listOf(user("CYRIL")),
            5 to listOf(user("DAVID")),
        )

        val output = renderGroupedPost(grouped)

        expectThat(output).contains("🥇")
        expectThat(output).contains("🥈")
        expectThat(output).contains("🥉")
        expectThat(output).contains("&nbsp;4.")
    }

    @Test
    fun `rendering grouped post without medals has plain ranks`() {
        val grouped = sortedMapOf(
            reverseOrder(),
            9 to listOf(user("ALICE")),
            8 to listOf(user("BOB")),
        )

        val output = renderGroupedPost(grouped, withMedals = false)

        expectThat(output).contains("1.")
        expectThat(output).contains("2.")
        expectThat(output).not().contains("🥇")
        expectThat(output).not().contains("🥈")
        expectThat(output).not().contains("🥉")
    }

    @Test
    fun `rendering grouped table without medals has plain ranks`() {
        val grouped = sortedMapOf(
            reverseOrder(),
            9 to listOf(user("ALICE"), user("BOB")),
            7 to listOf(user("CYRIL")),
        )

        val output = renderGroupedTable(grouped, withMedals = false)

        expectThat(output).contains("1.-2.")
        expectThat(output).contains("3.")
        expectThat(output).not().contains("🥇")
        expectThat(output).not().contains("🥈")
        expectThat(output).not().contains("🥉")
    }

    @Test
    fun `rendering grouped table with medals keeps medals for tie spanning top three`() {
        val grouped = sortedMapOf(
            reverseOrder(),
            9 to listOf(user("ALICE"), user("BOB")),
            7 to listOf(user("CYRIL")),
        )

        val output = renderGroupedTable(grouped)

        expectThat(output).contains("🥇")
        expectThat(output).contains("🥈")
        expectThat(output).contains("🥉")
        expectThat(output).contains("1.-2.")
    }

    @Test
    fun `limiting displayed places truncates lower ranks`() {
        val grouped = sortedMapOf(
            reverseOrder(),
            9 to listOf(user("ALICE")),
            8 to listOf(user("BOB")),
        )

        val output = renderGroupedPost(grouped, limitDisplayedPlaces = 1)

        expectThat(output).contains("ALICE")
        expectThat(output).not().contains("BOB")
    }

    private fun user(name: String) = UserAndPoints(name, emptyList())
}
