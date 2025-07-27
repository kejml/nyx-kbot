import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20" apply true
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
    application
}

group = "eu.kejml"
version = "1.0-SNAPSHOT"

val ktorVersion by extra("2.3.12")
val awsLambdaVersion by extra("1.2.2")
val awsSdkVersion by extra("2.21.29")

repositories {
    mavenCentral()
}

dependencies {
    // AWS Lambda
    implementation("com.amazonaws:aws-lambda-java-core:$awsLambdaVersion")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")

    // HTTP Client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:dynamodb")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")

    testImplementation(kotlin("test"))
    testImplementation("io.strikt:strikt-core:0.34.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
}

application {
    mainClass.set("eu.kejml.nyx.kbot.MainKt")
}

// Fat JAR for Lambda deployment
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    },)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
