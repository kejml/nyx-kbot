package eu.kejml.nyx.kbot

import io.kotless.dsl.lang.http.Get

object Main {
    @Get("/")
    fun root() = "Hello world!"
}