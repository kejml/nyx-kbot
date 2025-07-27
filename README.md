# KBOT for Nyx.cz

Simple single-purpose bot using API of Czech discussion board [Nyx.cz](https://nyx.cz) to keep scores in specific club on the server.

## Technical overview

Serverless application running on AWS using Lambda functions and DynamoDB, deployed with AWS CDK. The bot automatically tracks point awards in discussions, validates them, and generates monthly/yearly summaries.

### Architecture
- **Lambda Functions**: Individual handlers for scheduled updates and API endpoints
- **DynamoDB**: Points storage with local secondary indexes for efficient queries
- **API Gateway**: REST endpoints for testing and debugging
- **EventBridge**: Scheduled triggers for automated updates
- **AWS CDK**: Infrastructure as code using Kotlin

## Running and Deployment

### Prerequisites
- AWS CLI configured with appropriate permissions
- Node.js and npm (for CDK)
- Gradle and JDK 11+

### Build and Deploy
```bash
# Build the application
./gradlew build

# Deploy to AWS (builds and deploys infrastructure)
./deploy.sh    # Linux/Mac
deploy.bat     # Windows
```

### Development Commands
See [CLAUDE.md](CLAUDE.md) for detailed development commands and architecture information.
