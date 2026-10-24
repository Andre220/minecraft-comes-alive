#!/bin/sh
# Runs gradle in the MCA checkout only when the dev client is closed (Linux).
# Usage: scripts/claude-tools/safe_gradle.sh :fabric:compileJava ...
if pgrep -f 'KnotClient|fabric.dli.env=client' >/dev/null 2>&1; then
  echo "CLIENT OPEN - gradle skipped"
  exit 3
fi
cd "$(dirname "$0")/../.." && ./gradlew "$@"
