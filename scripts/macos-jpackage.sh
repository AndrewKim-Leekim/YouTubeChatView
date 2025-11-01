#!/usr/bin/env bash
set -euo pipefail

# Required env vars
: "${APP_NAME:?APP_NAME is required}"
: "${APP_VERSION:?APP_VERSION is required}"
: "${MODULE_SPEC:?MODULE_SPEC is required}"
: "${RUNTIME_IMAGE:?RUNTIME_IMAGE is required}"
: "${ICON:?ICON is required}"
: "${DEST:?DEST is required}"
: "${MAC_PACKAGE_IDENTIFIER:?MAC_PACKAGE_IDENTIFIER is required}"

trim() {
  local value="$1"
  # shellcheck disable=SC2001
  echo "${value}" | sed 's/^\s\+//;s/\s\+$//'
}

SIGN_IDENTITY=$(trim "${MAC_SIGN_IDENTITY:-}")
SIGN_KEYCHAIN=$(trim "${MAC_SIGN_KEYCHAIN:-}")

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)
  else
    echo "JAVA_HOME is not set and /usr/libexec/java_home is unavailable" >&2
    exit 1
  fi
fi

JPACKAGE_BIN="${JAVA_HOME}/bin/jpackage"
if [[ ! -x "${JPACKAGE_BIN}" ]]; then
  echo "Could not locate jpackage at ${JPACKAGE_BIN}. Ensure JDK 17+ is installed." >&2
  exit 1
fi

mkdir -p "${DEST}"

JPACKAGE_ARGS=(
  --type dmg
  --name "${APP_NAME}"
  --app-version "${APP_VERSION}"
  --dest "${DEST}"
  --runtime-image "${RUNTIME_IMAGE}"
  --module "${MODULE_SPEC}"
  --icon "${ICON}"
  --mac-package-name "${APP_NAME}"
  --mac-package-identifier "${MAC_PACKAGE_IDENTIFIER}"
)

if [[ -n "${SIGN_IDENTITY}" ]]; then
  JPACKAGE_ARGS+=(--mac-sign --mac-signing-key-user-name "${SIGN_IDENTITY}")
  if [[ -n "${SIGN_KEYCHAIN}" ]]; then
    JPACKAGE_ARGS+=(--mac-signing-keychain "${SIGN_KEYCHAIN}")
  fi
fi

"${JPACKAGE_BIN}" "${JPACKAGE_ARGS[@]}"
