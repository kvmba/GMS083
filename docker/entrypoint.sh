#!/bin/sh
set -e

# 运行目录是挂载的宿主机根目录，确保日志目录存在
mkdir -p /workspace/gms-server/logs

if [ ! -f /app/BeiDou.jar ]; then
    echo "[BeiDou] 未找到 /app/BeiDou.jar" >&2
    echo "[BeiDou] 请先执行: docker compose up -d --build" >&2
    exit 1
fi

if [ ! -f /app/application.yml ]; then
    echo "[BeiDou] 警告: 未找到 /app/application.yml，将使用 jar 内默认配置" >&2
fi

exec java -server \
    -Xms1g -Xmx2g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+ParallelRefProcEnabled \
    -Dspring.config.location=optional:file:/app/application.yml \
    -jar /app/BeiDou.jar \
    "$@"