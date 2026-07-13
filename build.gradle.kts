import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10" apply true
    id("org.jlleitschuh.gradle.ktlint") version "14.1.0"
    application
}

group = "eu.kejml"
version = "1.0-SNAPSHOT"

val ktorVersion by extra("3.2.2")
val awsLambdaVersion by extra("1.4.0")
val awsSdkVersion by extra("2.34.0")

repositories {
    mavenCentral()
}

dependencies {
    // AWS Lambda
    implementation("com.amazonaws:aws-lambda-java-core:$awsLambdaVersion")
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")

    // HTTP Client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:s3")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("io.strikt:strikt-core:0.35.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.12.2")
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
        configurations.runtimeClasspath
            .get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
