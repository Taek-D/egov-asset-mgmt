-- 초기화 클라이언트가 파일을 latin1로 읽어 한글이 이중 인코딩되는 것을 막는다(서버 collation 설정 아님)
SET NAMES utf8mb4;

-- 공통컴포넌트 배치 화면(/sym/bat/)은 보기만 남긴다: 작업·결과의 목록·상세는 자산담당자·관리자, 나머지(등록·수정·삭제,
-- 입력 화면, 결과 삭제, 스케줄 목록·상세, 작업 선택 팝업)는 모든 역할 거부.
-- 권한이 연결되지 않은 패턴은 무시되어 기반의 모든 .do 포괄 패턴('3')으로 새므로, 거부 패턴은 아무도 갖지 않는 전용 권한에 연결한다.
-- 이 권한은 역할 계층·사용자·부서 어디에도 주지 않는다.
-- 운영 중인 DB에 손으로 다시 적용해도 오류 없이 같은 결과가 되도록 작성한다(적용 후 앱 재기동 필요: 권한 맵은 기동 때 읽는다).

INSERT INTO COMTNAUTHORINFO (AUTHOR_CODE, AUTHOR_NM, AUTHOR_DC, AUTHOR_CREAT_DE)
SELECT 'ROLE_BATCH_EDIT_LOCKED', '배치 편집 잠금(부여 금지)', '배치 편집 URL 차단용. 어떤 사용자·부서·역할 계층에도 주지 않는다', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM COMTNAUTHORINFO WHERE AUTHOR_CODE = 'ROLE_BATCH_EDIT_LOCKED');

-- 배치 허용 패턴을 작업·결과의 목록·상세 4개로 좁힌다(\.do까지 맞춰 getBatchOpertListPopup은 제외)
UPDATE COMTNROLEINFO
   SET ROLE_NM = '배치 조회',
       ROLE_PTTRN = '\\A/sym/bat/(getBatchOpertList|getBatchOpert|getBatchResultList|getBatchResult)\\.do.*\\Z',
       ROLE_DC = '배치 작업·결과 목록·상세(보기만)'
 WHERE ROLE_CODE = 'asset-000005';

-- 그 밖의 모든 /sym/bat/*.do. 위 허용 패턴과 겹치지 않으므로 같은 정렬 '0'이어도 순서와 무관하다
INSERT INTO COMTNROLEINFO (ROLE_CODE, ROLE_NM, ROLE_PTTRN, ROLE_DC, ROLE_TY, ROLE_SORT, ROLE_CREAT_DE)
SELECT 'asset-000010', '배치 편집 잠금', '\\A/sym/bat/(?!(getBatchOpertList|getBatchOpert|getBatchResultList|getBatchResult)\\.do).*\\.do.*\\Z',
       '배치 작업·스케줄·결과 편집, 스케줄 조회, 작업 선택 팝업은 모든 역할 거부', 'url', '0', SYSDATE() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM COMTNROLEINFO WHERE ROLE_CODE = 'asset-000010');

INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT)
SELECT 'ROLE_BATCH_EDIT_LOCKED', 'asset-000010', SYSDATE() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM COMTNAUTHORROLERELATE WHERE AUTHOR_CODE = 'ROLE_BATCH_EDIT_LOCKED' AND ROLE_CODE = 'asset-000010');
