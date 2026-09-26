# 엔지니어링 노트

## 테스트·설정

**테스트에서 "EgovFramework Fatal ERROR" 후 `Access denied for user ...`**
- 원인: 공통컴포넌트 자체 테스트 소스에 `@Configuration` 클래스 `egovframework.com.crypto.config.EgovCryptoTestConfig`가 있다. 이것이 `target/test-classes`에 컴파일되면 루트 컨텍스트의 `egovframework` 컴포넌트 스캔에 잡혀 `egovCryptoConfig` 빈을 기본 키 설정으로 덮어쓴다.
- 결과: 생성된 설정 파일과 관계없이 기본 키로 DB 암호를 풀어 접속이 실패한다.
- 대응: pom의 `testExcludes`(`egovframework/com/**`)를 유지한다. 의심되면 `target/test-classes/egovframework/com/crypto`가 있는지 본다.

**테스트에서 DB 사용자 이름은 새 값인데 암호만 틀림**
- 원인: 설정 파일을 읽는 경로가 두 가지다.
  - `PropertyPlaceholderConfigurer`(`${Globals.mysql.UserName}` 등)는 클래스패스 순서를 따르므로 `test-classes`가 먼저다.
  - `EgovProperties.getProperty`(DB 암호 해석에 쓰임)는 **자기 클래스 파일이 있는 디렉터리**를 기준으로 `globals.properties`를 찾는다. `test-classes`에 `egovframework/com/cmm/service` 디렉터리가 없으면 `target/classes`의 원본을 읽는다.
- 대응: `verify.sh`가 생성 직후 빈 디렉터리 `target/test-classes/egovframework/com/cmm/service`를 만든다. 테스트 설정을 따로 만드는 절차를 바꿀 때도 이 디렉터리를 유지한다.
- 금지: `target/classes`에 비밀 값을 쓰면 다음 `package` 때 WAR에 들어간다. maven-resources는 더 새 파일을 덮어쓰지 않는다.

**Spring 테스트가 `ClassNotFoundException: jakarta.servlet.ServletConnection` 또는 `HV000183 ... jakarta.el.ExpressionFactory`로 실패**
- 원인: Spring 6의 모의 요청은 Servlet 6.0 API를 요구하고, 공통컴포넌트 검증 어드바이스는 EL 구현을 요구한다. 둘 다 운영에서는 Tomcat 10.1이 준다.
- 대응: pom의 `jakarta.servlet-api` 6.0.0(provided)과 테스트 전용 `org.glassfish.expressly:expressly` 5.0.0을 유지한다.

**MVC 컨트롤러 테스트**
- 테스트 컨텍스트에는 루트 컨텍스트만 있고 디스패처(컨트롤러) 컨텍스트는 없다.
- 컨트롤러 동작(예: 중복 제출 토큰)을 볼 때는 컨트롤러를 직접 만들어 서비스 빈을 넣고 `MockMvcBuilders.standaloneSetup` + `MockHttpSession`으로 호출한다. JSP 렌더링은 테스트에서 확인할 수 없어 실제 기동으로 본다.

## Crypto·설정 생성

- 공통컴포넌트는 `algorithmKeyHash`를 `Base64(SHA-256(algorithmKey))`와 비교한다. 설정 생성 도구는 `EgovPasswordEncoder.encryptPassword(key)`로 같은 값을 만든다.
- DB 암호 저장값은 ARIA 암호화 → Base64 → URL 인코딩 순이다(`EgovEnvCryptoServiceImpl.encrypt`와 같은 방식). 설정 파일에 넣을 때 `%3D` 같은 인코딩이 남아 있어야 정상이다.
- 기동 로그의 "EgovFramework Fatal ERROR"는 키가 `egovframe`일 때만 나온다. 이 문장이 보이면 생성된 crypto 설정이 아닌 다른 설정이 읽힌 것이다.
- `globals.properties`의 `Globals.File.algorithmKey`도 같은 키로 바꾼다(기본값 `egovframe`가 남지 않게).

## MySQL·초기화

**초기화 후 한글이 `ë¹„í’ˆ` 식으로 깨짐(공통컴포넌트 기본 데이터 포함)**
- 원인: MySQL 공식 이미지의 초기화 클라이언트가 SQL 파일을 latin1로 읽는다. UTF-8 바이트가 한 번 더 인코딩되어 저장된다.
- 대응: 모든 초기화 SQL의 첫 문장을 `SET NAMES utf8mb4;`로 한다.
- 안 되는 방법: Windows에서 `my.cnf`를 bind mount하면 "World-writable config file ... is ignored"로 무시된다.
- 확인: `SELECT HEX(MENU_NM) FROM COMTNMENUINFO WHERE MENU_NO = 29100000`이 `EBB984...`로 시작해야 한다.

**로그인 SQL "Illegal mix of collations"**
- 원인: 서버 collation을 `utf8mb4_unicode_ci`로 지정하면 사용자 뷰 비교에서 collation이 섞인다.
- 대응: 서버 옵션을 두지 않는다(MySQL 8 기본 `utf8mb4_0900_ai_ci`). 공식 저장소에는 아직 보고되지 않았다.

**초기화 SQL 오류로 DB 컨테이너가 멈춤**
- 초기화 SQL 하나가 실패하면 엔트리포인트가 종료하고, `mysqladmin ping`은 끝내 성공하지 않는다.
- `verify.sh`는 컨테이너가 멈추면 곧바로 실패하고 로그 끝부분을 보여 준다. 메뉴 번호처럼 기반 DML과 겹치는 키(기반은 메뉴 번호를 9110000까지 씀)가 흔한 원인이다.

- 날짜 컬럼은 DB의 `SYSDATE()`로 채운다. DB 컨테이너 TZ가 앱과 다르면 화면 시각이 어긋난다. 두 컨테이너 모두 `TZ=Asia/Seoul`로 둔다.
- 자산번호·품목명 비교는 기본 collation(대소문자·악센트 무시)을 따른다. 파일 안 중복 검사(소문자 비교)가 놓친 악센트 차이 중복은 저장 단계에서 DB 유일 제약에 걸려 전체가 롤백되고, 한 줄 오류 대신 일반 메시지가 나온다.

## 권한·메뉴

- URL 권한 패턴은 정렬 순서 문자열(`ROLE_SORT`) 순서로 평가하고 **처음 일치한 패턴이 결정한다**.
  - 한때 `sym/mnu/` 전체를 관리자 전용으로 잡아, 모든 사용자의 포털 메인화면까지 막았다. 관리 URL만 좁게(`sym/mnu/(mpm/EgovMenu|mcm/)`) 잡는다.
  - 패턴을 고치면 앱을 재기동해야 반영된다(권한 맵은 기동 때 읽는다).
- 포털 메뉴 조회 SQL은 `COMTNEMPLYRSCRTYESTBS`의 권한 1행만 보고 역할 계층을 따르지 않는다. 자산담당자에게 신청자 메뉴를 보이려면 `ROLE_ASSET`에도 그 메뉴 행을 준다.
- 포털 상단 메뉴는 POST 폼으로 이동한다. 진입 URL을 GET 전용으로 매핑하면 메뉴 클릭 때 405가 난다.
- 로그인 폼의 `userSe`는 기본값이 일반회원(GNR)이다. 업무사용자는 `USR`로 보내야 한다. 자동화 스크립트는 `document.loginForm.userSe.value='USR'`로 넣는다. 탭 전환 함수 `checkLogin`은 화면 요소에 의존해 자동화에서 실패한다.
- 세션 쿠키가 `Secure`라 curl 같은 평문 HTTP 클라이언트는 쿠키를 자동으로 보내지 않는다. 헤더에 직접 넣는다. 브라우저는 `localhost`에서만 허용한다.

## 동시성 구현

- 개별 자산 가용의 보관 수는 품목 잠금 아래에서 `FOR SHARE`로 읽는다.
  - 이유: MySQL 기본 격리수준(REPEATABLE READ)의 일반 SELECT는 트랜잭션 시작 시점의 스냅샷을 보므로, 다른 트랜잭션이 방금 커밋한 자산 변경을 놓친다.
  - 잠금 조회는 최신 커밋 값을 본다.
- 개별 자산 등록·상태 변경은 품목 행을 먼저 잠근 뒤 자산 행을 바꾼다. 순서를 뒤집으면 동시에 도는 승인(품목 잠금 → 보관 수 조회)과 교착한다.
- 품목 유형 변경·삭제 전의 이력 검사도 관련 행을 `FOR SHARE`로 센다. 그래서 같은 품목에 신청 제출·자산 등록이 동시에 오면 잠깐 기다린다.
- 중복 제출 토큰은 공통컴포넌트 `EgovDoubleSubmitHelper`(세션당 토큰 1개, 확인하면 새 토큰으로 교체)다. 신청 컨트롤러는 세션 뮤텍스로 확인 구간을 직렬화하고, 성공하면 리다이렉트한다.

## 배치·스케줄

- 공통컴포넌트 `BatchScheduler`는 기동 때 `COMTNBATCHSCHDUL`의 모든 행을 Quartz 잡으로 등록하고, 작업 행의 프로그램을 셸 명령으로 실행한다. 야간 집계는 이 스케줄러를 쓰지 않는다.
  - `50_asset_batch.sql`은 배치 작업 행 1건(`BAT00000000000000001`, 명령 실행용이 아님)만 넣는다.
  - 결과 행의 스케줄 ID 칸에는 스케줄 행이 없는 고정값 `BSC_ASSET_SHORTAGE`를 쓴다.
  - 기반 결과 목록 SQL은 작업 테이블과 상태 코드(COM076)만 조인하므로 결과가 보인다.
- 기반의 배치 작업 관리 화면에서 이 작업 행을 지우면 결과 목록(내부 조인)에서 집계 결과가 사라진다. 이 작업에 스케줄을 추가하면 셸 실행 실패 로그가 쌓인다.
- 집계 실행 실패는 저장점(savepoint)까지 되돌린 뒤 실패 결과 행만 남긴다. 반쯤 쓴 실행 기록은 남지 않는다.

## 매퍼 조각 재사용

- 부서장 유효성 SQL 조각 `DeptHead.headStatus`와 `DeptHead.loginable`은 신청 매퍼에서도 `<include>`로 쓴다.
- 신청 매퍼에는 `DeptHead.loginable`을 가리키는 별칭 조각 `loginable`이 있다(포함된 조각 안의 참조가 현재 namespace로 풀리기 때문). 부서장 매퍼의 조각 이름을 바꾸면 신청 매퍼 로딩이 실패하고 앱 전체가 뜨지 않는다.

## 이 PC(Windows) 작업 환경

- `bash`가 WSL을 가리킬 수 있다. Docker에 닿지 못하면 Git Bash(`/usr/bin/bash`)로 스크립트를 실행한다.
- Git Bash에서 docker 볼륨 경로에는 `MSYS_NO_PATHCONV=1`과 `$(pwd -W)`가 필요하다(스크립트에 들어 있음).
- 컨테이너의 `/lib`처럼 시스템 경로에 볼륨을 걸면 이미지가 깨진다.
- Windows `find`·`sort`가 GNU 도구보다 앞에 올 수 있다. `check-rules.sh`는 `find` 대신 bash globstar를 쓴다.
- 시간:
  - `verify.sh` 약 10분(MySQL 초기화 1분 + 컴파일·테스트).
  - `build.sh` 약 20분(WAR 조립 시 bind mount 파일 복사가 느림).
  - 반복 개발 때는 이름을 정한 DB 컨테이너를 띄워 두고 `verify.sh` 안의 Maven 명령을 직접 돌린다. 마지막 근거는 `verify.sh` 전체 실행으로 남긴다.
