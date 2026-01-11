package eu.kejml.nyx.kbot.cdk

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
        // The table name "points" will be the same regardless of whether it's existing or newly created
        val pointsTable = Table.fromTableName(this, "PointsTableReference", "points")

        // Lambda functions
        val lambdaEnvironment = mapOf(
            "TABLE_NAME" to pointsTable.tableName
        )
        
        val hourlyUpdateFunction = Function.Builder.create(this, "HourlyUpdateFunction")
            .runtime(Runtime.JAVA_21)
            .handler("eu.kejml.nyx.kbot.lambda.HourlyUpdateHandler")
            .code(Code.fromAsset("../build/libs/nyx-kbot-1.0-SNAPSHOT-all.jar"))
            .timeout(Duration.minutes(15))
            .memorySize(512)
            .environment(lambdaEnvironment)
            .build()

        val monthlySummaryFunction = Function.Builder.create(this, "MonthlySummaryFunction")
            .runtime(Runtime.JAVA_21)
            .handler("eu.kejml.nyx.kbot.lambda.MonthlySummaryHandler")
            .code(Code.fromAsset("../build/libs/nyx-kbot-1.0-SNAPSHOT-all.jar"))
            .timeout(Duration.minutes(15))
            .memorySize(512)
            .environment(lambdaEnvironment)
            .build()

        val yearlySummaryFunction = Function.Builder.create(this, "YearlySummaryFunction")
            .runtime(Runtime.JAVA_21)
            .handler("eu.kejml.nyx.kbot.lambda.YearlySummaryHandler")
            .code(Code.fromAsset("../build/libs/nyx-kbot-1.0-SNAPSHOT-all.jar"))
            .timeout(Duration.minutes(15))
            .memorySize(512)
            .environment(lambdaEnvironment)
            .build()

        val updateHomeFunction = Function.Builder.create(this, "UpdatedHomeFunction")
            .runtime(Runtime.JAVA_21)
            .handler("eu.kejml.nyx.kbot.lambda.UpdatedHomeHandler")
            .code(Code.fromAsset("../build/libs/nyx-kbot-1.0-SNAPSHOT-all.jar"))
            .timeout(Duration.minutes(15))
            .memorySize(512)
            .environment(lambdaEnvironment)
            .build()

        val helloFunction = Function.Builder.create(this, "HelloFunction")
            .runtime(Runtime.JAVA_21)
            .handler("eu.kejml.nyx.kbot.lambda.HelloHandler")
            .code(Code.fromAsset("../build/libs/nyx-kbot-1.0-SNAPSHOT-all.jar"))
            .timeout(Duration.seconds(30))
            .memorySize(256)
            .environment(lambdaEnvironment)
            .build()
            
        val nyxTestFunction = Function.Builder.create(this, "NyxTestFunction")
            .runtime(Runtime.JAVA_21)
            .handler("eu.kejml.nyx.kbot.lambda.NyxTestHandler")
            .code(Code.fromAsset("../build/libs/nyx-kbot-1.0-SNAPSHOT-all.jar"))
            .timeout(Duration.minutes(2))
            .memorySize(512)
            .environment(lambdaEnvironment)
            .build()

        // Grant DynamoDB permissions
        pointsTable.grantReadWriteData(hourlyUpdateFunction)
        pointsTable.grantReadWriteData(monthlySummaryFunction)
        pointsTable.grantReadWriteData(yearlySummaryFunction)
        pointsTable.grantReadWriteData(updateHomeFunction)
        pointsTable.grantReadData(helloFunction)
        pointsTable.grantReadData(nyxTestFunction)
        
        // Additional permissions for existing table indexes (needed when using Table.fromTableName)
        val tableArn = "arn:aws:dynamodb:${this.region}:${this.account}:table/points"
        val indexPolicy = PolicyStatement.Builder.create()
            .effect(Effect.ALLOW)
            .actions(listOf(
                "dynamodb:Query",
                "dynamodb:GetItem",
                "dynamodb:PutItem",
                "dynamodb:UpdateItem",
                "dynamodb:DeleteItem",
                "dynamodb:Scan"
            ))
            .resources(listOf(
                tableArn,
                "$tableArn/index/*"
            ))
            .build()
            
        hourlyUpdateFunction.addToRolePolicy(indexPolicy)
        monthlySummaryFunction.addToRolePolicy(indexPolicy)
        yearlySummaryFunction.addToRolePolicy(indexPolicy)
        updateHomeFunction.addToRolePolicy(indexPolicy)
        helloFunction.addToRolePolicy(indexPolicy)
        nyxTestFunction.addToRolePolicy(indexPolicy)

        // EventBridge rules for scheduled functions
        // Note: Adding timestamp in description forces CDK to detect drift and update rules
        val deploymentTime = java.time.Instant.now().toString()

        Rule.Builder.create(this, "HourlyUpdateRule")
            .description("Hourly update rule - Last deployed: $deploymentTime")
            .schedule(Schedule.rate(Duration.hours(1)))
            .targets(listOf(LambdaFunction(hourlyUpdateFunction)))
            .build()

        Rule.Builder.create(this, "MonthlySummaryRule")
            .description("Monthly summary rule - Last deployed: $deploymentTime")
            .schedule(Schedule.cron(CronOptions.builder()
                .minute("10")
                .hour("01")
                .day("1")
                .month("2-12")
                .build()))
            .targets(listOf(LambdaFunction(monthlySummaryFunction)))
            .build()

        Rule.Builder.create(this, "YearlySummaryRule")
            .description("Yearly summary rule - Last deployed: $deploymentTime")
            .schedule(Schedule.cron(CronOptions.builder()
                .minute("10")
                .hour("01")
                .day("1")
                .month("1")
                .build()))
            .targets(listOf(LambdaFunction(yearlySummaryFunction)))
            .build()

        Rule.Builder.create(this, "UpdatedHomeHandler")
            .description("Updated home rule - Last deployed: $deploymentTime")
            .schedule(Schedule.cron(CronOptions.builder()
                .minute("15")
                .hour("4")
                .build()))
            .targets(listOf(LambdaFunction(updateHomeFunction)))
            .build()

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
}