plugins {
    kotlin("jvm") version "2.1.20"
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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}