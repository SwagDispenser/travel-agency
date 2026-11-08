#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
if [[ "$(uname -s)" == "Darwin" ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  export JAVA_HOME
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java_version="$(java -version 2>&1)"
if [[ "$java_version" != *'version "17.'* ]]; then
  echo "Select JDK 17 via JAVA_HOME before running this build." >&2
  exit 1
fi
exec mvn "$@"
