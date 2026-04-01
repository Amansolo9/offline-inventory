#!/usr/bin/env bash
set -euo pipefail

echo "=== Running Task-136 shared module unit tests ==="
echo ""

# Use Gradle wrapper if available, otherwise fall back to system gradle
if [ -f "./gradlew" ]; then
    GRADLE="./gradlew"
    chmod +x "$GRADLE" 2>/dev/null || true
else
    GRADLE="gradle"
fi

$GRADLE :shared:testDebugUnitTest --no-daemon "$@"

echo ""
echo "=== All tests passed ==="
