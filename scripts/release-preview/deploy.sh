#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "$0")" && pwd)"
action=start
if [[ "$#" -gt 0 ]]; then action="$1"; fi
case "$action" in start|fresh|stop|status|logs|help|--help|-h) ;; *) echo "Usage: bash deploy.sh [start|fresh|stop|status|logs]"; exit 2;; esac
if [[ "$action" == help || "$action" == --help || "$action" == -h ]]; then
 echo "Preview only. Each start recreates only the private erp_next database."
 exit 0
fi
[[ "$(id -u)" != 0 ]] || { echo "请以普通用户运行，不要 sudo bash deploy.sh"; exit 1; }
STATE="$HOME/.local/share/zjerp-native-preview"
[[ ! -L "$STATE" ]] || { echo "拒绝符号链接状态目录"; exit 1; }
mkdir -p "$STATE"
[[ "$(stat -c %u "$STATE")" == "$(id -u)" ]] || { echo "状态目录归属无效"; exit 1; }
command -v flock >/dev/null || { echo "缺少 flock，请先安装 util-linux"; exit 1; }
exec 9>>"$STATE/lock"
if ! flock -n 9; then
 if [[ "$action" == start || "$action" == fresh ]]; then
  holder=""
  for proc in /proc/[0-9]*; do
   pid="$(basename "$proc")"
   [[ "$pid" != "$$" ]] || continue
   [[ -r "$proc/status" && "$(stat -c %u "$proc" 2>/dev/null || true)" == "$(id -u)" ]] || continue
   cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
   [[ "$cmd" == *preview-deploy.py* || "$cmd" == *deploy.sh* ]] || continue
   for fd in "$proc"/fd/*; do
    target="$(readlink "$fd" 2>/dev/null || true)"
    if [[ "$target" == "$STATE/lock" ]]; then holder="$pid"; break; fi
   done
   [[ -n "$holder" ]] && break
  done
  if [[ -n "$holder" ]]; then
   echo "发现旧部署脚本 PID=$holder，正在终止..."
   kill -TERM "$holder" 2>/dev/null || true
   acquired=0
   for i in $(seq 1 150); do
    if flock -n 9; then acquired=1; break; fi
    sleep 0.1
   done
   if [[ "$acquired" != 1 ]]; then
    alive=0
    for fd in /proc/"$holder"/fd/*; do
     [[ "$(readlink "$fd" 2>/dev/null || true)" == "$STATE/lock" ]] && alive=1
    done
    if [[ "$alive" == 1 && -r "/proc/$holder/cmdline" && "$(stat -c %u "/proc/$holder" 2>/dev/null || true)" == "$(id -u)" ]]; then
     cmd="$(tr '\0' ' ' < "/proc/$holder/cmdline" 2>/dev/null || true)"
     if [[ "$cmd" == *preview-deploy.py* || "$cmd" == *deploy.sh* ]]; then kill -KILL "$holder" 2>/dev/null || true; fi
    fi
    for i in $(seq 1 150); do
     if flock -n 9; then acquired=1; break; fi
     sleep 0.1
    done
   fi
   [[ "$acquired" == 1 ]] || { echo "无法安全取得部署锁，已停止本次部署"; exit 1; }
  else
   echo "部署锁被占用，但无法确认持有者身份；拒绝终止其他进程"
   exit 1
  fi
 else
  flock 9
 fi
fi
if [[ "$action" == start || "$action" == fresh ]]; then
 (cd "$ROOT" && sha256sum -c SHA256SUMS --quiet)
 java17="/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)/bin/java"
 java_ok(){ [[ -x "$java17" ]] || { command -v java >/dev/null && java -version 2>&1 | grep -E 'version "17[.]' >/dev/null; }; }
 packages=""
 java_ok || packages="$packages openjdk-17-jre-headless"
 command -v nginx >/dev/null || packages="$packages nginx"
 command -v python3 >/dev/null || packages="$packages python3"
 command -v curl >/dev/null || packages="$packages curl"
 if [[ ! -x /usr/lib/postgresql/17/bin/initdb || ! -x /usr/lib/postgresql/17/bin/psql ]]; then
  . /etc/os-release
  case "$ID:$VERSION_CODENAME" in ubuntu:jammy|ubuntu:noble|ubuntu:resolute|debian:bookworm|debian:trixie) ;; *) echo "请先手工安装 PostgreSQL 17"; exit 1;; esac
  sudo apt-get update
  if ! apt-cache show postgresql-17 >/dev/null 2>&1; then
   sudo apt-get install -y curl ca-certificates gnupg
   key="$(mktemp)"
   curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc -o "$key"
   gpg --batch --show-keys --with-colons "$key" | grep 'B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8' >/dev/null || { rm -f "$key"; exit 1; }
   sudo install -d /usr/share/postgresql-common/pgdg
   sudo install -m 0644 "$key" /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc
   rm -f "$key"
   printf 'Types: deb\nURIs: https://apt.postgresql.org/pub/repos/apt/\nSuites: %s-pgdg\nComponents: main\nSigned-By: /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc\n' "$VERSION_CODENAME" | sudo tee /etc/apt/sources.list.d/zjerp-pgdg.sources >/dev/null
   sudo apt-get update
  fi
  packages="$packages postgresql-17 postgresql-client-17"
 fi
 if [[ -n "$packages" ]]; then
  echo "安装缺少的运行依赖：$packages"
  sudo apt-get update
  sudo apt-get install -y --no-install-recommends $packages
 fi
 java_ok || { echo "未找到 Java 17"; exit 1; }
 [[ -x "$java17" ]] || java17="$(command -v java)"
 export ERP_PREVIEW_JAVA="$java17"
fi
command -v python3 >/dev/null || { echo "缺少 Python3"; exit 1; }
exec python3 "$ROOT/preview-deploy.py" "$action"
