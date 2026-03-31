#!/usr/bin/env bash
set -euo pipefail

CARWATCH_HOME="${CARWATCH_HOME:-/opt/carwatch}"
JAR_PATH="${CARWATCH_JAR_PATH:-$CARWATCH_HOME/carwatch-boot.jar}"
JAVA_OPTS="${JAVA_OPTS:--Xms128m -Xmx384m}"

exec java $JAVA_OPTS \
  -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
  -jar "$JAR_PATH"
