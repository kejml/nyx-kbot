#!/bin/bash
set -e

# Check if parameter was provided
CREATE_TABLE_PARAM="--parameters CreateTable=false"
if [ "$1" = "--create-new-table" ]; then
    CREATE_TABLE_PARAM=""
    echo "Creating new DynamoDB table if needed..."
else
    echo "Using existing DynamoDB table..."
fi

echo "Building Lambda JAR..."
./gradlew fatJar

echo "Deploying with CDK..."
cd cdk

if [ -z "$CREATE_TABLE_PARAM" ]; then
    npx cdk deploy --require-approval never --profile kbot
else
    npx cdk deploy --require-approval never --profile kbot $CREATE_TABLE_PARAM
fi

echo "Deployment completed successfully!"
cd ..

echo "Uploading web files to R2..."
R2_ENDPOINT=$(grep r2_endpoint src/main/resources/secret.properties | cut -d= -f2)
R2_BUCKET=$(grep r2_bucket_name src/main/resources/secret.properties | cut -d= -f2)

aws s3 sync web/ s3://$R2_BUCKET/ --endpoint-url $R2_ENDPOINT --profile r2

echo "Web files uploaded successfully!"