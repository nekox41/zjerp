#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# 先执行 mvn -Dmaven.test.skip=true package；复用应用打包的依赖，无需额外测试库。
verification_dir=$(mktemp -d)
trap 'rm -rf "$verification_dir"' EXIT
python3 - "$verification_dir" <<'PYTHON'
import sys, zipfile
with zipfile.ZipFile('zjerp-admin/target/zjerp-admin.jar') as jar:
    for name in jar.namelist():
        if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
            jar.extract(name, sys.argv[1])
PYTHON
verification_cp="zjerp-system/target/classes:zjerp-common/target/classes:$verification_dir/BOOT-INF/lib/*"
javac -encoding UTF-8 -cp "$verification_cp" -d "$verification_dir" zjerp-system/src/test/java/top/aameow/system/service/SysFilePlusServiceTest.java
java -ea -cp "$verification_dir:$verification_cp" top.aameow.system.service.SysFilePlusServiceTest
