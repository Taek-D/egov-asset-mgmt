# 시스템 구성

## 구성 요소와 연결

```text
브라우저 ──HTTP 8080──▶ [app 컨테이너: Tomcat 10.1 + ROOT 웹앱]
                          │  (db 컨테이너와 네트워크 공유 → 127.0.0.1:3306)
                          ▼
                        [db 컨테이너: MySQL 8.0, DB 이름 com, 볼륨 db-data]
```

- **app 컨테이너**(`tomcat:10.1-jdk17`, TZ Asia/Seoul): 시작 스크립트가 WAR를 `webapps/ROOT`에 직접 풀고, `.env` 값으로 설정 파일을 만든 뒤(아래 "기동 순서") Tomcat을 띄운다. 외부로 여는 포트는 db 서비스에 걸린 8080 하나다(app이 db의 네트워크 네임스페이스를 쓰기 때문).
- **db 컨테이너**(`mysql:8.0`, TZ Asia/Seoul, 서버 옵션은 `--lower-case-table-names=1`뿐): 볼륨이 비어 있을 때만 `db/init/*.sql`을 파일 이름 순서로 적재한다. `10_` 공통컴포넌트 DDL → `20_` 공통컴포넌트 DML → `30_` 업무 DDL → `40_` 업무 공통코드·권한·메뉴·시드·기본 계정 비활성화 → `50_` 배치 작업 등록. 볼륨이 이미 있으면 다시 적재하지 않는다.
- 외부 시스템 연동은 없다(메일·SSO·외부 API 없음).

## 웹 애플리케이션 내부 계층

공통컴포넌트의 `EgovWebApplicationInitializer`가 두 개의 Spring 컨텍스트를 만든다.

- **루트 컨텍스트**: `classpath*:egovframework/spring/com/**/context-*.xml`. 업무 설정은 `spring/com/asset/`에 있어 자동으로 포함된다(ID 생성기, 업무 트랜잭션 포인트컷, 야간 집계 스케줄러). 컴포넌트 스캔 범위 `egovframework`에 업무 서비스·DAO가 들어온다.
- **디스패처 컨텍스트**: `/WEB-INF/config/egovframework/springmvc/egov-com-*.xml`. `@Controller`를 스캔하고 JSP는 `/WEB-INF/jsp/` + 뷰 이름 + `.jsp`로 찾는다.
- 보안 필터(공통컴포넌트 Spring Security 설정)가 모든 요청 앞에서 로그인과 URL 권한(DB에 등록된 정규식 패턴)을 검사한다.

업무 계층 방향(역방향 호출 없음):

```text
Controller(web) → 서비스 인터페이스(service) → *ServiceImpl(service.impl, 트랜잭션 경계)
                                                  → *DAO(EgovAbstractMapper) → MyBatis 매퍼 XML(mapper/asset/<모듈>) → MySQL
```

## 모듈 지도

| 모듈 | 역할 | 의존(→ 호출 대상) |
|---|---|---|
| `init` | 기동 전 설정 생성 도구(앱 밖에서 실행), 기동 시 데모 암호 1회 적용 | 공통컴포넌트 사용자 테이블 |
| `item` | 품목 등록·수정·사용 중지·삭제 | (없음) |
| `stock` | 가용 계산, 품목 행 잠금, 재고 변동과 재고 이력 | `item`의 VO·예외 |
| `unit` | 개별 자산 등록(입고), 상태 전이, 지급 시 자산 전환 | `stock` |
| `depthead` | 부서장 지정, 부서장 유효성, 승인자 결정 | 공통컴포넌트 조직·사용자·권한 테이블 |
| `req` | 신청·승인·반려·신청취소·지급·지급취소, 조회 범위 | `stock`, `unit`, `depthead` |
| `excel` | 품목·자산 일괄등록, 내려받기 3종 | `item`, `stock`, `unit`, `req` |
| `shortage` | 야간·수동 재고 부족 집계, 배치 결과 기록 | `stock`의 가용 정의, 공통컴포넌트 배치 결과 테이블 |

공통컴포넌트(`egovframework.com`)는 업무 모듈을 호출하지 않는다. 업무 모듈은 공통컴포넌트의 로그인 사용자 조회(`EgovUserDetailsHelper`), 중복 제출 토큰, 공통코드 조회, 엑셀 서비스, 배치 결과 ID 생성기를 쓴다.

## 대표 흐름: 부서장 승인

1. 부서장이 포털 메뉴에서 승인 대기함(`/asset/approve/inbox.do`, POST 이동)을 연다. 목록 SQL은 신청 당시 부서의 현재 유효 부서장·관리자 규칙으로 "지금 이 사용자가 승인자인 대기 신청"만 고른다.
2. 승인 화면에서 품목마다 승인 수량 또는 반려+사유를 골라 `/asset/approve/submit.do`로 한 번에 보낸다(CSRF 토큰 포함).
3. 컨트롤러가 로그인 사용자 아이디를 꺼내 `assetReqService.approve(신청번호, 품목별 결정, 승인자)`를 부른다.
4. 서비스(하나의 트랜잭션): 신청서 행 `FOR UPDATE` → 대기 상태·본인 여부·현재 승인자 여부 재확인 → 신청 품목 행 잠금 → 결정 검증 → 승인 품목의 품목 행을 코드 오름차순으로 `FOR UPDATE`(`assetStockService.lockItems`) → 모든 승인 품목의 가용 확인(하나라도 부족하면 예외, 전체 롤백) → 품목별 `reserve`(재고 차감 또는 확보 증가 + `RESERVE` 이력) → 신청 품목·헤더 갱신 → 처리 이력.
5. 성공하면 대기함으로 돌아가 메시지를 보여 주고, 규칙 위반은 예외 메시지를 화면에 그대로 보여 준다.

## 기동 순서(app 컨테이너)

1. `docker/app-start.sh`: WAR를 `ROOT`에 풀기(매 기동마다 새로).
2. `egovframework.asset.init.ConfigGenerator`를 WAR의 클래스·라이브러리로 실행: `.env`의 `CRYPTO_KEY`로 `egov-crypto-config.properties`의 키와 짝 해시를, `MYSQL_USER`·`MYSQL_PASSWORD`로 `globals.properties`의 DB 계정과 새 키로 암호화한 암호를 `WEB-INF/classes`에 쓴다. 필수 값이 없으면 여기서 컨테이너가 멈춘다.
3. Tomcat 기동 → 루트 컨텍스트가 데이터소스를 만들 때 공통컴포넌트 방식대로 암호를 복호화 → `AssetStartupInitializer`가 데모 암호를 한 번만 적용 → 스케줄러가 매일 02:00 집계 예약.

## 경계: 무엇이 오가고 무엇은 오가지 않는가

- 저장소와 WAR에는 공통컴포넌트의 공개 기본 설정값만 있고, 이 배포의 키·암호는 기동 시 컨테이너 안에서만 만들어진다.
- 업무 화면과 공통컴포넌트 화면은 같은 로그인 세션과 URL 권한 체계를 쓴다. 업무 메뉴는 공통컴포넌트 포털 메인화면(`/sym/mnu/mpm/EgovMainMenuHome.do`)에 역할별로 나온다. 공통컴포넌트의 기본 좌측 메뉴(컴포넌트 전시용)는 업무 메뉴를 담지 않는다.
- 야간 집계는 공통컴포넌트의 Quartz 배치 스케줄러를 쓰지 않고 앱 내부 Spring 스케줄러로 돈다. 공통컴포넌트와는 배치 작업 행 1건과 실행마다의 배치 결과 행만 공유한다.
