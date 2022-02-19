package eu.kejml.nyx.kbot.lambda

import eu.kejml.nyx.kbot.api.Nyx
import eu.kejml.nyx.kbot.data.Discussion
import io.kotless.dsl.lang.http.Get
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@ExperimentalSerializationApi
object Main {
    val client = HttpClient()

    @Get("/hello")
    fun root(): String {
        return "Hello world!"
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Get("/nyx-test")
    fun nyxTest(): String = runBlocking {
        val data = Nyx.getDiscussion("20310")
        val discussion = json.decodeFromString<Discussion>(data)
        discussion.posts.filter { it.content.contains(Regex("<(b|strong)>bod</(b|strong)>", RegexOption.IGNORE_CASE)) }.toString()
    }
}