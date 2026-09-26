package egovframework.asset.shortage.service;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 재고 부족 집계 실행 1건과 그 배치 결과.
 */
@Getter
@Setter
public class AssetShortageRunVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String runId;
	/** AUTO / MANUAL */
	private String runTy;
	/** 실행 시각(yyyy-MM-dd HH:mm:ss) */
	private String runPnttm;
	private int itemCnt;
	private String runUserId;
	private String runUserNm;

	/** 공통컴포넌트 배치 결과 ID */
	private String batchResultId;
	/** 배치 결과 상태(01 정상 / 02 비정상) */
	private String sttus;
	private String errorInfo;

	public boolean isSuccess() {
		return AssetShortageService.STTUS_SUCCESS.equals(sttus);
	}
}
