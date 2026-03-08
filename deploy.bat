@echo off
setlocal enabledelayedexpansion

REM Check if parameter was provided
set "CREATE_TABLE_PARAM=--parameters CreateTable=false"
if "%1"=="--create-new-table" (
    set "CREATE_TABLE_PARAM="
    echo Creating new DynamoDB table if needed...
) else (
    echo Using existing DynamoDB table...
)

echo Building Lambda JAR...
call gradlew.bat fatJar

if !errorlevel! neq 0 (
    echo Build failed!
    exit /b 1
)

echo Deploying with CDK...
cd /d cdk

if "!CREATE_TABLE_PARAM!"=="" (
    call npx cdk deploy --require-approval never --profile kbot
) else (
    call npx cdk deploy --require-approval never --profile kbot !CREATE_TABLE_PARAM!
)

if !errorlevel! neq 0 (
    echo CDK deployment failed!
    cd /d ..
    exit /b 1
)

echo Deployment completed successfully!
cd /d ..

echo Uploading web files to R2...
for /f "tokens=2 delims==" %%a in ('findstr "r2_endpoint" src\main\resources\secret.properties') do set R2_ENDPOINT=%%a
for /f "tokens=2 delims==" %%a in ('findstr "r2_bucket_name" src\main\resources\secret.properties') do set R2_BUCKET=%%a

call aws s3 sync web/ s3://%R2_BUCKET%/ --endpoint-url %R2_ENDPOINT% --profile r2

if !errorlevel! neq 0 (
    echo Web upload to R2 failed!
    exit /b 1
)

echo Web files uploaded successfully!