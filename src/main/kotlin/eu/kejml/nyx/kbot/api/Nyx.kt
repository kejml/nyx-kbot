package eu.kejml.nyx.kbot.api

import eu.kejml.nyx.kbot.lambda.Main
import io.ktor.client.request.*
import java.io.InputStream
import java.util.*

object Nyx {
    val secretStream: InputStream? = this.javaClass.classLoader.getResourceAsStream("secret.properties")
    val props = Properties().apply { load(secretStream) }
    val nyxToken = props["nyx_token"]

    suspend fun getDiscussion(id: String): String {
        return nyxGet("discussion/$id")
    }

    suspend fun nyxGet(endpoint: String): String {
        return Main.client.get("https://nyx.cz/api/$endpoint") {
            headers {
                this.append("Authorization", "Bearer $nyxToken")
            }
        }
    }
}