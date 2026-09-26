# 규칙

어기면 빌드·테스트 실패, 런타임 오류, 구조 불일치 가운데 하나가 생기는 규칙만 적는다.

## 검증 게이트(병합·배포 전)

- `bash scripts/verify.sh`가 종료 코드 0이고 "실행된 업무 테스트: N건"에서 N > 0이어야 한다. 0건 실행은 실패다.
  - 이 스크립트는 새 MySQL 8.0 컨테이너(서버 collation 옵션 없음, TZ Asia/Seoul)에 `db/init` 전부를 적재한다.
  - 이어서 `.env`로 테스트 설정을 만들고 `egovframework/asset/**/*Test`를 실행한다.
- `bash scripts/check-rules.sh`가 위반 0건이어야 한다.
- 메모리 DB로 바꾸지 않는다. 동시성 테스트가 실제 MySQL 잠금에서만 의미가 있다.
- 공통컴포넌트 자체 테스트(`src/test/java/egovframework/com/**`)는 컴파일에서 제외된 상태를 유지한다. 포함하면 그 안의 `@Configuration` 테스트 설정이 업무 테스트 컨텍스트의 Crypto 설정을 덮어써 DB 접속이 실패한다.

## 구조 규칙(업무 패키지 `egovframework.asset`)

1. 서비스 구현 클래스는 `*ServiceImpl`로 이름 짓고 `org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl`을 상속하며 서비스 인터페이스를 구현한다. 위치는 `<모듈>.service.impl`, 인터페이스와 VO는 `<모듈>.service`에 둔다.
2. DAO는 `org.egovframe.rte.psl.dataaccess.EgovAbstractMapper`를 **직접** 상속하고 `@Resource(name = "egov.sqlSession")`로 `setSqlSessionFactory`를 재정의한다. 중간 추상 클래스를 두면 점검 스크립트가 잡는다.
   - SQL은 `src/main/resources/egovframework/mapper/asset/<모듈>/<이름>_SQL_mysql.xml`(모듈별 고유 namespace)에만 둔다.
   - 운영 코드에서 JdbcTemplate·JDBC Statement를 쓰지 않는다(테스트의 준비·검증용 JdbcTemplate은 허용).
3. 컨트롤러(`<모듈>.web`)는 서비스 인터페이스만 `@Resource`로 주입한다. DAO, `*ServiceImpl`, `service.impl` 패키지를 import하지 않는다.
4. 목록 페이징은 `PaginationInfo`(JSP는 `<ui:pagination>`), 업무 키는 `context-asset-idgn.xml`의 `EgovIdGnrService` 빈으로 만든다. 새 키 종류에는 빈과 `COMTECOPSEQ` 행을 함께 추가한다. 시드는 100 미만 번호를 쓰고 시퀀스는 101부터 시작한다.
5. pom에서 실행환경 jar와 Spring 버전을 재정의하지 않는다. parent BOM이 정한다.

## 트랜잭션과 잠금

- `egovframework.asset..*Impl` 클래스의 모든 public 메서드는 REQUIRED 트랜잭션이며 어떤 예외에도 롤백한다(`context-asset-transaction.xml`).
  - 기반의 트랜잭션 포인트컷은 `egovframework.com`만 대상이라, 이름이 `*Impl`이 아닌 업무 클래스에는 트랜잭션이 없다.
  - DAO·컨트롤러·잡 클래스는 `*Impl`로 이름 짓지 않는다.
- 재고·확보·자산 상태·신청 상태를 바꾸는 서비스 메서드는 다음 순서를 지킨다.
  1. 신청서 행을 `FOR UPDATE`로 잠근다(있을 때).
  2. `assetStockService.lockItems(관련 품목 전부)`로 품목 행을 코드 오름차순으로 잠근다.
  3. 상태와 가용을 다시 읽어 확인한다.
  4. 바꾸고 이력을 남긴다.
  - 품목 잠금 전에 `ASSET_UNIT` 행을 바꾸지 않는다.
- 업무 규칙 위반은 `egovframework.asset.item.service.AssetItemException`(메시지는 화면에 그대로 보일 한국어 문장)으로 던진다. 다른 서비스가 던진 이 예외는 바깥 트랜잭션을 롤백 전용으로 만든다. 잡아서 계속 진행하면 커밋 때 `UnexpectedRollbackException`이 난다. 그대로 전파한다.
- 서비스 메서드는 처리자 아이디를 인자로 받는다. 서비스 안에서 보안 컨텍스트를 읽지 않는다. 컨트롤러가 `EgovUserDetailsHelper.getAuthenticatedUser()`로 꺼내 넘긴다.

## 화면

- JSP는 `WEB-INF/jsp/egovframework/asset/<모듈>/`에 두고, 공통컴포넌트 화면 틀·CSS(`/css/egovframework/com/com.css`)를 따른다.
- 공통컴포넌트 공용 레이아웃 JSP와 메뉴 JSP는 고치지 않는다. 메뉴는 DB로 등록한다.
- 포털 메뉴가 연결하는 진입 URL은 GET과 POST를 모두 받는다. 포털 상단 메뉴가 POST 폼으로 이동하기 때문이다.
- 모든 POST 폼에 CSRF 토큰을 넣는다.
- 신청·승인·지급 화면은 KWCAG 2.2 기본 항목을 지킨다: 모든 입력에 `label`/`title`, 이미지에 대체텍스트, 마우스 전용 조작 없음.

## URL·권한·메뉴 추가

- 새 업무 URL은 기존 영역 접두사(`/asset/req`, `/asset/approve`, `/asset/item`, `/asset/stock`, `/asset/unit`, `/asset/deliver`, `/asset/excel`, `/asset/shortage`, `/asset/depthead`, `/asset/admin`) 아래에 둔다. 새 접두사를 만들면 `COMTNROLEINFO` 패턴과 권한 연결 행을 같이 추가한다. 그러지 않으면 포괄 패턴에 걸려 모든 로그인 사용자에게 열린다.
- 패턴은 SQL 문자열 안에서 역슬래시를 두 번 쓴다(`'\\A/asset/.*\\.do.*\\Z'`). 정렬 순서는 `'0'`으로 둔다.
- 새 메뉴는 역할마다 `COMTNMENUCREATDTLS` 행을 따로 준다(계층 상속 없음). 메뉴 번호는 기반이 쓰지 않는 `29xxxxxx` 대역을 쓴다.

## DB 초기화 파일

- `db/init`의 SQL은 파일 이름 순서로 한 번만 적재된다. 새 파일은 `50_` 뒤 번호로 추가하고, 이미 배포된 파일을 고쳐도 기존 DB 볼륨에는 반영되지 않는다.
- 모든 초기화 SQL 파일의 첫 문장은 `SET NAMES utf8mb4;`다. 없으면 한글이 이중 인코딩되어 들어간다.
- 공통컴포넌트 배치 스케줄 테이블(`COMTNBATCHSCHDUL`)에 행을 넣지 않는다. 기반 스케줄러가 모든 스케줄 행을 셸 명령으로 실행한다.
- MySQL 서버에 collation·character-set 옵션을 주지 않는다(`utf8mb4_unicode_ci`를 서버 기본으로 두면 공통컴포넌트 로그인 SQL이 실패한다).

## 테스트 작성

- JUnit 5, `src/test/java/egovframework/asset/<모듈>/*Test.java`, DB가 필요하면 `AssetIntegrationSupport`를 상속한다.
- 테스트 클래스·메서드에 `@Transactional`을 붙이지 않는다. 동시성 테스트는 스레드마다 서비스를 불러 각자 트랜잭션을 쓰고, `CountDownLatch`로 동시에 시작한다.
- 테스트는 고유한 이름·아이디(`System.nanoTime()` 접미사 등)로 자기 데이터를 만든다. 한 번의 실행 안에서 모든 테스트 클래스가 같은 DB를 쓰므로 테이블 전체 건수를 단정하지 않는다. 시드 상태(계정 잠금 등)를 바꿨으면 `finally`에서 되돌린다.
- 재고·상태를 바꾸는 규칙에는 거부 경로와 "아무것도 바뀌지 않음" 단정을 함께 둔다.

## 기반 소스 수정

- 공통컴포넌트(`egovframework.com`, 기반 설정, pom)를 고칠 때는 최소한으로 하고, 커밋 메시지에 "기반 소스 수정 사유"를 적는다.
- 현재까지의 수정: 매퍼 경로 추가, 공통컴포넌트 테스트 컴파일 제외, servlet-api 6.0.0, 테스트 전용 EL 구현, 초기화 SQL 사본의 `SET NAMES`, surefire `skipTests` 속성화, artifactId.

## 비밀 값과 커밋

- `.env`는 커밋하지 않는다. 비밀 값이 들어간 설정 파일을 `src/`나 `target/classes`에 쓰지 않는다. 테스트 설정은 `target/test-classes`에만 생성한다.
- 커밋 메시지는 한국어로 무엇을 왜 바꿨는지 적는다.
