# 운영

## 준비물

- Docker(Compose 포함). 로컬 JDK·Maven은 필요 없다. 빌드·테스트는 `maven:3.9-eclipse-temurin-17` 컨테이너에서 한다.
- 인터넷: 처음 빌드할 때 Maven Central과 `maven.egovframe.go.kr`에서 의존성을 받는다. Maven 캐시는 Docker 볼륨 `egov-m2`에 쌓이며 없으면 자동으로 생긴다.
- 8080 포트가 비어 있어야 한다.

## 환경 설정(`.env`)

`.env.example`을 복사해 `.env`를 만든다. 모든 값은 필수다. 비밀번호는 셸에서 그대로 쓰일 수 있도록 영문·숫자 위주로 정한다.

| 키 | 쓰임 | 적용 시점 |
|---|---|---|
| `MYSQL_USER`, `MYSQL_PASSWORD` | 앱이 쓰는 DB 계정(DB 이름은 `com`) | DB: 볼륨이 처음 만들어질 때 계정 생성. 앱: 매 기동 때 설정 파일에 기록 |
| `MYSQL_ROOT_PASSWORD` | MySQL 루트 계정. 헬스체크와 검증 스크립트가 쓴다 | DB 볼륨 최초 생성 때 |
| `CRYPTO_KEY` | 공통컴포넌트 Crypto 키. `egovframe` 금지 | 매 앱 기동 |
| `DEMO_PASSWORD` | 시드 계정 9개의 데모 비밀번호 | 처음 앱 기동 때 한 번(이후 무시, 비어 있으면 기동 중단) |

## 빌드와 기동

```bash
bash scripts/build.sh                  # target/egov-asset-mgmt-1.0.0.war (약 20분, 테스트 생략)
docker compose up -d                   # db 초기화(최초) → app: WAR 풀기 → 설정 생성 → Tomcat
docker compose logs -f app             # "Server startup in" 이 보이면 준비 완료
```

- WAR를 만들기 전에 `docker compose up`을 하면 Docker가 WAR 경로에 빈 디렉터리를 만든다. 이때는 그 디렉터리를 지우고 빌드부터 다시 한다.
- 앱 컨테이너는 기동마다 WAR를 새로 풀고 설정을 다시 만든다. 코드를 바꿨으면 `build.sh` 후 `docker compose restart app`.
- 정상 기동 로그: `[설정 생성] ... 만들었습니다`, 첫 기동이면 `데모 암호를 시드 계정 9개에 적용했습니다`, `Server startup in`. "EgovFramework Fatal ERROR"나 `SEVERE`가 있으면 비정상이다.
- 접속: `http://localhost:8080/uat/uia/egovLoginUsr.do` → 업무사용자 탭 → 로그인 후 `http://localhost:8080/sym/mnu/mpm/EgovMainMenuHome.do`.

## 검증

```bash
bash scripts/verify.sh                 # 새 MySQL에서 업무 통합 테스트 전체, 0건이면 실패 (약 10분)
bash scripts/check-rules.sh            # 구조 규칙 1~3, 위반 0건이어야 함
```

- `verify.sh`가 하는 일(순서가 중요하다):
  1. `egov-verify-<폴더 이름>` MySQL 컨테이너를 띄운다. 폴더 이름이 다르면 여러 작업 폴더에서 동시에 돌려도 충돌하지 않는다.
  2. 초기화를 기다린다.
  3. `target/test-classes`를 지우고 테스트를 컴파일한다.
  4. `.env`로 테스트 설정을 `target/test-classes`에 만든다.
  5. `EgovProperties`용 디렉터리를 만든다.
  6. 테스트를 실행하고 실행 건수를 센다.
  7. DB 컨테이너를 지운다.
- 검증 중 DB 컨테이너가 초기화 SQL 오류로 멈추면 스크립트가 곧바로 로그 끝부분을 보여 주고 실패한다.

## 데이터 초기화

- 모두 지우고 처음 상태로: `docker compose down -v` 후 `docker compose up -d`. DB 볼륨이 새로 만들어지며 `db/init`을 다시 적재하고, 데모 암호도 다시 한 번 적용된다.
- `db/init`의 SQL을 고치면 기존 볼륨에는 반영되지 않는다. 볼륨을 지우거나 같은 변경을 수동으로 실행한다.
- 적재 순서: `10_com_ddl` → `20_com_dml` → `30_asset_ddl` → `40_asset_dml`(공통코드·권한·메뉴·공지 게시판·시드·기본 계정 비활성화) → `50_asset_batch`.

## 야간 집계 시각

- 기본 `0 0 2 * * *`(초 분 시 일 월 요일), 시간대 `Asia/Seoul`.
- 바꾸려면 Spring 플레이스홀더 `asset.shortage.cron`, `asset.shortage.zone`을 지정한다.
  - 방법 1: `globals.properties`에 키를 더한다.
  - 방법 2: `CATALINA_OPTS`에 `-Dasset.shortage.cron=...`를 준다.
  - 기본값 외의 값은 아직 실제로 시험하지 않았다.

## 비밀 값 교체

- Crypto 키: `.env`를 바꾸고 `docker compose restart app`. DB 암호는 새 키로 다시 암호화되어 기록된다.
- DB 앱 암호: DB 안의 계정 암호를 먼저 바꾸고(`ALTER USER`), `.env`를 같은 값으로 바꾼 뒤 app을 재기동한다. `.env`만 바꾸면 접속이 실패한다.
- 데모 암호: 적용 후에는 `.env` 변경이 반영되지 않는다. 공통컴포넌트 사용자 관리 화면에서 바꾸거나 볼륨을 새로 만든다.

## 배포

- 이 저장소는 로컬 Docker 실행만 다룬다.
- 다른 서버에 평문 HTTP로 올리면 Secure 세션 쿠키 때문에 로그인이 유지되지 않는다. 외부 공개는 HTTPS 종단(리버스 프록시 등)이 있어야 한다.
