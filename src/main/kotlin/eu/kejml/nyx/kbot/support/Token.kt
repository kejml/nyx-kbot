package eu.kejml.nyx.kbot.support

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

suspend fun main() {
    val client = HttpClient()

    val response: String = client.post("https://nyx.cz/api/create_token/KBOT") {
        headers {
            append(HttpHeaders.UserAgent, "KBOT")
        }
    }

    println(response)
}