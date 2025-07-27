package eu.kejml.nyx.kbot.cdk

import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps

fun main() {
    val app = App()
    
    KbotStack(app, "KbotStack", StackProps.builder()
        .env(Environment.builder()
            .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
            .region("eu-central-1")
            .build())
        .build())
    
    app.synth()
}