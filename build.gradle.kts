import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    id("io.kotless") version "0.2.0" apply true
}

group = "eu.kejml"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    //Kotless repository
    maven(url = uri("https://packages.jetbrains.team/maven/p/ktls/maven"))
}

dependencies {
    implementation("io.kotless", "kotless-lang", "0.2.0")
    implementation("io.kotless", "kotless-lang-aws", "0.2.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
