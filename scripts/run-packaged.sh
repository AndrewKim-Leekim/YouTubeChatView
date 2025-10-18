#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="$ROOT_DIR/target/yt-multichat-javafx-1.0.0.jar"
LIB_DIR="$ROOT_DIR/target/lib"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Error: $JAR_PATH not found. Run 'mvn clean package' first." >&2
  exit 1
fi

if [[ ! -d "$LIB_DIR" ]]; then
  echo "Error: $LIB_DIR not found. The runtime libraries are missing. Run 'mvn clean package' again." >&2
  exit 1
fi

JAVA_CMD="${JAVA_HOME:-}"/bin/java
if [[ ! -x "$JAVA_CMD" ]]; then
  JAVA_CMD="java"
fi

exec "$JAVA_CMD" \
  --module-path "$LIB_DIR" \
  --add-modules javafx.controls,javafx.web \
  -jar "$JAR_PATH"
