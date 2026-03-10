@echo off
setlocal enabledelayedexpansion

for /f "tokens=2 delims==" %%a in ('findstr "r2_endpoint" src\main\resources\secret.properties') do set R2_ENDPOINT=%%a
for /f "tokens=2 delims==" %%a in ('findstr "r2_bucket_name" src\main\resources\secret.properties') do set R2_BUCKET=%%a

for /f "tokens=2 delims==" %%a in ('wmic os get localdatetime /value ^| findstr "="') do set DT=%%a
set TIMESTAMP=%DT:~0,14%
powershell -Command "(Get-Content web\index.html) -replace 'app\.js', 'app.js?v=%TIMESTAMP%' | Set-Content index_deploy.html"

echo Uploading web files to R2...
call aws s3 cp index_deploy.html s3://%R2_BUCKET%/index.html --endpoint-url %R2_ENDPOINT% --profile r2
call aws s3 sync web/ s3://%R2_BUCKET%/ --endpoint-url %R2_ENDPOINT% --profile r2 --exclude "index.html"
del index_deploy.html

if !errorlevel! neq 0 (
    echo Web upload to R2 failed!
    exit /b 1
)

echo Web files uploaded successfully!
