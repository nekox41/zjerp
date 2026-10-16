#!/bin/bash

# ==============================================================================
# RuoYi 后端项目启动脚本
# 放置在项目根目录 (zjerp_backend)
# ==============================================================================

# 设置终端颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}   ZJERP 后端服务启动脚本              ${NC}"
echo -e "${GREEN}=========================================${NC}"

# 1. 切换到脚本所在目录，确保相对路径正确
cd "$(dirname "$0")"

# 2. 检查 MySQL 服务状态
echo -n "检查 MySQL 服务状态... "
if pgrep -x "mysqld" > /dev/null; then
    echo -e "${GREEN}[运行中]${NC}"
else
    echo -e "${RED}[未运行]${NC}"
    echo -e "${YELLOW}尝试启动 MySQL...${NC}"
    sudo service mysql start
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}MySQL 启动成功！${NC}"
    else
        echo -e "${RED}MySQL 启动失败，请手动检查！${NC}"
        exit 1
    fi
fi

# 3. 检查 Redis 服务状态
echo -n "检查 Redis 服务状态... "
if pgrep -x "redis-server" > /dev/null; then
    echo -e "${GREEN}[运行中]${NC}"
else
    echo -e "${RED}[未运行]${NC}"
    echo -e "${YELLOW}尝试启动 Redis...${NC}"
    sudo service redis-server start
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Redis 启动成功！${NC}"
    else
        echo -e "${RED}Redis 启动失败，请手动检查！${NC}"
        exit 1
    fi
fi

# 4. 检查 Jar 包是否存在
JAR_FILE="./zjerp-admin/target/zjerp-admin.jar"
echo -n "检查 Jar 包是否存在... "
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}[缺失]${NC}"
    echo -e "${YELLOW}未找到 $JAR_FILE，请先在终端执行 'mvn clean package -DskipTests' 打包！${NC}"
    exit 1
else
    echo -e "${GREEN}[存在]${NC}"
fi

# 5. 检查是否已有相同服务在运行，避免端口冲突
PID=$(ps -ef | grep zjerp-admin.jar | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo -e "${YELLOW}检测到 zjerp-admin.jar 已在运行 (PID: $PID)，正在停止旧进程...${NC}"
    kill -15 $PID
    sleep 3
    # 如果还没有杀掉，强杀
    if ps -p $PID > /dev/null; then
        kill -9 $PID
    fi
    echo -e "${GREEN}旧进程已停止。${NC}"
fi

# 6. JVM 优化参数配置
# -Xms512m: 初始堆内存
# -Xmx1024m: 最大堆内存 (Codespaces 中 1G-2G 比较合适，避免 OOM)
# -XX:MetaspaceSize=128m: 元空间初始大小
# -XX:MaxMetaspaceSize=256m: 元空间最大大小
# -XX:+UseG1GC: 使用 G1 垃圾回收器 (适合多核大内存，现代 Java 首选)
# -XX:+HeapDumpOnOutOfMemoryError: 内存溢出时自动导出 Dump 文件
# -Dfile.encoding=UTF-8: 强制 UTF-8 编码，防止中文乱码
JVM_OPTS="-Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/heapdump.hprof -Dfile.encoding=UTF-8"

# 7. 启动应用
mkdir -p logs
LOG_FILE="./logs/zjerp-admin.log"

echo -e "${GREEN}正在启动 ZJERP 后端服务...${NC}"
echo -e "配置文件: prod (如需修改请编辑此脚本的 --spring.profiles.active)"
echo -e "日志文件: $LOG_FILE"

# 使用 nohup 后台启动，并将日志输出到指定文件
nohup java $JVM_OPTS -jar $JAR_FILE --spring.profiles.active=prod > $LOG_FILE 2>&1 &

# 8. 验证启动结果
NEW_PID=$!
sleep 5
if ps -p $NEW_PID > /dev/null; then
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}🎉 启动成功！${NC}"
    echo -e "进程 PID: ${YELLOW}$NEW_PID${NC}"
    echo -e "查看实时日志: ${YELLOW}tail -f logs/zjerp-admin.log${NC}"
    echo -e "${GREEN}=========================================${NC}"
else
    echo -e "${RED}=========================================${NC}"
    echo -e "${RED}❌ 启动失败，请查看日志排查错误：${NC}"
    echo -e "${YELLOW}tail -n 50 logs/zjerp-admin.log${NC}"
    echo -e "${RED}=========================================${NC}"
fi