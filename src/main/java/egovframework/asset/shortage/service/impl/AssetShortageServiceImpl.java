package egovframework.asset.shortage.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.cmmn.exception.FdlException;
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.shortage.service.AssetShortageRunVO;
import egovframework.asset.shortage.service.AssetShortageService;
import egovframework.asset.shortage.service.AssetShortageVO;
import egovframework.com.sym.bat.service.BatchResult;
import egovframework.com.sym.bat.service.EgovBatchSchdulService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * 재고 부족 집계 서비스 구현.
 * <p>
 * public 메서드는 업무 트랜잭션(REQUIRED, 모든 예외 롤백) 안에서 돈다. 실패 결과 행이 함께 롤백되지 않도록
 * 집계(실행·품목·성공 결과)는 세이브포인트(NESTED) 안에서 하고, 실패하면 세이브포인트까지만 되돌린 뒤
 * 실패 결과를 남기고 정상 반환한다.
 */
@Service("assetShortageService")
public class AssetShortageServiceImpl extends EgovAbstractServiceImpl implements AssetShortageService {

	private static final int ERROR_INFO_MAX = 2000;

	@Resource(name = "assetShortageDAO")
	private AssetShortageDAO assetShortageDAO;

	@Resource(name = "assetShortageRunIdGnrService")
	private EgovIdGnrService runIdGnrService;

	@Resource(name = "egovBatchResultIdGnrService")
	private EgovIdGnrService batchResultIdGnrService;

	@Resource(name = "egovBatchSchdulService")
	private EgovBatchSchdulService egovBatchSchdulService;

	@Resource(name = "txManager")
	private PlatformTransactionManager txManager;

	private TransactionTemplate nestedTx;

	@PostConstruct
	void initNestedTx() {
		nestedTx = new TransactionTemplate(txManager);
		nestedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
	}

	@Override
	public AssetShortageRunVO run(String runTy, String actorId) {
		if (!RUN_AUTO.equals(runTy) && !RUN_MANUAL.equals(runTy)) {
			throw new AssetItemException("실행 구분이 올바르지 않습니다.");
		}
		boolean noActor = actorId == null || actorId.isBlank();
		if (RUN_MANUAL.equals(runTy) && noActor) {
			throw new AssetItemException("즉시 실행은 실행자가 있어야 합니다.");
		}
		String registerId = noActor ? "SYSTEM" : actorId;
		AssetShortageRunVO run = new AssetShortageRunVO();
		run.setRunTy(runTy);
		run.setRunUserId(noActor ? null : actorId);

		BatchResult result = new BatchResult();
		result.setBatchOpertId(BATCH_OPERT_ID);
		result.setBatchSchdulId(BATCH_SCHDUL_ID);
		result.setFrstRegisterId(registerId);
		result.setLastUpdusrId(registerId);
		result.setParamtr("실행구분=" + runTy + ", 실행자=" + registerId);
		result.setExecutBeginTime(now());
		try {
			nestedTx.executeWithoutResult(status -> aggregate(run, result));
		} catch (RuntimeException e) {
			egovLogger.error("재고 부족 집계에 실패했습니다.", e);
			String info = "재고 부족 집계에 실패했습니다(실행 기록은 남기지 않음): " + e;
			result.setSttus(STTUS_FAILURE);
			result.setErrorInfo(info.length() > ERROR_INFO_MAX ? info.substring(0, ERROR_INFO_MAX) : info);
			result.setExecutEndTime(now());
			insertBatchResult(result);
		}
		run.setBatchResultId(result.getBatchResultId());
		run.setSttus(result.getSttus());
		run.setErrorInfo(result.getErrorInfo());
		return run;
	}

	/** 실행 행 → 부족 품목 → 성공 결과 행. 하나라도 실패하면 세이브포인트로 전부 되돌린다 */
	private void aggregate(AssetShortageRunVO run, BatchResult result) {
		List<AssetShortageVO> items = assetShortageDAO.selectShortageTargets();
		run.setRunId(nextId(runIdGnrService));
		run.setItemCnt(items.size());
		assetShortageDAO.insertRun(run);
		if (!items.isEmpty()) {
			assetShortageDAO.insertShortageList(run.getRunId(), items);
		}
		result.setSttus(STTUS_SUCCESS);
		result.setErrorInfo("");
		result.setParamtr(result.getParamtr() + ", 실행ID=" + run.getRunId() + ", 부족 품목=" + items.size() + "건");
		result.setExecutEndTime(now());
		insertBatchResult(result);
	}

	private void insertBatchResult(BatchResult result) {
		result.setBatchResultId(nextId(batchResultIdGnrService));
		try {
			egovBatchSchdulService.insertBatchResult(result);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("배치 결과를 저장하지 못했습니다.", e);
		}
	}

	private static String nextId(EgovIdGnrService idGnrService) {
		try {
			return idGnrService.getNextStringId();
		} catch (FdlException e) {
			throw new IllegalStateException("ID를 만들지 못했습니다.", e);
		}
	}

	/** 기반 BatchJobListener와 같은 형식(yyyyMMddHHmmss) */
	private static String now() {
		return new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
	}

	@Override
	public AssetShortageRunVO selectLatestRun() {
		return assetShortageDAO.selectLatestRun();
	}

	@Override
	public List<AssetShortageVO> selectShortageList(AssetShortageVO searchVO) {
		return assetShortageDAO.selectShortageList(searchVO);
	}

	@Override
	public int selectShortageListTotCnt(AssetShortageVO searchVO) {
		return assetShortageDAO.selectShortageListTotCnt(searchVO);
	}
}
