#!/usr/bin/env bash
# Preview only. Run as an ordinary Linux user; sudo is used only for missing packages.
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "$0")" && pwd)"
action="${1:-start}"
case "$action" in start|fresh|stop|status|logs|help|--help|-h) ;; *) echo "Usage: bash deploy.sh [start|fresh|stop|status|logs]"; exit 2;; esac
if [[ "$action" =~ ^(help|--help|-h)$ ]]; then
 echo "Preview only: bash deploy.sh [start|fresh|stop|status|logs]. Start/fresh resets the dedicated database and attachments. Never use for production."
 exit 0
fi
[[ "$(id -u)" != 0 ]] || { echo "请以普通用户运行，不要 sudo bash deploy.sh"; exit 1; }
[[ $# -le 1 ]] || exit 2
if [[ "$action" == start || "$action" == fresh ]]; then
 (cd "$ROOT" && sha256sum -c SHA256SUMS --quiet)
 java17=/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)/bin/java
 java_ok(){ [[ -x "$java17" ]] || { command -v java >/dev/null && java -version 2>&1 | grep -Eq 'version "17[.]'; }; }
 packages=()
 java_ok || packages+=(openjdk-17-jre-headless)
 command -v nginx >/dev/null || packages+=(nginx)
 command -v python3 >/dev/null || packages+=(python3)
 command -v flock >/dev/null || packages+=(util-linux)
 command -v curl >/dev/null || packages+=(curl)
 if [[ ! -x /usr/lib/postgresql/17/bin/initdb || ! -x /usr/lib/postgresql/17/bin/psql ]]; then
   . /etc/os-release
   case "$ID:$VERSION_CODENAME" in ubuntu:jammy|ubuntu:noble|ubuntu:resolute|debian:bookworm|debian:trixie) ;; *) echo "请先手工安装 PostgreSQL 17；自动安装支持 Ubuntu 22.04/24.04/26.04、Debian 12/13"; exit 1;; esac
   sudo apt-get update
   if ! apt-cache show postgresql-17 >/dev/null 2>&1; then
     sudo apt-get install -y curl ca-certificates gnupg
     key="$(mktemp)"
     curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc -o "$key"
     gpg --batch --show-keys --with-colons "$key" | grep -q 'B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8' || { rm -f "$key"; exit 1; }
     sudo install -d /usr/share/postgresql-common/pgdg
     sudo install -m 0644 "$key" /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc
     rm -f "$key"
     printf 'Types: deb\nURIs: https://apt.postgresql.org/pub/repos/apt/\nSuites: %s-pgdg\nComponents: main\nSigned-By: /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc\n' "$VERSION_CODENAME" | sudo tee /etc/apt/sources.list.d/zjerp-pgdg.sources >/dev/null
     sudo apt-get update
   fi
   packages+=(postgresql-17 postgresql-client-17)
 fi
 if ((${#packages[@]})); then
   echo "安装缺少的运行依赖：${packages[*]}"
   sudo apt-get update
   sudo apt-get install -y --no-install-recommends "${packages[@]}"
 fi
 java_ok || { echo "未找到 Java 17"; exit 1; }
 [[ -x "$java17" ]] || java17="$(command -v java)"
 export ERP_PREVIEW_JAVA="$java17"
fi
command -v python3 >/dev/null || { echo "缺少 Python3"; exit 1; }
exec python3 "$ROOT/preview-deploy.py" "$action"
