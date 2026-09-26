#!/usr/bin/env bash
# 구조 규칙 점검(spec 2절 1~3). egovframework.asset 업무 코드만 본다. 위반이 있으면 목록을 출력하고 종료 코드 1.
set -uo pipefail
shopt -s globstar nullglob
cd "$(dirname "$0")/.."
SRC=src/main/java/egovframework/asset
V=0
fail() { echo "  위반: $1"; V=$((V + 1)); }

echo "[규칙 1] 서비스 구현은 EgovAbstractServiceImpl 상속 + 서비스 인터페이스 구현"
n=0
for f in "$SRC"/**/*ServiceImpl.java; do
  n=$((n + 1))
  grep -q 'extends EgovAbstractServiceImpl' "$f" || fail "$f: EgovAbstractServiceImpl 미상속"
  grep -Eq 'implements [A-Za-z]+Service\b' "$f" || fail "$f: 서비스 인터페이스 미구현"
done
echo "  대상 ${n}개"; [ "$n" -gt 0 ] || fail "점검 대상 파일을 찾지 못함"

echo "[규칙 2] DAO는 EgovAbstractMapper 상속, SQL은 MyBatis 매퍼 XML에만"
n=0
for f in "$SRC"/**/*DAO.java; do
  n=$((n + 1))
  grep -q 'extends EgovAbstractMapper' "$f" || fail "$f: EgovAbstractMapper 미상속"
done
echo "  대상 ${n}개"; [ "$n" -gt 0 ] || fail "점검 대상 파일을 찾지 못함"
for f in $(grep -rlE '"\s*(SELECT|INSERT INTO|UPDATE [A-Z_]+ SET|DELETE FROM)\b' "$SRC" 2>/dev/null); do
  fail "$f: 자바 코드 안의 SQL 문자열"
done
for f in $(grep -rlE 'JdbcTemplate|java\.sql\.(Statement|PreparedStatement)' "$SRC" 2>/dev/null); do
  fail "$f: JDBC 직접 사용"
done
M=(src/main/resources/egovframework/mapper/asset/**/*_mysql.xml)
echo "  매퍼 XML ${#M[@]}개"

echo "[규칙 3] 컨트롤러에는 서비스 인터페이스만 주입(DAO·구현 클래스 참조 금지)"
n=0
for f in $(grep -rl '@Controller' "$SRC"); do
  n=$((n + 1))
  grep -Eq '^import .*\.(service\.impl\.|[A-Za-z]*DAO;)' "$f" && fail "$f: DAO/구현 클래스 import"
  grep -Eq '\b[A-Za-z]*(DAO|ServiceImpl)\s+[a-z][A-Za-z]*\s*;' "$f" && fail "$f: DAO/구현 클래스 필드"
done
echo "  대상 ${n}개"; [ "$n" -gt 0 ] || fail "점검 대상 파일을 찾지 못함"

echo "[참고] 규칙 4 사용처: PaginationInfo $(grep -rl 'PaginationInfo' "$SRC" | wc -l)개 파일, EgovIdGnrService $(grep -rl 'EgovIdGnrService' "$SRC" | wc -l)개 파일"
echo "위반 합계: ${V}건"
[ "$V" -eq 0 ]
