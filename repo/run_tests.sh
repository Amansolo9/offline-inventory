#!/usr/bin/env bash
set -euo pipefail

echo "=== Running Task-136 shared module unit tests ==="
echo ""

# Detect whether we are already inside the Docker container
# by checking for the Android SDK that the Dockerfile installs.
if [ -d "/opt/android-sdk" ]; then
    # Inside container – run Gradle directly
    if [ -f "./gradlew" ]; then
        GRADLE="./gradlew"
        chmod +x "$GRADLE" 2>/dev/null || true
    else
        GRADLE="gradle"
    fi

    $GRADLE :shared:testDebugUnitTest --no-daemon "$@"
else
    # Outside container – delegate to docker compose
    docker compose run --rm --no-deps \
        -e GRADLE_USER_HOME=/home/gradle/.gradle \
        app bash -lc \
        "chmod +x ./gradlew 2>/dev/null || true; ./gradlew :shared:testDebugUnitTest --no-daemon $*"
fi

echo ""
echo "=== All tests passed ==="
