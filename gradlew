#!/bin/sh
# Gradle wrapper – generated
DIRNAME="$(cd "$(dirname "$0")" && pwd)"
exec "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" "$@" 2>/dev/null || \
  java -jar "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" "$@"
