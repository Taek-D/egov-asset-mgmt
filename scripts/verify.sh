#!/usr/bin/env bash
# 업무(egovframework.asset) 통합 테스트를 실제 MySQL 8.0 컨테이너에서 실행한다.
# 테스트가 0건 실행되면 실패로 본다.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT_HOST=$( (pwd -W 2>/dev/null) || pwd )
export MSYS_NO_PATHCONV=1
[ -f .env ] || { echo ".env가 없습니다. .env.example을 복사해 값을 채우세요." >&2; exit 2; }
set -a; . ./.env; set +a

DB="egov-verify-$(basename "$PWD")"
docker rm -f "$DB" >/dev/null 2>&1 || true
trap 'docker rm -f "$DB" >/dev/null 2>&1 || true' EXIT

# 서버 collation 옵션 없음(운영 구성과 동일)
docker run -d --name "$DB" -e MYSQL_DATABASE=com -e MYSQL_USER -e MYSQL_PASSWORD -e MYSQL_ROOT_PASSWORD \
  -v "$ROOT_HOST/db/init:/docker-entrypoint-initdb.d:ro" mysql:8.0 --lower-case-table-names=1 >/dev/null

echo "MySQL 초기화 대기..."
for _ in $(seq 1 150); do
  if docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$DB" mysqladmin ping -h 127.0.0.1 -uroot --silent >/dev/null 2>&1; then
    READY=1; break
  fi
  # 초기화 SQL 오류로 컨테이너가 멈췄으면 바로 실패
  [ "$(docker inspect -f '{{.State.Running}}' "$DB" 2>/dev/null)" = true ] || break
  sleep 2
done
[ "${READY:-}" = 1 ] || { docker logs "$DB" | tail -30; echo "MySQL 기동 실패" >&2; exit 1; }

rm -rf target/surefire-reports target/test-classes
docker run --rm --network "container:$DB" -v "$ROOT_HOST:/src" -v egov-m2:/root/.m2 -w /src --env-file .env \
  maven:3.9-eclipse-temurin-17 bash -c '
set -e
mvn -B -q -Dmaven.javadoc.skip=true test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
# 테스트용 설정(.env의 DB 계정·Crypto 키)을 target/test-classes에 생성 — 앱 기동과 같은 생성기
if [ -f target/classes/egovframework/asset/init/ConfigGenerator.class ]; then
  java -cp "target/classes:$(cat target/cp.txt)" egovframework.asset.init.ConfigGenerator target/test-classes
  # EgovProperties는 자기 클래스(egovframework/com/cmm/service) 위치를 기준으로 globals.properties를 찾는다.
  # 같은 패키지 디렉터리를 test-classes에 두어 테스트가 생성된 설정을 읽게 한다(target/classes에는 비밀 값을 쓰지 않는다)
  mkdir -p target/test-classes/egovframework/com/cmm/service
fi
mvn -B -Dmaven.javadoc.skip=true -DskipTests=false "-Dtest=egovframework/asset/**/*Test" \
  -Dsurefire.failIfNoSpecifiedTests=false surefire:test
'

TOTAL=$(cat target/surefire-reports/TEST-*.xml 2>/dev/null | grep -o '<testsuite [^>]*' | grep -o ' tests="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}' || true)
echo "실행된 업무 테스트: ${TOTAL}건"
[ "$TOTAL" -gt 0 ] || { echo "실행된 테스트가 0건입니다. 실패로 처리합니다." >&2; exit 1; }
