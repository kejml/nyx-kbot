#!/usr/bin/env node
const { spawn } = require('child_process');
const path = require('path');

// Determine the correct gradlew command based on platform
const isWindows = process.platform === 'win32';
const gradlewCmd = isWindows ? 'gradlew.bat' : 'gradlew';
const gradlewPath = path.join('..', gradlewCmd);

// Spawn the gradle process
const gradle = spawn(gradlewPath, [':cdk:run'], {
    stdio: 'inherit',
    shell: isWindows
});

gradle.on('close', (code) => {
    process.exit(code);
});

gradle.on('error', (err) => {
    console.error('Failed to start gradle:', err);
    process.exit(1);
});