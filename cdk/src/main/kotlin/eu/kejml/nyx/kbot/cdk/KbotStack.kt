package eu.kejml.nyx.kbot.cdk

import eu.kejml.nyx.kbot.lambda.*
import software.amazon.awscdk.*
import software.amazon.awscdk.services.apigateway.*
import software.amazon.awscdk.services.dynamodb.*
import software.amazon.awscdk.services.events.CronOptions
import software.amazon.awscdk.services.events.Rule
import software.amazon.awscdk.services.events.Schedule
import software.amazon.awscdk.services.events.targets.LambdaFunction
import software.amazon.awscdk.services.iam.Effect
import software.amazon.awscdk.services.iam.PolicyStatement
import software.amazon.awscdk.services.lambda.Code
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.Runtime
import software.constructs.Construct

class KbotStack(scope: Construct, id: String, props: StackProps) : Stack(scope, id, props) {

    private val jarPath = "../build/libs/nyx-kbot-1.0-SNAPSHOT-all.jar"
    private val deploymentTime = java.time.Instant.now().toString()

    init {
        // Parameter to control table creation
        val createTable = CfnParameter.Builder.create(this, "CreateTable")
            .type("String")
            .defaultValue("true")
            .allowedValues(listOf("true", "false"))
            .description("Whether to create the DynamoDB table (false to use existing)")
            .build()

        // Condition for table creation
        val shouldCreateTable = CfnCondition.Builder.create(this, "ShouldCreateTable")
            .expression(Fn.conditionEquals(createTable, "true"))
            .build()

        // Create new table conditionally
        val newTable = Table.Builder.create(this, "NewPointsTable")
            .tableName("points")
            .partitionKey(Attribute.builder()
                .name("discussionId")
                .type(AttributeType.NUMBER)
                .build())
            .sortKey(Attribute.builder()
                .name("questionId")
                .type(AttributeType.NUMBER)
                .build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build()

        // Add local secondary indexes to new table
        newTable.addLocalSecondaryIndex(LocalSecondaryIndexProps.builder()
            .indexName("dateTimeIndex")
            .sortKey(Attribute.builder()
                .name("givenDateTime")
                .type(AttributeType.STRING)
                .build())
            .projectionType(ProjectionType.INCLUDE)
            .nonKeyAttributes(listOf("postId", "givenTo"))
            .build())

        newTable.addLocalSecondaryIndex(LocalSecondaryIndexProps.builder()
            .indexName("lastId")
            .sortKey(Attribute.builder()
                .name("postId")
                .type(AttributeType.NUMBER)
                .build())
            .projectionType(ProjectionType.KEYS_ONLY)
            .build())

        // Apply condition to the new table
        (newTable.node.defaultChild as CfnTable).cfnOptions.condition = shouldCreateTable

        // Always use Table.fromTableName for permissions - this works whether table exists or will be created
        val pointsTable = Table.fromTableName(this, "PointsTableReference", "points")

        // Index policy for all scheduled Lambdas (covers table + indexes)
        val tableArn = "arn:aws:dynamodb:${this.region}:${this.account}:table/points"
        val indexPolicy = PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .actions(listOf(
                "dynamodb:Query",
                "dynamodb:GetItem",
                "dynamodb:PutItem",
                "dynamodb:UpdateItem",
                "dynamodb:DeleteItem",
                "dynamodb:Scan",
            ))
            .resources(listOf(
                tableArn,
                "$tableArn/index/*",
            ))
            .build()

        // Scheduled Lambdas — one set per discussion
        createDiscussionLambdas(
            label = "PoznejPcHru",
            discussionId = 11354L,
            homeContentId = 68695L,
            hallOfFameContentId = 68810L,
            table = pointsTable,
            indexPolicy = indexPolicy,
            enabled = true,
        )
        createDiscussionLambdas(
            label = "ZabavnyKviz",
            discussionId = 7045L,
            homeContentId = null,
            hallOfFameContentId = null,
            table = pointsTable,
            indexPolicy = indexPolicy,
            enabled = true,
            startFromPostId = 58541254L,
        )
        createDiscussionLambdas(
            label = "Sandbox",
            discussionId = 20310L,
            homeContentId = 68692L,
            hallOfFameContentId = 54996L,
            table = pointsTable,
            indexPolicy = indexPolicy,
            enabled = false,
        )

        // Non-discussion-specific Lambdas
        val baseEnv = mapOf("TABLE_NAME" to pointsTable.tableName)

        val helloFunction = Function.Builder.create(this, "HelloFunction")
            .runtime(Runtime.JAVA_21)
            .handler(HelloHandler::class.java.name)
            .code(Code.fromAsset(jarPath))
            .timeout(Duration.seconds(30))
            .memorySize(256)
            .environment(baseEnv)
            .build()

        val nyxTestFunction = Function.Builder.create(this, "NyxTestFunction")
            .runtime(Runtime.JAVA_21)
            .handler(NyxTestHandler::class.java.name)
            .code(Code.fromAsset(jarPath))
            .timeout(Duration.minutes(2))
            .memorySize(512)
            .environment(baseEnv)
            .build()

        pointsTable.grantReadData(helloFunction)
        pointsTable.grantReadData(nyxTestFunction)
        helloFunction.addToRolePolicy(indexPolicy)
        nyxTestFunction.addToRolePolicy(indexPolicy)

        // API Gateway
        val api = RestApi.Builder.create(this, "KbotApi")
            .restApiName("Kbot API")
            .description("Kbot REST API")
            .build()

        val helloIntegration = LambdaIntegration(helloFunction)
        api.root.addResource("hello").addMethod("GET", helloIntegration)

        val nyxTestIntegration = LambdaIntegration(nyxTestFunction,
            LambdaIntegrationOptions.builder()
                .requestTemplates(mapOf(
                    "application/json" to "{\n  \"headers\": {\n    \"X-Amz-Invocation-Type\": \"Event\"\n  }\n}"
                ))
                .integrationResponses(listOf(
                    IntegrationResponse.builder()
                        .statusCode("202")
                        .responseTemplates(mapOf(
                            "application/json" to "{\"message\": \"Request accepted for processing\"}"
                        ))
                        .build()
                ))
                .build())
        api.root.addResource("nyx-test").addMethod("GET", nyxTestIntegration,
            MethodOptions.builder()
                .methodResponses(listOf(
                    MethodResponse.builder()
                        .statusCode("202")
                        .build()
                ))
                .build())

        // Outputs
        CfnOutput.Builder.create(this, "ApiUrl")
            .value(api.url)
            .description("API Gateway URL")
            .build()
    }

    private fun createDiscussionLambdas(
        label: String,
        discussionId: Long,
        homeContentId: Long?,
        hallOfFameContentId: Long?,
        table: ITable,
        indexPolicy: PolicyStatement,
        enabled: Boolean = true,
        startFromPostId: Long? = null,
    ) {
        val env = buildMap {
            put("TABLE_NAME", table.tableName)
            put("DISCUSSION_ID", discussionId.toString())
            homeContentId?.let { put("HOME_CONTENT_ID", it.toString()) }
            hallOfFameContentId?.let { put("HALL_OF_FAME_CONTENT_ID", it.toString()) }
            startFromPostId?.let { put("START_FROM_POST_ID", it.toString()) }
        }

        val hourly = Function.Builder.create(this, "HourlyUpdateFunction$label")
            .runtime(Runtime.JAVA_21)
            .handler(HourlyUpdateHandler::class.java.name)
            .code(Code.fromAsset(jarPath))
            .timeout(Duration.minutes(15))
            .memorySize(512)
            .environment(env)
            .build()

        val monthly = Function.Builder.create(this, "MonthlySummaryFunction$label")
            .runtime(Runtime.JAVA_21)
            .handler(MonthlySummaryHandler::class.java.name)
            .code(Code.fromAsset(jarPath))
            .timeout(Duration.minutes(15))
            .memorySize(512)
            .environment(env)
            .build()

        val yearly = Function.Builder.create(this, "YearlySummaryFunction$label")
            .runtime(Runtime.JAVA_21)
            .handler(YearlySummaryHandler::class.java.name)
            .code(Code.fromAsset(jarPath))
            .timeout(Duration.minutes(15))
            .memorySize(512)
            .environment(env)
            .build()

        table.grantReadWriteData(hourly)
        table.grantReadWriteData(monthly)
        table.grantReadWriteData(yearly)
        hourly.addToRolePolicy(indexPolicy)
        monthly.addToRolePolicy(indexPolicy)
        yearly.addToRolePolicy(indexPolicy)

        if (enabled) {
            Rule.Builder.create(this, "HourlyUpdateRule$label")
                .description("Hourly update rule ($label) - Last deployed: $deploymentTime")
                .schedule(Schedule.rate(Duration.hours(1)))
                .targets(listOf(LambdaFunction(hourly)))
                .build()

            Rule.Builder.create(this, "MonthlySummaryRule$label")
                .description("Monthly summary rule ($label) - Last deployed: $deploymentTime")
                .schedule(Schedule.cron(CronOptions.builder()
                    .minute("10")
                    .hour("01")
                    .day("1")
                    .month("2-12")
                    .build()))
                .targets(listOf(LambdaFunction(monthly)))
                .build()

            Rule.Builder.create(this, "YearlySummaryRule$label")
                .description("Yearly summary rule ($label) - Last deployed: $deploymentTime")
                .schedule(Schedule.cron(CronOptions.builder()
                    .minute("10")
                    .hour("01")
                    .day("1")
                    .month("1")
                    .build()))
                .targets(listOf(LambdaFunction(yearly)))
                .build()
        }
    }

}
