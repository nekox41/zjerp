#!/usr/bin/env bash
# Compatibility entry point. Builds current sources and runs the dev profile.
set -euo pipefail
exec bash "$(dirname "$0")/scripts/dev-backend.sh" "$@"
