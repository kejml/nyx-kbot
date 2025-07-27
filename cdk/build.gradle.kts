plugins {
    kotlin("jvm") version "1.9.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("software.amazon.awscdk:aws-cdk-lib:2.114.1")
    implementation("software.constructs:constructs:10.3.0")
    
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("eu.kejml.nyx.kbot.cdk.KbotAppKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}