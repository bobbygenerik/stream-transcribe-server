#!/usr/bin/env bash
# Minimal Gradle wrapper stub
# This wrapper expects a gradle wrapper jar at gradle/wrapper/gradle-wrapper.jar
# If the jar is missing, this script will attempt to download the distribution using system gradle
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
PROPS="$DIR/gradle/wrapper/gradle-wrapper.properties"
if [ -x "$WRAPPER_JAR" ]; then
  java -jar "$WRAPPER_JAR" "$@"
else
  echo "gradle wrapper jar not found at $WRAPPER_JAR"
  if command -v gradle >/dev/null 2>&1; then
    echo "Invoking system gradle to generate wrapper jar (requires internet)"
    (cd "$DIR" && gradle wrapper)
    if [ -f "$WRAPPER_JAR" ]; then
      java -jar "$WRAPPER_JAR" "$@"
    else
      echo "Failed to generate gradle wrapper jar; falling back to system gradle to run task"
      gradle "$@"
    fi
  else
    echo "No system gradle available; please run 'gradle wrapper --gradle-version <version>' locally to generate wrapper jar." >&2
    exit 1
  fi
fi
