@echo off
@title BeiDou
chcp 65001

.\jdk-21.0.11+10-jre\bin\java.exe -server -Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled -Dspring.config.location=application.yml -jar BeiDou.jar
pause
