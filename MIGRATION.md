# Migration from Kotless to AWS CDK

This document describes the migration from Kotless to AWS CDK that was completed.

## What Changed

### Dependencies
- **Removed**: Kotless dependencies and plugin
- **Added**: AWS Lambda Java runtime, AWS CDK for infrastructure
- **Updated**: Kotlin version to 1.9.21, Ktor to 2.3.7

### Architecture
- **Before**: Single `ScheduledActions` object with Kotless annotations
- **After**: Individual Lambda handler classes implementing AWS Lambda interfaces
- **Infrastructure**: Moved from Kotless+Terraform to pure AWS CDK

### Project Structure
```
├── src/main/kotlin/
│   ├── lambda/
│   │   ├── LambdaHandlers.kt    # NEW: Individual Lambda handlers
│   │   └── ScheduledActions.kt  # DEPRECATED: Legacy object
│   ├── api/                     # UNCHANGED: HTTP client
│   ├── storage/                 # UNCHANGED: Business logic
│   └── support/                 # MODIFIED: Removed Kotless annotations
├── cdk/                         # NEW: CDK infrastructure
│   ├── src/main/kotlin/
│   │   └── eu/kejml/nyx/kbot/cdk/
│   │       ├── KbotApp.kt
│   │       └── KbotStack.kt
│   ├── build.gradle.kts
│   ├── package.json
│   └── cdk.json
├── deploy.sh / deploy.bat       # NEW: Deployment scripts
└── src/main/tf/                 # REMOVED: Terraform files
```

## Deployment Changes

### Before (Kotless)
```bash
./gradlew deploy
```

### After (CDK)
```bash
# Full deployment
./deploy.sh  # or deploy.bat on Windows

# Manual steps
./gradlew fatJar
cd cdk
npx cdk deploy
```

## Lambda Functions

| Function | Handler Class | Schedule | Purpose |
|----------|---------------|----------|---------|
| HourlyUpdateHandler | `eu.kejml.nyx.kbot.lambda.HourlyUpdateHandler` | Every hour | Update points from discussion |
| MonthlySummaryHandler | `eu.kejml.nyx.kbot.lambda.MonthlySummaryHandler` | 1st day, 01:10, months 2-12 | Post monthly summary |
| YearlySummaryHandler | `eu.kejml.nyx.kbot.lambda.YearlySummaryHandler` | 1st day, 01:10, January | Post yearly summary |
| UpdateHomeHandler| `eu.kejml.nyx.kbot.lambda.YearlySummaryHandler` | Every Monday 4:15 | Update current score on home |
| HelloHandler | `eu.kejml.nyx.kbot.lambda.HelloHandler` | API Gateway `/hello` | Test endpoint |
| NyxTestHandler | `eu.kejml.nyx.kbot.lambda.NyxTestHandler` | API Gateway `/nyx-test` | Debug endpoint |

## Infrastructure

### DynamoDB Table
- Name: `points`
- Same structure as before (partition key: `discussionId`, sort key: `questionId`)
- Local secondary indexes: `dateTimeIndex`, `lastId`
- Provisioned capacity: 20 read/20 write

### API Gateway
- REST API with `/hello` and `/nyx-test` endpoints
- Same functionality as before

### EventBridge Rules
- Hourly schedule for points update
- Monthly schedule (cron: `10 01 1 2-12 ? *`)
- Yearly schedule (cron: `10 01 1 1 ? *`)

## Prerequisites

1. **AWS CLI** configured with appropriate credentials
2. **AWS CDK** installed: `npm install -g aws-cdk`
3. **Node.js** for CDK CLI
4. **Java 11+** for building Kotlin code

## Migration Steps Completed

1. ✅ Updated `build.gradle.kts` to remove Kotless and add Lambda dependencies
2. ✅ Created individual Lambda handler classes
3. ✅ Removed Kotless annotations from existing code
4. ✅ Created CDK infrastructure in `cdk/` directory
5. ✅ Added deployment scripts
6. ✅ Updated documentation

## Benefits

- **Maintainable**: AWS CDK is actively maintained unlike Kotless
- **Type-safe**: Infrastructure as code in Kotlin
- **Flexible**: Full control over AWS resources
- **Modern**: Latest Kotlin and AWS SDK versions
- **Clear separation**: Business logic vs infrastructure code