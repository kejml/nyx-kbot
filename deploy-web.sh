#!/bin/bash
set -e

R2_ENDPOINT=$(grep r2_endpoint src/main/resources/secret.properties | cut -d= -f2)
R2_BUCKET=$(grep r2_bucket_name src/main/resources/secret.properties | cut -d= -f2)

TIMESTAMP=$(date +%s)
sed "s/app\.js/app.js?v=$TIMESTAMP/g" web/index.html > index_deploy.html

echo "Uploading web files to R2..."
aws s3 cp index_deploy.html s3://$R2_BUCKET/index.html --endpoint-url $R2_ENDPOINT --profile r2
aws s3 sync web/ s3://$R2_BUCKET/ --endpoint-url $R2_ENDPOINT --profile r2 --exclude "index.html"
rm index_deploy.html

echo "Web files uploaded successfully!"
