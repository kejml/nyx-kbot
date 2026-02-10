# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KBOT is a single-purpose bot for the Czech discussion board Nyx.cz that tracks scoring in a specific club. It's built with Kotlin and deployed to AWS using AWS CDK, leveraging Lambda functions and DynamoDB storage.

## Development Commands

### Build and Test
- `./gradlew build` - Build the main project  
- `./gradlew test` - Run tests using JUnit 5
- `./gradlew ktlintCheck` - Check code style with ktlint
- `./gradlew ktlintFormat` - Auto-format code with ktlint
- `./gradlew fatJar` - Build fat JAR for Lambda deployment

### Deployment
- `./deploy.sh` (Linux/Mac) or `deploy.bat` (Windows) - Build and deploy to AWS using CDK
- `cd cdk && npx cdk deploy` - Deploy only the infrastructure
- `cd cdk && npx cdk destroy` - Remove all AWS resources

## Architecture

The codebase follows a serverless architecture with individual Lambda functions:

### Core Components
- **lambda/LambdaHandlers.kt** - AWS Lambda request handlers
  - `HourlyUpdateHandler` - Scheduled hourly points update
  - `MonthlySummaryHandler` - Monthly summary generation
  - `YearlySummaryHandler` - Yearly summary generation  
  - `HelloHandler` - Test endpoint at `/hello`
  - `NyxTestHandler` - Debug endpoint at `/nyx-test`
- **lambda/ScheduledActions.kt** - Legacy object (deprecated, use handlers instead)
- **api/NyxClient.kt** - HTTP client for Nyx.cz API communication
  - Handles authentication via Bearer token
  - Supports discussion reading, posting, and rating
- **storage/** - DynamoDB operations and business logic
  - **Points.kt** - DynamoDB model and operations
  - **PointsHandlers.kt** - Core business logic for parsing, validation, and summary generation
- **support/Debug.kt** - Debug utilities and test functions

### Infrastructure (CDK)
- **cdk/KbotStack.kt** - AWS CDK infrastructure definition
  - DynamoDB table with local secondary indexes
  - Lambda functions with appropriate timeouts and memory
  - EventBridge rules for scheduled execution
  - API Gateway for REST endpoints

### Key Features
- Parses HTML content from discussions to extract point awards using regex
- Validates points by checking if referenced posts still exist
- Generates monthly and yearly summaries with formatted leaderboards
- Automatically rates posts with points when they are counted in
- Regularly posts current standing to the discussion's home space

## Configuration

- AWS region: eu-central-1 (configured in CDK)
- Secrets stored in `src/main/resources/secret.properties` (nyx_token)
- Target discussion IDs hardcoded in handlers (PROD: 11354, SANDBOX: 20310)
- Uses Kotlin code style "official" with trailing commas enabled

## Dependencies

- AWS Lambda Java runtime for serverless execution
- AWS CDK for infrastructure as code
- Ktor 2.3.7 for HTTP client
- Kotlinx serialization for JSON parsing
- AWS SDK 2.x for DynamoDB
- Strikt for testing assertions