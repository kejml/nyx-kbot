# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KBOT is a bot for the Czech discussion board Nyx.cz that tracks scoring in multiple clubs. It's built with Kotlin and deployed to AWS using AWS CDK, leveraging Lambda functions and DynamoDB storage. A web dashboard served via Cloudflare R2 provides interactive visualizations.

## Development Commands

### Build and Test
- `./gradlew build` - Build the main project
- `./gradlew test` - Run tests using JUnit 5
- `./gradlew ktlintCheck` - Check code style with ktlint
- `./gradlew ktlintFormat` - Auto-format code with ktlint
- `./gradlew fatJar` - Build fat JAR for Lambda deployment

### Deployment
- `./deploy.sh` (Linux/Mac) or `deploy.bat` (Windows) - Build fat JAR, deploy CDK, and sync web files to R2
- `./deploy-web.sh` / `deploy-web.bat` - Deploy web files to R2 only (no Lambda/CDK rebuild)
- `cd cdk && npx cdk deploy --profile kbot` - Deploy only the infrastructure
- `cd cdk && npx cdk destroy` - Remove all AWS resources

## Architecture

The codebase follows a serverless architecture with individual Lambda functions:

### Core Components
- **lambda/LambdaHandlers.kt** - AWS Lambda request handlers
  - `HourlyUpdateHandler` - Scheduled hourly points update and home standings refresh
  - `MonthlySummaryHandler` - Monthly summary generation (1st of month at 01:10 UTC)
  - `YearlySummaryHandler` - Yearly summary generation (Jan 1st at 01:10 UTC)
  - `HelloHandler` - Test endpoint at `/hello`
  - `NyxTestHandler` - Debug endpoint at `/nyx-test`
- **lambda/WebsiteDataGeneratorHandler.kt** - Generates JSON data for the web dashboard
  - Runs hourly per discussion
  - Pulls all points from DynamoDB and uploads `data/{discussionId}.json` to Cloudflare R2
- **lambda/ScheduledActions.kt** - Legacy object (deprecated, use handlers instead)
- **api/NyxClient.kt** - HTTP client for Nyx.cz API communication
  - Handles authentication via Bearer token
  - Supports discussion reading, posting, and rating
- **api/Discussion.kt** - Data models for Nyx.cz API responses (`Discussion`, `Post`)
- **api/Home.kt** - Home page content models (`Home`, `Item`)
- **storage/** - DynamoDB operations and business logic
  - **Points.kt** - DynamoDB model and operations for regular points
  - **BonusPoints.kt** - DynamoDB operations for bonus points (`bonusPoints` table)
  - **PointsHandlers.kt** - Core business logic for parsing, validation, and summary generation
    - `PointType` enum (BOD, BONUS) drives the parsing keyword and the derived Nyx search text
- **support/Debug.kt** - Debug utilities and test functions

### Web Dashboard
- **web/index.html** - Interactive dashboard built with Highcharts Stock
  - Time-series chart, day-of-week/hourly distributions, top 20 receivers/givers
  - Multi-filter support: by user (receiver/giver), by hours, by points value
  - Supports multiple discussions via URL slug or `?discussion=` query param
  - Fetches data from `data/{discussionId}.json` on R2
- **web/data/** - Directory for generated JSON data files (populated at runtime)
- Hosted at https://kbot.kejml.eu via Cloudflare R2

### Infrastructure (CDK)
- **cdk/KbotStack.kt** - AWS CDK infrastructure definition
  - DynamoDB table `points` with local secondary indexes (`dateTimeIndex`, `lastId`)
  - DynamoDB table `bonusPoints` (PK `discussionId`, SK `questionIdPostId` = `"questionId#postId"`, allowing multiple bonus points per question) with the same LSIs
  - Per-discussion Lambda function sets (Java 21, 512MB, 15min timeout):
    - **PoznejPcHru** (discussionId: 11354) - ENABLED
    - **ZabavnyKviz** (discussionId: 7045) - ENABLED
    - **Sandbox** (discussionId: 20310) - DISABLED
  - Each enabled discussion gets: `HourlyUpdate`, `MonthlySummary`, `YearlySummary`, `WebsiteDataGenerator` functions
  - EventBridge rules for scheduled execution
  - API Gateway for REST endpoints (`/hello`, `/nyx-test`)
  - Conditional DynamoDB table creation via `CreateTable` CDK parameter

### Key Features
- Parses HTML content from discussions to extract point awards using regex
- Two point types: regular (keyword BOD, `points` table, one per question) and bonus (keyword BONUS, `bonusPoints` table, multiple per question); both are collected hourly and rated, but only regular points are reported (summaries, standings, web) for now
- Validates points by checking if referenced posts still exist
- Generates monthly and yearly summaries with formatted leaderboards
- Automatically rates posts with points when they are counted in
- Regularly posts current standing to the discussion's home space
- Exports point data as JSON to Cloudflare R2 for web dashboard consumption

## Configuration

- AWS region: eu-central-1; AWS profile: `kbot`
- Secrets stored in `src/main/resources/secret.properties` (nyx_token, R2 credentials)
- Discussions configured in CDK (not hardcoded in handlers); environment variables passed to each Lambda:
  - `TABLE_NAME`, `DISCUSSION_ID`, `HOME_CONTENT_ID`, `HALL_OF_FAME_CONTENT_ID`, `START_FROM_POST_ID`
  - `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET_NAME`
- Uses Kotlin code style "official" with trailing commas enabled

## Dependencies

- AWS Lambda Java runtime (Java 21) for serverless execution
- AWS CDK 2.199.0 for infrastructure as code
- Ktor 3.2.2 for HTTP client (CIO engine)
- Kotlinx serialization 1.9.0 for JSON parsing
- AWS SDK 2.34.0 for DynamoDB and S3 (also used for Cloudflare R2 via endpoint override)
- Strikt for testing assertions
- Kotlin 2.3.10
