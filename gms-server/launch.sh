#!/bin/sh

# 加载仓库根目录 .env(若存在)注入环境变量
if [ -f ../.env ]; then
    set -a
    . ../.env
    set +a
fi

./jdk-21.0.11+10-jre/bin/java -server \
  -Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+ParallelRefProcEnabled \
  -Dspring.config.location=application.yml -jar BeiDou.jar &
