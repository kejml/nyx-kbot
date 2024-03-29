import io.kotless.plugin.gradle.dsl.kotless
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10" // Kotless doesn't work with kotlin 1.6.21 - https://github.com/JetBrains/kotless/issues/121
    kotlin("plugin.serialization") version "1.6.10" apply true
    id("io.kotless") version "0.2.0" apply true
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
}

group = "eu.kejml"
version = "1.0-SNAPSHOT"

val kotlessVersion by extra("0.2.0")
val ktorVersion by extra("1.6.8")

repositories {
    mavenCentral()
    // Kotless repository
    maven(url = uri("https://packages.jetbrains.team/maven/p/ktls/maven"))
}

dependencies {
    implementation("io.kotless", "kotless-lang", kotlessVersion)
    implementation("io.kotless", "kotless-lang-aws", kotlessVersion)
    implementation("io.ktor", "ktor-client-core", ktorVersion)
    implementation("io.ktor", "ktor-client-cio", ktorVersion)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.3")
    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.17.196"))
    implementation("software.amazon.awssdk:dynamodb")

    testImplementation(kotlin("test"))
    testImplementation("io.strikt:strikt-core:0.34.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
}

kotless {
    config {
        aws {
            storage {
                bucket = "kbot.bucket"
            }
            profile = "kbot"
            region = "eu-central-1"
        }
    }
    webapp {
        lambda {
            kotless {
                packages = setOf("eu.kejml.nyx.kbot.lambda")
            }
        }
    }
    extensions {
        terraform {
            files {
                add(file("src/main/resources/secret.properties"))
                add(file("src/main/tf/extensions.tf"))
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
