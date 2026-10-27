#!/usr/bin/env python3
"""生成 Compose 本地配置；不覆盖已有文件，不输出密钥。"""
import argparse
import base64
import os
from pathlib import Path
import secrets

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", default=str(Path(__file__).resolve().parents[1] / ".env"))
args = parser.parse_args()
output = Path(args.output)
values = {
    "HTTP_PORT": "8088", "MYSQL_DATABASE": "server", "MYSQL_USER": "zjerp",
    "MYSQL_ROOT_PASSWORD": secrets.token_hex(24), "MYSQL_PASSWORD": secrets.token_hex(24),
    "REDIS_PASSWORD": secrets.token_hex(24),
    "TOKEN_SECRET": base64.b64encode(secrets.token_bytes(64)).decode(),
    "PUBLIC_API_URL": "http://localhost:8088/prod-api", "FRONTEND_CONTEXT": "../zjerp_frontend",
    "BOOTSTRAP_DATABASE": "true",
}
try:
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    raise SystemExit(f"配置已存在，未覆盖: {output}")
with os.fdopen(fd, "w") as config:
    config.write("\n".join(f"{key}={value}" for key, value in values.items()) + "\n")
print(f"已生成配置: {output}；请按部署域名调整 PUBLIC_API_URL。")
