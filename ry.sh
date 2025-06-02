#!/bin/sh
# ./ry.sh start 启动 stop 停止 restart 重启 status 状态
AppName=zjerp-admin.jar

# JVM参数
JVM_OPTS="-Dname=$AppName  -Duser.timezone=Asia/Shanghai -Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m"
APP_HOME=`pwd`
LOG_PATH=$APP_HOME/logs/$AppName.log

if [ "$1" = "" ];
then
    echo -e "\033[0;31m 未输入操作名 \033[0m  \033[0;34m {start|stop|restart|status} \033[0m"
    exit 1
fi

if [ "$AppName" = "" ];
then
    echo -e "\033[0;31m 未输入应用名 \033[0m"
    exit 1
fi

# 检查服务是否运行
check_service() {
    service_name=$1
    PID=$(ps -ef | grep $service_name | grep -v "grep" | awk '{print $2}')
    if [ -z "$PID" ]; then
        return 1  # 未运行
    else
        echo "$service_name 正在运行 (PID: $PID)"
        return 0
    fi
}

# 尝试启动服务
start_service() {
    service_name=$1
    case "$service_name" in
        mysqld)
            echo "尝试启动 mysqld..."
            mysqld --daemonize
            ;;
        redis-server)
            echo "尝试启动 redis-server..."
            redis-server /home/linuxbrew/.linuxbrew/etc/redis.conf
            ;;
        *)
            echo "不支持的服务: $service_name"
            exit 1
            ;;
    esac

    sleep 2  # 给点时间让服务启动
    check_service "$service_name"
    if [ $? -ne 0 ]; then
        echo -e "\033[0;31m 错误：无法启动 $service_name，请手动检查配置或权限！\033[0m"
        exit 1
    fi
}

start()
{
    # 检查并尝试启动依赖服务
    check_service "mysqld" || start_service "mysqld"
    check_service "redis-server" || start_service "redis-server"

    PID=`ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}'`

	if [ x"$PID" != x"" ]; then
	    echo "$AppName is running..."
	else
		nohup java $JVM_OPTS -jar $AppName > /dev/null 2>&1 &
        # java $JVM_OPTS -jar $AppName
		echo "Start $AppName success..."
	fi
}

stop()
{
    echo "Stop $AppName"

	PID=""
	query(){
		PID=`ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}'`
	}

	query
	if [ x"$PID" != x"" ]; then
		kill -TERM $PID
		echo "$AppName (pid:$PID) exiting..."
		while [ x"$PID" != x"" ]
		do
			sleep 1
			query
		done
		echo "$AppName exited."
	else
		echo "$AppName already stopped."
	fi
}

restart()
{
    stop
    sleep 2
    start
}

status()
{
    PID=`ps -ef |grep java|grep $AppName|grep -v grep|wc -l`
    if [ $PID != 0 ];then
        echo "$AppName is running..."
    else
        echo "$AppName is not running..."
    fi
}

case $1 in
    start)
    start;;
    stop)
    stop;;
    restart)
    restart;;
    status)
    status;;
    *)
esac