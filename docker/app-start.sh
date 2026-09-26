#!/bin/sh
# app 컨테이너 시작: WAR를 직접 풀고 → .env로 설정 파일 생성 → Tomcat 시작
set -e
APP=/usr/local/tomcat/webapps/ROOT
rm -rf "$APP"
mkdir -p "$APP"
cd "$APP"
jar xf /app/app.war
cd /usr/local/tomcat
exec catalina.sh run
