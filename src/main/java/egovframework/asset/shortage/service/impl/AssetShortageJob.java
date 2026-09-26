package egovframework.asset.shortage.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import egovframework.asset.shortage.service.AssetShortageRunVO;
import egovframework.asset.shortage.service.AssetShortageService;

/**
 * 야간 자동 집계 작업. context-asset-scheduling.xml에서만 빈으로 등록한다
 * (루트·MVC 두 컨텍스트가 egovframework를 스캔하므로 애너테이션을 달면 두 번 만들어진다).
 */
public class AssetShortageJob {

	private static final Logger LOGGER = LoggerFactory.getLogger(AssetShortageJob.class);

	private final AssetShortageService assetShortageService;

	public AssetShortageJob(AssetShortageService assetShortageService) {
		this.assetShortageService = assetShortageService;
	}

	public void execute() {
		AssetShortageRunVO run = assetShortageService.run(AssetShortageService.RUN_AUTO, null);
		if (run.isSuccess()) {
			LOGGER.info("야간 재고 부족 집계 완료: 실행 {}, 부족 품목 {}건", run.getRunId(), run.getItemCnt());
		} else {
			LOGGER.error("야간 재고 부족 집계 실패: 배치 결과 {}", run.getBatchResultId());
		}
	}
}
