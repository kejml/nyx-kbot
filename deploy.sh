#!/bin/bash
set -e

echo "Building Lambda JAR..."
./gradlew fatJar

echo "Deploying with CDK..."
cd cdk
npx cdk deploy --require-approval never --profile kbot

echo "Deployment completed successfully!"
cd ..