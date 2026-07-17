@echo off
@title BeiDou
chcp 65001

call mvn dependency:resolve 
call mvn package -DskipTests
copy /y target\BeiDou.jar BeiDou.jar

pause

