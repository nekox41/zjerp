#!/usr/bin/env bash
# WSL2/Ubuntu: Docker supplies Java, Maven and Node. Never touch deployment volumes.
set -Eeuo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
mode="${1:---full}"
case "$mode" in --full|--new-only|--smoke|--static) ;; *) echo "Usage: bash test.sh [--full|--new-only|--smoke|--static]"; exit 2;; esac
mkdir -p test-results
log="$(mktemp "$ROOT/test-results/test-$(date +%Y%m%d-%H%M%S)-XXXXXX.log")"
echo "测试日志: $log"
# Wait for the logger and preserve failures from either the runner or tee.
set +e
bash scripts/erp-next-docker-test.sh "$mode" 2>&1 | tee -a "$log"
rc=$?
set -e
if ((rc==0)); then
  echo "TEST RESULT: PASS ($mode)" | tee -a "$log"
else
  echo "TEST RESULT: FAIL ($mode), exit=$rc" | tee -a "$log"
fi
echo "日志: $log"
exit "$rc"
