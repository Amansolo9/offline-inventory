#!/usr/bin/env sh
# Task-136 test runner.
#
# Contract:
#   - Docker-first: by default runs tests inside the Dockerfile.android container
#     so a reviewer needs only Docker to execute the suite.
#   - If already inside the container (detected via /opt/android-sdk) runs Gradle directly.
#   - TASK136_FORCE_LOCAL=1 opts into a local-SDK fallback (ANDROID_HOME must be set).
#
# Uses /bin/sh (POSIX) so it runs on any image including Alpine-based gradle images.

set -eu

echo "=== Task-136 shared + composeApp unit tests ==="

# 1. Inside the container: run Gradle directly.
if [ -d "/opt/android-sdk" ]; then
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew 2>/dev/null || true
        ./gradlew :shared:testDebugUnitTest :composeApp:testDebugUnitTest --no-daemon --project-cache-dir /tmp/gradle-test-cache "$@"
    else
        gradle :shared:testDebugUnitTest :composeApp:testDebugUnitTest --no-daemon --project-cache-dir /tmp/gradle-test-cache "$@"
    fi
    echo ""
    echo "=== Tests complete (in-container) ==="
    exit 0
fi

# 2. Docker-first host path (recommended, fully contained).
if command -v docker >/dev/null 2>&1 && [ "${TASK136_FORCE_LOCAL:-0}" != "1" ]; then
    echo "Running inside Docker container (no local SDK required)..."
    docker compose stop app 2>/dev/null || true

    # Disable MSYS/Git-Bash path conversion so unix-style paths are passed
    # through to docker unchanged on Windows hosts.
    export MSYS_NO_PATHCONV=1
    export MSYS2_ARG_CONV_EXCL="*"

    # Override the image's entrypoint to /bin/sh. The eclipse-temurin base
    # image already sets JAVA_HOME, PATH and ships bash.
    docker compose run --rm --no-deps \
        --entrypoint //bin/sh \
        -e GRADLE_USER_HOME=/tmp/gradle-test \
        app -c "chmod +x ./gradlew 2>/dev/null || true; ./gradlew :shared:testDebugUnitTest :composeApp:testDebugUnitTest --no-daemon --project-cache-dir /tmp/gradle-test-cache $*"
    echo ""
    echo "=== Tests complete (Docker) ==="
    exit 0
fi

# 3. Opt-in local fallback (requires ANDROID_HOME).
if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
    echo "TASK136_FORCE_LOCAL=1 set - using local Android SDK at ${ANDROID_HOME}"
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew 2>/dev/null || true
        ./gradlew :shared:testDebugUnitTest :composeApp:testDebugUnitTest --no-daemon "$@"
    elif [ -f "./gradlew.bat" ]; then
        ./gradlew.bat :shared:testDebugUnitTest :composeApp:testDebugUnitTest --no-daemon "$@"
    else
        echo "ERROR: no gradlew found" >&2
        exit 1
    fi
    echo ""
    echo "=== Tests complete (local SDK) ==="
    exit 0
fi

echo "ERROR: Docker is not available and TASK136_FORCE_LOCAL=1 not set." >&2
echo "Install Docker Desktop, or set TASK136_FORCE_LOCAL=1 with ANDROID_HOME pointing to an Android SDK." >&2
exit 1
