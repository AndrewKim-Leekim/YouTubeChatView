#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/build-app-image.sh [--arch intel|arm] [--type app|dmg]

Options:
  --arch   Target macOS architecture. "intel" uses the default JavaFX (classifier mac),
           "arm" enables the mac-aarch64 Maven profile. Default: intel.
  --type   Packaging type to hand off to jpackage. "app" produces an .app bundle,
           "dmg" produces a mountable disk image. Default: app.

The script expects JDK 17+ with the `jpackage` tool available.
USAGE
}

ARCH="intel"
PACKAGE_TYPE="app"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --arch)
      ARCH="$2"
      shift 2
      ;;
    --type)
      PACKAGE_TYPE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/target"
JAR_NAME="yt-multichat-javafx-1.0.0.jar"
RUNTIME_IMAGE_NAME="yt-multichat"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "Error: jpackage not found. Install JDK 17+ with the jpackage tool." >&2
  exit 1
fi

MVN_CMD=("mvn")
if [[ "$ARCH" == "arm" ]]; then
  MVN_CMD+=("-Pmac-aarch64")
fi
MVN_CMD+=("clean" "package")

printf '==> Building project (%s)\n' "${MVN_CMD[*]}"
"${MVN_CMD[@]}"

APP_IMAGE_DEST="$TARGET_DIR/dist"
mkdir -p "$APP_IMAGE_DEST"

JPACKAGE_TYPE="app-image"
case "$PACKAGE_TYPE" in
  app)
    JPACKAGE_TYPE="app-image"
    ;;
  dmg)
    JPACKAGE_TYPE="dmg"
    ;;
  *)
    echo "Unsupported package type: $PACKAGE_TYPE" >&2
    exit 1
    ;;
endcase

printf '==> Running jpackage (--type %s)\n' "$JPACKAGE_TYPE"

JPACKAGE_ARGS=(
  "--type" "$JPACKAGE_TYPE"
  "--name" "TubeMultiView"
  "--app-version" "1.0.0"
  "--input" "$TARGET_DIR"
  "--main-jar" "$JAR_NAME"
  "--main-class" "app.Main"
  "--runtime-image" "$TARGET_DIR/$RUNTIME_IMAGE_NAME"
  "--dest" "$APP_IMAGE_DEST"
)

if [[ "$JPACKAGE_TYPE" == "dmg" ]]; then
  # Keep the .app alongside the DMG for manual signing or testing.
  JPACKAGE_ARGS+=("--install-dir" "/Applications")
fi

jpackage "${JPACKAGE_ARGS[@]}"

printf '\nArtifacts are available under %s\n' "$APP_IMAGE_DEST"
if [[ "$JPACKAGE_TYPE" == "app-image" ]]; then
  printf '  - %s/TubeMultiView.app\n' "$APP_IMAGE_DEST"
else
  printf '  - %s/TubeMultiView*.dmg\n' "$APP_IMAGE_DEST"
fi
