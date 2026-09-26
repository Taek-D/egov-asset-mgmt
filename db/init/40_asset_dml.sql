-- 초기화 클라이언트가 파일을 latin1로 읽어 한글이 이중 인코딩되는 것을 막는다(서버 collation 설정 아님)
SET NAMES utf8mb4;

-- 기관 비품·자산 관리: 공통코드, 권한·URL·메뉴, 공지 게시판, 시드 데이터, 기본 계정 비활성화
-- 암호 값은 여기 두지 않는다. 시드 계정 비밀번호는 앱이 처음 기동할 때 .env의 DEMO_PASSWORD로 한 번 설정한다.

-- ========== 1. 공통코드 ==========
INSERT INTO COMTCCMMNCLCODE (CL_CODE, CL_CODE_NM, CL_CODE_DC, USE_AT, FRST_REGIST_PNTTM, FRST_REGISTER_ID, LAST_UPDT_PNTTM, LAST_UPDUSR_ID)
VALUES ('AST', '비품·자산관리', '비품·자산관리 업무 코드', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM');

INSERT INTO COMTCCMMNCODE (CL_CODE, CODE_ID, CODE_ID_NM, CODE_ID_DC, USE_AT, FRST_REGIST_PNTTM, FRST_REGISTER_ID, LAST_UPDT_PNTTM, LAST_UPDUSR_ID) VALUES
('AST', 'AST001', '자산분류', '품목 분류', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST', 'AST002', '단위', '품목 단위', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST', 'AST003', '품목유형', '수량 비품 / 개별 자산', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST', 'AST004', '자산상태', '개별 자산 상태', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM');

INSERT INTO COMTCCMMNDETAILCODE (CODE_ID, CODE, CODE_NM, CODE_DC, USE_AT, FRST_REGIST_PNTTM, FRST_REGISTER_ID, LAST_UPDT_PNTTM, LAST_UPDUSR_ID) VALUES
('AST001', 'OFFICE', '사무용품', '사무용품', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST001', 'SUPPLY', '소모품', '소모품', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST001', 'IT', '정보기기', '정보기기', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST001', 'FURNITURE', '가구', '가구', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST002', 'EA', '개', '개', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST002', 'BOX', '상자', '상자', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST002', 'PACK', '팩', '팩', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST002', 'SET', '대', '대', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST003', 'QTY', '수량 비품', '개수로 세는 비품', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST003', 'UNIT', '개별 자산', '자산번호로 관리하는 자산', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST004', 'STORED', '보관', '보관', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST004', 'IN_USE', '사용', '사용', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST004', 'REPAIR', '수리', '수리', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM'),
('AST004', 'DISPOSED', '폐기', '폐기', 'Y', SYSDATE(), 'SYSTEM', SYSDATE(), 'SYSTEM');

-- ========== 2. ID 생성기 시퀀스 (시드는 100 미만 번호를 쓴다) ==========
INSERT INTO COMTECOPSEQ (TABLE_NAME, NEXT_ID) VALUES
('ASSET_ITEM_ID', 101),
('ASSET_STOCK_HIST_ID', 101),
('ASSET_UNIT_HIST_ID', 101),
('ASSET_REQ_ID', 101),
('ASSET_REQ_HIST_ID', 101),
('ASSET_SHORT_RUN_ID', 101);

-- ========== 3. 권한: 자산담당자 역할 ==========
-- ROLE_USER의 하위로 두어 신청자 권한을 물려받는다. ROLE_ADMIN 아래에 두지 않는다(관리자는 입고·지급을 하지 않는다)
INSERT INTO COMTNAUTHORINFO (AUTHOR_CODE, AUTHOR_NM, AUTHOR_DC, AUTHOR_CREAT_DE) VALUES ('ROLE_ASSET', '자산담당자', '입고·지급·재고·자산 관리', NOW());
INSERT INTO COMTNROLES_HIERARCHY (PARNTS_ROLE, CHLDRN_ROLE) VALUES ('ROLE_USER', 'ROLE_ASSET');

-- URL 패턴. 정렬 순서 '0'으로 기반의 모든 .do 포괄 패턴('3')과 회원관리('1')보다 먼저 평가된다
INSERT INTO COMTNROLEINFO (ROLE_CODE, ROLE_NM, ROLE_PTTRN, ROLE_DC, ROLE_TY, ROLE_SORT, ROLE_CREAT_DE) VALUES
('asset-000001', '비품 신청', '\\A/asset/req/.*\\.do.*\\Z', '신청 작성·내 신청·신청 취소', 'url', '0', SYSDATE()),
('asset-000002', '비품 승인', '\\A/asset/approve/.*\\.do.*\\Z', '승인 대기함·승인·부서 신청 조회(승인자 여부는 서비스에서 판정)', 'url', '0', SYSDATE()),
('asset-000003', '재고·자산 관리', '\\A/asset/(item|stock|unit|deliver|excel|shortage)/.*\\.do.*\\Z', '자산담당자 전용', 'url', '0', SYSDATE()),
('asset-000004', '비품 관리자', '\\A/asset/(depthead|admin)/.*\\.do.*\\Z', '부서장 지정·전체 신청 조회', 'url', '0', SYSDATE()),
('asset-000005', '배치 관리', '\\A/sym/bat/.*\\.do.*\\Z', '배치 작업·결과 화면', 'url', '0', SYSDATE()),
('asset-000006', '게시글 쓰기', '\\A/cop/bbs/(insertArticle|replyArticle|updateArticle|deleteArticle)(View)?\\.do.*\\Z', '공지 게시판 쓰기는 관리자·자산담당자', 'url', '0', SYSDATE()),
('asset-000007', '게시판 관리', '\\A/cop/bbs/.*BBSMaster.*\\.do.*\\Z', '게시판 생성·속성 관리', 'url', '0', SYSDATE()),
('asset-000008', '사용자·권한 관리', '\\A/(sec/ram/EgovAuthor|sec/rgm/|sec/gmt/|sym/mnu/(mpm/EgovMenu|mcm/)|uss/umt/dpt/|uss/umt/Egov(Emplyr|Mber|Entrprs)(Manage|Insert|Delete|SelectUpdt|LockIncorrect)).*\\.do.*\\Z', '공통컴포넌트 사용자·권한·메뉴·부서 관리', 'url', '0', SYSDATE()),
-- 공통컴포넌트 관리 영역(보안·시스템·로그인정책·통계·연계·시스템 감시)은 관리자만. 기반은 로그인 사용자에게 모든 .do를 열어 두므로
-- 막지 않으면 신청자가 부서권한관리(/sec/drm) 등으로 스스로 권한을 올릴 수 있다. 포털 메인 메뉴·달력·우편번호·배치(위 '0' 패턴)는 제외.
-- 정렬 순서 '05'는 '0' 패턴들보다 뒤, 기반 패턴('1', '3')보다 앞이다.
('asset-000009', '공통 관리 영역', '\\A/(sec/(?!rnc/|ram/accessDenied)|sym/(?!mnu/mpm/EgovMainMenu|cal/|ccm/zip/|bat/)|uat/uap/|sts/|ssi/|utl/sys/).*\\.do.*\\Z', '공통컴포넌트 관리 화면', 'url', '05', SYSDATE());

INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT) VALUES
('ROLE_USER', 'asset-000001', SYSDATE()),
('ROLE_ASSET', 'asset-000001', SYSDATE()),
('ROLE_ADMIN', 'asset-000001', SYSDATE()),
('ROLE_USER', 'asset-000002', SYSDATE()),
('ROLE_ASSET', 'asset-000002', SYSDATE()),
('ROLE_ADMIN', 'asset-000002', SYSDATE()),
('ROLE_ASSET', 'asset-000003', SYSDATE()),
('ROLE_ADMIN', 'asset-000004', SYSDATE()),
('ROLE_ASSET', 'asset-000005', SYSDATE()),
('ROLE_ADMIN', 'asset-000005', SYSDATE()),
('ROLE_ASSET', 'asset-000006', SYSDATE()),
('ROLE_ADMIN', 'asset-000006', SYSDATE()),
('ROLE_ADMIN', 'asset-000007', SYSDATE()),
('ROLE_ADMIN', 'asset-000008', SYSDATE()),
('ROLE_ADMIN', 'asset-000009', SYSDATE()),
-- 기반 포괄 패턴(모든 .do)도 명시적으로 준다(기반이 ROLE_ADMIN에 준 방식과 같게)
('ROLE_ASSET', 'web-000003', SYSDATE());

-- ========== 4. 공지 게시판 ==========
INSERT INTO COMTNBBSMASTER (BBS_ID, BBS_NM, BBS_INTRCN, BBS_TY_CODE, REPLY_POSBL_AT, FILE_ATCH_POSBL_AT, ATCH_POSBL_FILE_NUMBER, USE_AT, TMPLAT_ID, FRST_REGISTER_ID, FRST_REGIST_PNTTM)
VALUES ('BBSMSTR_000000000001', '공지사항', '비품·자산 관련 공지', 'BBST01', 'N', 'Y', 3, 'Y', 'TMPLAT_BOARD_DEFAULT', 'USRCNFRM_00000000003', SYSDATE());
UPDATE COMTECOPSEQ SET NEXT_ID = 2 WHERE TABLE_NAME = 'BBS_ID';

-- ========== 5. 메뉴 (공통컴포넌트 포털 메인화면 /sym/mnu/mpm/EgovMainMenuHome.do에 역할별로 나온다) ==========
INSERT INTO COMTNPROGRMLIST (PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, PROGRM_DC, URL) VALUES
('assetReqForm', '/asset/req/', '신청서 작성', '비품 신청서 작성', '/asset/req/form.do'),
('assetReqMyList', '/asset/req/', '내 신청', '내 신청 조회·취소', '/asset/req/myList.do'),
('assetApproveInbox', '/asset/approve/', '승인 대기함', '승인할 신청 목록', '/asset/approve/inbox.do'),
('assetApproveDeptList', '/asset/approve/', '부서 신청 조회', '부서장의 부서 신청 조회', '/asset/approve/deptList.do'),
('assetNotice', '/cop/bbs/', '공지사항', '공지 게시판', '/cop/bbs/selectArticleList.do?bbsId=BBSMSTR_000000000001'),
('assetItemList', '/asset/item/', '품목 관리', '품목·입고·조정', '/asset/item/list.do'),
('assetStockHistory', '/asset/stock/', '재고 이력', '재고 변동 이력', '/asset/stock/history.do'),
('assetUnitList', '/asset/unit/', '개별 자산', '개별 자산 등록·상태 관리', '/asset/unit/list.do'),
('assetDeliverList', '/asset/deliver/', '지급 관리', '지급·지급취소·전체 신청', '/asset/deliver/list.do'),
('assetExcelMain', '/asset/excel/', '엑셀', '일괄등록·내려받기', '/asset/excel/main.do'),
('assetShortageResult', '/asset/shortage/', '재고 부족 집계', '야간 재고 부족 집계 결과', '/asset/shortage/result.do'),
('assetDeptHeadList', '/asset/depthead/', '부서장 지정', '부서장 지정·해제', '/asset/depthead/list.do'),
('assetAdminReqList', '/asset/admin/', '전체 신청 조회', '관리자 전체 신청 조회', '/asset/admin/reqList.do');

INSERT INTO COMTNMENUINFO (MENU_NM, PROGRM_FILE_NM, MENU_NO, UPPER_MENU_NO, MENU_ORDR, MENU_DC, RELATE_IMAGE_PATH, RELATE_IMAGE_NM) VALUES
('비품 신청', 'dir', 29100000, 0, 1, '비품 신청', '/', '/'),
('재고·자산 관리', 'dir', 29200000, 0, 2, '재고·자산 관리(자산담당자)', '/', '/'),
('관리', 'dir', 29300000, 0, 3, '관리(관리자)', '/', '/');
INSERT INTO COMTNMENUINFO (MENU_NM, PROGRM_FILE_NM, MENU_NO, UPPER_MENU_NO, MENU_ORDR, MENU_DC, RELATE_IMAGE_PATH, RELATE_IMAGE_NM) VALUES
('신청서 작성', 'assetReqForm', 29110000, 29100000, 1, '신청서 작성', '/', '/'),
('내 신청', 'assetReqMyList', 29120000, 29100000, 2, '내 신청', '/', '/'),
('승인 대기함', 'assetApproveInbox', 29130000, 29100000, 3, '승인 대기함', '/', '/'),
('부서 신청 조회', 'assetApproveDeptList', 29140000, 29100000, 4, '부서 신청 조회', '/', '/'),
('공지사항', 'assetNotice', 29150000, 29100000, 5, '공지사항', '/', '/'),
('품목 관리', 'assetItemList', 29210000, 29200000, 1, '품목 관리', '/', '/'),
('재고 이력', 'assetStockHistory', 29220000, 29200000, 2, '재고 이력', '/', '/'),
('개별 자산', 'assetUnitList', 29230000, 29200000, 3, '개별 자산', '/', '/'),
('지급 관리', 'assetDeliverList', 29240000, 29200000, 4, '지급 관리', '/', '/'),
('엑셀', 'assetExcelMain', 29250000, 29200000, 5, '엑셀', '/', '/'),
('재고 부족 집계', 'assetShortageResult', 29260000, 29200000, 6, '재고 부족 집계', '/', '/'),
('배치 결과', 'getBatchResultList', 29270000, 29200000, 7, '배치 결과', '/', '/'),
('부서장 지정', 'assetDeptHeadList', 29310000, 29300000, 1, '부서장 지정', '/', '/'),
('전체 신청 조회', 'assetAdminReqList', 29320000, 29300000, 2, '전체 신청 조회', '/', '/'),
('업무사용자 관리', 'EgovUserManage', 29330000, 29300000, 3, '업무사용자 관리', '/', '/'),
('부서 관리', 'selectDeptManageListView', 29340000, 29300000, 4, '부서 관리', '/', '/'),
('권한 관리', 'EgovAuthorList', 29350000, 29300000, 5, '권한 관리', '/', '/'),
('배치 결과', 'getBatchResultList', 29360000, 29300000, 6, '배치 결과', '/', '/');

-- 메뉴 조회 SQL은 사용자 권한 행 하나만 보고 역할 계층을 따르지 않으므로 역할마다 메뉴를 준다
INSERT INTO COMTNMENUCREATDTLS (MENU_NO, AUTHOR_CODE)
SELECT m.MENU_NO, a.AUTHOR_CODE
  FROM COMTNMENUINFO m
  JOIN (SELECT 'ROLE_USER' AUTHOR_CODE UNION ALL SELECT 'ROLE_ASSET' UNION ALL SELECT 'ROLE_ADMIN') a
 WHERE m.MENU_NO = 29100000 OR m.UPPER_MENU_NO = 29100000;
INSERT INTO COMTNMENUCREATDTLS (MENU_NO, AUTHOR_CODE)
SELECT MENU_NO, 'ROLE_ASSET' FROM COMTNMENUINFO WHERE MENU_NO = 29200000 OR UPPER_MENU_NO = 29200000;
INSERT INTO COMTNMENUCREATDTLS (MENU_NO, AUTHOR_CODE)
SELECT MENU_NO, 'ROLE_ADMIN' FROM COMTNMENUINFO WHERE MENU_NO = 29300000 OR UPPER_MENU_NO = 29300000;

-- ========== 6. 공통컴포넌트 기본 계정 4개 비활성화 ==========
-- 비활성 = 잠금 표시(LOCK_AT='Y') + 권한 행 제거. 공개된 기본 비밀번호 해시도 쓸 수 없는 값으로 바꾼다
UPDATE COMTNEMPLYRINFO SET LOCK_AT = 'Y', PASSWORD = '!disabled' WHERE EMPLYR_ID IN ('TEST1', 'webmaster');
UPDATE COMTNGNRLMBER SET LOCK_AT = 'Y', PASSWORD = '!disabled' WHERE MBER_ID = 'USER';
UPDATE COMTNENTRPRSMBER SET LOCK_AT = 'Y', ENTRPRS_MBER_PASSWORD = '!disabled' WHERE ENTRPRS_MBER_ID = 'ENTERPRISE';
DELETE FROM COMTNEMPLYRSCRTYESTBS
 WHERE SCRTY_DTRMN_TRGET_ID IN ('USRCNFRM_00000000000', 'USRCNFRM_00000000001', 'USRCNFRM_00000000002', 'USRCNFRM_99999999999');

-- ========== 7. 시드: 부서 ==========
INSERT INTO COMTNORGNZTINFO (ORGNZT_ID, ORGNZT_NM, ORGNZT_DC) VALUES
('ORGNZT_0000000000001', '총무과', '부서장 있음'),
('ORGNZT_0000000000002', '정보화팀', '부서장 있음'),
('ORGNZT_0000000000003', '민원실', '부서장 없음(관리자가 대신 승인)');
UPDATE COMTECOPSEQ SET NEXT_ID = 4 WHERE TABLE_NAME = 'ORGNZT_ID';

-- ========== 8. 시드: 업무 사용자 (비밀번호는 기동 시 데모 암호로 설정되는 자리표시 값) ==========
INSERT INTO COMTNEMPLYRINFO (EMPLYR_ID, ORGNZT_ID, USER_NM, PASSWORD, EMPL_NO, SEXDSTN_CODE, HOUSE_ADRES, PASSWORD_HINT, PASSWORD_CNSR,
       HOUSE_END_TELNO, AREA_NO, HOUSE_MIDDLE_TELNO, ZIP, EMAIL_ADRES, OFCPS_NM, GROUP_ID, EMPLYR_STTUS_CODE, ESNTL_ID, SBSCRB_DE, CHG_PWD_LAST_PNTTM) VALUES
('admin1', 'ORGNZT_0000000000001', '김관리', '!demo-pending', 'D0001', 'F', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'admin1@example.com', '관리자', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000003', SYSDATE(), SYSDATE()),
('admin2', 'ORGNZT_0000000000002', '이관리', '!demo-pending', 'D0002', 'M', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'admin2@example.com', '관리자', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000004', SYSDATE(), SYSDATE()),
('keeper', 'ORGNZT_0000000000001', '박담당', '!demo-pending', 'D0003', 'M', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'keeper@example.com', '자산담당', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000005', SYSDATE(), SYSDATE()),
('head1', 'ORGNZT_0000000000001', '최과장', '!demo-pending', 'D0004', 'F', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'head1@example.com', '과장', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000006', SYSDATE(), SYSDATE()),
('head2', 'ORGNZT_0000000000002', '정팀장', '!demo-pending', 'D0005', 'M', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'head2@example.com', '팀장', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000007', SYSDATE(), SYSDATE()),
('user1', 'ORGNZT_0000000000001', '한신청', '!demo-pending', 'D0006', 'F', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'user1@example.com', '주무관', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000008', SYSDATE(), SYSDATE()),
('user2', 'ORGNZT_0000000000002', '오신청', '!demo-pending', 'D0007', 'M', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'user2@example.com', '주무관', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000009', SYSDATE(), SYSDATE()),
('user3', 'ORGNZT_0000000000003', '윤신청', '!demo-pending', 'D0008', 'F', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'user3@example.com', '주무관', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000010', SYSDATE(), SYSDATE()),
('user4', 'ORGNZT_0000000000003', '강신청', '!demo-pending', 'D0009', 'M', '가상시 가상로 1', 'P01', '데모', '0000', '02', '0000', '000000', 'user4@example.com', '주무관', 'GROUP_00000000000000', 'P', 'USRCNFRM_00000000011', SYSDATE(), SYSDATE());
UPDATE COMTECOPSEQ SET NEXT_ID = 12 WHERE TABLE_NAME = 'USRCNFRM_ID';

INSERT INTO COMTNEMPLYRSCRTYESTBS (SCRTY_DTRMN_TRGET_ID, MBER_TY_CODE, AUTHOR_CODE) VALUES
('USRCNFRM_00000000003', 'USR03', 'ROLE_ADMIN'),
('USRCNFRM_00000000004', 'USR03', 'ROLE_ADMIN'),
('USRCNFRM_00000000005', 'USR03', 'ROLE_ASSET'),
('USRCNFRM_00000000006', 'USR03', 'ROLE_USER'),
('USRCNFRM_00000000007', 'USR03', 'ROLE_USER'),
('USRCNFRM_00000000008', 'USR03', 'ROLE_USER'),
('USRCNFRM_00000000009', 'USR03', 'ROLE_USER'),
('USRCNFRM_00000000010', 'USR03', 'ROLE_USER'),
('USRCNFRM_00000000011', 'USR03', 'ROLE_USER');

-- 부서장: 총무과·정보화팀. 민원실은 부서장 없음
INSERT INTO ASSET_DEPT_HEAD (ORGNZT_ID, EMPLYR_ID, ASSIGN_ID, ASSIGN_PNTTM) VALUES
('ORGNZT_0000000000001', 'head1', 'admin1', SYSDATE()),
('ORGNZT_0000000000002', 'head2', 'admin1', SYSDATE());

-- ========== 9. 시드: 품목·재고·개별 자산 (재고는 입고 이력과 함께 적재) ==========
INSERT INTO ASSET_ITEM (ITEM_CODE, ITEM_NM, ITEM_TY, CL_CODE, UNIT_CODE, MIN_QTY, USE_AT, STOCK_QTY, RESERVED_QTY, FRST_REGISTER_ID, FRST_REGIST_PNTTM) VALUES
('ITEM_000000000000001', 'A4 용지', 'QTY', 'SUPPLY', 'BOX', 10, 'Y', 30, 0, 'keeper', SYSDATE()),
('ITEM_000000000000002', '볼펜(흑색)', 'QTY', 'OFFICE', 'EA', 50, 'Y', 200, 0, 'keeper', SYSDATE()),
('ITEM_000000000000003', '프린터 토너', 'QTY', 'SUPPLY', 'EA', 5, 'Y', 3, 0, 'keeper', SYSDATE()),
('ITEM_000000000000004', '점착 메모지', 'QTY', 'OFFICE', 'PACK', 20, 'Y', 40, 0, 'keeper', SYSDATE()),
('ITEM_000000000000005', '클리어 파일', 'QTY', 'OFFICE', 'EA', 30, 'Y', 100, 0, 'keeper', SYSDATE()),
('ITEM_000000000000006', '노트북', 'UNIT', 'IT', 'SET', 2, 'Y', 0, 0, 'keeper', SYSDATE()),
('ITEM_000000000000007', '모니터', 'UNIT', 'IT', 'SET', 2, 'Y', 0, 0, 'keeper', SYSDATE()),
('ITEM_000000000000008', '태블릿', 'UNIT', 'IT', 'SET', 3, 'Y', 0, 0, 'keeper', SYSDATE());

INSERT INTO ASSET_STOCK_HIST (STOCK_HIST_ID, ITEM_CODE, HIST_TY, CHANGE_QTY, AFTER_QTY, RESN, PROCESS_ID, PROCESS_PNTTM) VALUES
('STKH_000000000000001', 'ITEM_000000000000001', 'IN', 30, 30, '초기 재고', 'keeper', SYSDATE()),
('STKH_000000000000002', 'ITEM_000000000000002', 'IN', 200, 200, '초기 재고', 'keeper', SYSDATE()),
('STKH_000000000000003', 'ITEM_000000000000003', 'IN', 3, 3, '초기 재고', 'keeper', SYSDATE()),
('STKH_000000000000004', 'ITEM_000000000000004', 'IN', 40, 40, '초기 재고', 'keeper', SYSDATE()),
('STKH_000000000000005', 'ITEM_000000000000005', 'IN', 100, 100, '초기 재고', 'keeper', SYSDATE()),
('STKH_000000000000006', 'ITEM_000000000000006', 'IN', 1, 1, '초기 등록 NB-2026-0001', 'keeper', SYSDATE()),
('STKH_000000000000007', 'ITEM_000000000000006', 'IN', 1, 2, '초기 등록 NB-2026-0002', 'keeper', SYSDATE()),
('STKH_000000000000008', 'ITEM_000000000000006', 'IN', 1, 3, '초기 등록 NB-2026-0003', 'keeper', SYSDATE()),
('STKH_000000000000009', 'ITEM_000000000000007', 'IN', 1, 1, '초기 등록 MN-2026-0001', 'keeper', SYSDATE()),
('STKH_000000000000010', 'ITEM_000000000000007', 'IN', 1, 2, '초기 등록 MN-2026-0002', 'keeper', SYSDATE()),
('STKH_000000000000011', 'ITEM_000000000000007', 'IN', 1, 3, '초기 등록 MN-2026-0003', 'keeper', SYSDATE()),
('STKH_000000000000012', 'ITEM_000000000000007', 'IN', 1, 4, '초기 등록 MN-2026-0004', 'keeper', SYSDATE()),
('STKH_000000000000013', 'ITEM_000000000000008', 'IN', 1, 1, '초기 등록 TB-2026-0001', 'keeper', SYSDATE()),
('STKH_000000000000014', 'ITEM_000000000000008', 'IN', 1, 2, '초기 등록 TB-2026-0002', 'keeper', SYSDATE());

INSERT INTO ASSET_UNIT (ASSET_NO, ITEM_CODE, STTUS, USER_ID, RM, FRST_REGISTER_ID, FRST_REGIST_PNTTM) VALUES
('NB-2026-0001', 'ITEM_000000000000006', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('NB-2026-0002', 'ITEM_000000000000006', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('NB-2026-0003', 'ITEM_000000000000006', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('MN-2026-0001', 'ITEM_000000000000007', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('MN-2026-0002', 'ITEM_000000000000007', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('MN-2026-0003', 'ITEM_000000000000007', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('MN-2026-0004', 'ITEM_000000000000007', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('TB-2026-0001', 'ITEM_000000000000008', 'STORED', NULL, NULL, 'keeper', SYSDATE()),
('TB-2026-0002', 'ITEM_000000000000008', 'STORED', NULL, NULL, 'keeper', SYSDATE());

INSERT INTO ASSET_UNIT_HIST (UNIT_HIST_ID, ASSET_NO, BEFORE_STTUS, AFTER_STTUS, RESN, PROCESS_ID, PROCESS_PNTTM)
SELECT CONCAT('UNTH_', LPAD(ROW_NUMBER() OVER (ORDER BY ASSET_NO), 15, '0')), ASSET_NO, NULL, 'STORED', '초기 등록', 'keeper', SYSDATE()
  FROM ASSET_UNIT;
