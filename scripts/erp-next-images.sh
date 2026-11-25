#!/usr/bin/env bash
# Sourced by the WSL test/deploy entrypoints. No daemon configuration is changed.
export ERP_IMAGE_PREFIX="${ERP_IMAGE_PREFIX:-}"
if [[ -n "$ERP_IMAGE_PREFIX" && ! "$ERP_IMAGE_PREFIX" =~ ^[a-zA-Z0-9][a-zA-Z0-9._:/-]*/$ ]]; then
  echo "ERP_IMAGE_PREFIX must be a trusted registry/path prefix ending in / (no https://)" >&2
  return 2
fi
if [[ "$ERP_IMAGE_PREFIX" == *://* ]]; then
  echo "ERP_IMAGE_PREFIX must not include a URL scheme" >&2
  return 2
fi
erp_image_check(){
  local image full attempt
  for image in "$@"; do
    full="$ERP_IMAGE_PREFIX$image"
    if docker image inspect "$full" >/dev/null 2>&1; then
      echo "[Docker image] cached: $full"
      continue
    fi
    for attempt in 1 2; do
      echo "[Docker image] pull $full ($attempt/2)"
      if timeout 180 docker pull "$full"; then break; fi
      if ((attempt==2)); then
        cat >&2 <<'EOF'
Docker 镜像拉取失败，尚未开始 Java/前端构建或数据库测试。
请先检查 Docker daemon 的网络、DNS、代理或可信镜像仓库。
WSL 中仅设置 shell 的 HTTP_PROXY/HTTPS_PROXY 不会自动使 Docker daemon 的镜像拉取走代理。
Docker Engine: https://docs.docker.com/engine/daemon/proxy/
Docker Desktop: Settings > Resources > Proxies
可配置 ERP_IMAGE_PREFIX 指向你信任且包含相同标签的镜像仓库；
或者先在可访问 Docker Hub 的环境中 docker pull / docker save，再在本机 docker load。
不要关闭 TLS 验证，不要使用未经核验的公共镜像代理。
EOF
        return 125
      fi
      sleep 3
    done
  done
}
erp_build_images(){
  erp_image_check maven:3.9.9-eclipse-temurin-17 eclipse-temurin:17-jre node:22-alpine nginx:stable-alpine mysql:8.4 flyway/flyway:10.20.1
}
