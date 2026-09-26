#!/bin/sh
# app 컨테이너 시작: WAR를 직접 풀고 → .env로 설정 파일 생성 → Tomcat 시작
set -e
APP=/usr/local/tomcat/webapps/ROOT
rm -rf "$APP"
mkdir -p "$APP"
cd "$APP"
jar xf /app/app.war
# Crypto 키·짝 해시, DB 계정, 새 키로 암호화한 DB 암호를 WEB-INF/classes에 쓴다. 필수 값이 없으면 여기서 멈춘다
java -cp "$APP/WEB-INF/classes:$APP/WEB-INF/lib/*" egovframework.asset.init.ConfigGenerator "$APP/WEB-INF/classes"
cd /usr/local/tomcat
exec catalina.sh run
