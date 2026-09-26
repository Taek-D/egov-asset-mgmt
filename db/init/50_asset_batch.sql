-- 초기화 클라이언트가 파일을 latin1로 읽어 한글이 이중 인코딩되는 것을 막는다(서버 collation 설정 아님)
SET NAMES utf8mb4;

-- 야간 재고 부족 집계: 공통컴포넌트 배치 결과 화면(/sym/bat/getBatchResultList.do)에 실행 결과가 보이도록 배치작업 1건만 등록한다.
-- 실행은 앱 내부 스케줄러(context-asset-scheduling.xml)가 맡는다.
-- 배치스케줄(COMTNBATCHSCHDUL) 행은 넣지 않는다: 기반 BatchScheduler가 스케줄 행을 모두 셸 작업으로 실행하기 때문이다.
INSERT INTO COMTNBATCHOPERT (BATCH_OPERT_ID, BATCH_OPERT_NM, BATCH_PROGRM, PARAMTR, USE_AT, FRST_REGISTER_ID, FRST_REGIST_PNTTM, LAST_UPDUSR_ID, LAST_UPDT_PNTTM)
VALUES ('BAT00000000000000001', '야간 재고 부족 집계', 'egovframework.asset.shortage (앱 내부 스케줄러)', 'AUTO/MANUAL', 'Y', 'SYSTEM', SYSDATE(), 'SYSTEM', SYSDATE());

-- 배치작업 ID 생성기가 위 고정 ID를 다시 내주지 않도록 다음 번호를 넘긴다
UPDATE COMTECOPSEQ SET NEXT_ID = 2 WHERE TABLE_NAME = 'BATCH_OPERT_ID' AND NEXT_ID < 2;
