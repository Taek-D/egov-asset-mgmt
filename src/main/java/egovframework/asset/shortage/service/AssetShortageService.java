package egovframework.asset.shortage.service;

import java.util.List;

/**
 * 재고 부족 집계(명세 §10): 사용 중이면서 가용 &lt; 최소 수량인 품목을 실행마다 기록한다.
 */
public interface AssetShortageService {

	/** 실행 구분: 야간 자동 */
	String RUN_AUTO = "AUTO";
	/** 실행 구분: 화면에서 즉시 */
	String RUN_MANUAL = "MANUAL";

	/** 공통컴포넌트 배치작업 ID(db/init/50_asset_batch.sql) */
	String BATCH_OPERT_ID = "BAT00000000000000001";
	/** 배치 결과 행의 배치스케줄 ID. 스케줄 행은 없다(기반 스케줄러가 셸 작업으로 돌리지 않도록) */
	String BATCH_SCHDUL_ID = "BSC_ASSET_SHORTAGE";
	/** 배치 결과 상태(공통코드 COM076): 정상 */
	String STTUS_SUCCESS = "01";
	/** 배치 결과 상태(공통코드 COM076): 비정상 */
	String STTUS_FAILURE = "02";

	/**
	 * 집계를 실행하고 공통컴포넌트 배치 결과에 성공·실패를 한 행 남긴다.
	 * 집계 중 오류가 나면 실행 기록은 남기지 않고(전부 되돌림) 실패 결과만 남긴 뒤 반환한다(예외를 던지지 않는다).
	 *
	 * @param runTy   {@link #RUN_AUTO} / {@link #RUN_MANUAL}
	 * @param actorId 실행자 EMPLYR_ID(자동 실행은 null 가능, 즉시 실행은 필수)
	 * @return 실행 결과(성공 여부, 배치 결과 ID, 실패 시 오류 내용)
	 */
	AssetShortageRunVO run(String runTy, String actorId);

	/** 가장 최근 실행(없으면 null) */
	AssetShortageRunVO selectLatestRun();

	/** 실행별 부족 품목(searchVO.runId, 페이지 조건) */
	List<AssetShortageVO> selectShortageList(AssetShortageVO searchVO);

	int selectShortageListTotCnt(AssetShortageVO searchVO);
}
