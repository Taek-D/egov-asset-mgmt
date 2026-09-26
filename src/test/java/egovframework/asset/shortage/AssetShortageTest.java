package egovframework.asset.shortage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.shortage.service.AssetShortageRunVO;
import egovframework.asset.shortage.service.AssetShortageService;
import egovframework.asset.shortage.service.AssetShortageVO;
import egovframework.asset.shortage.service.impl.AssetShortageDAO;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.com.sym.bat.service.BatchResult;
import egovframework.com.sym.bat.service.EgovBatchResultService;
import jakarta.annotation.Resource;

/**
 * 재고 부족 집계(§10): 대상 판정, 실행 기록, 최근 실행, 공통컴포넌트 배치 결과, 실패 처리, 야간 스케줄.
 */
class AssetShortageTest extends AssetIntegrationSupport {

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "egov.sqlSession")
	private SqlSessionFactory sqlSessionFactory;

	@Resource(name = "assetShortageService")
	private AssetShortageService shortageService;

	@Resource(name = "assetItemService")
	private AssetItemService itemService;

	@Resource(name = "assetStockService")
	private AssetStockService stockService;

	@Resource(name = "egovBatchResultService")
	private EgovBatchResultService batchResultService;

	@Resource(name = "egovBatchOpertIdGnrService")
	private EgovIdGnrService batchOpertIdGnrService;

	@Resource(name = "assetShortageTrigger")
	private CronTrigger shortageTrigger;

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private String newItem(String ty, int minQty) {
		AssetItemVO vo = new AssetItemVO();
		vo.setItemNm("부족" + ty + "-" + System.nanoTime());
		vo.setItemTy(ty);
		vo.setClCode("SUPPLY");
		vo.setUnitCode("EA");
		vo.setMinQty(minQty);
		return itemService.insertItem(vo, "keeper");
	}

	private String newQtyItem(int minQty, int stock) {
		String code = newItem(AssetItemVO.TY_QTY, minQty);
		if (stock > 0) {
			stockService.receive(code, stock, "부족 집계 테스트", "keeper");
		}
		return code;
	}

	private String newUnitItem(int minQty, int stored, int inUse, int reserved) {
		String code = newItem(AssetItemVO.TY_UNIT, minQty);
		addUnits(code, stored, "STORED");
		addUnits(code, inUse, "IN_USE");
		jdbc().update("UPDATE ASSET_ITEM SET RESERVED_QTY = ? WHERE ITEM_CODE = ?", reserved, code);
		return code;
	}

	private void addUnits(String itemCode, int count, String sttus) {
		for (int i = 0; i < count; i++) {
			jdbc().update("INSERT INTO ASSET_UNIT (ASSET_NO, ITEM_CODE, STTUS, FRST_REGISTER_ID, FRST_REGIST_PNTTM) VALUES (?, ?, ?, 'keeper', NOW())",
					"T7-" + System.nanoTime(), itemCode, sttus);
		}
	}

	/** 실행의 부족 품목(코드 → 행) */
	private Map<String, AssetShortageVO> itemsOf(String runId) {
		AssetShortageVO search = new AssetShortageVO();
		search.setRunId(runId);
		search.setFirstIndex(0);
		search.setRecordCountPerPage(100000);
		Map<String, AssetShortageVO> map = new HashMap<>();
		for (AssetShortageVO row : shortageService.selectShortageList(search)) {
			map.put(row.getItemCode(), row);
		}
		return map;
	}

	private int count(String sql, Object... args) {
		return jdbc().queryForObject(sql, Integer.class, args);
	}

	@Test
	void onlyActiveItemsBelowMinimumAreRecorded() {
		String qtyShort = newQtyItem(5, 3);          // 가용 3 < 5 → 포함, 부족 2
		String qtyEqual = newQtyItem(5, 5);          // 가용 = 최소 → 제외
		String qtyOff = newQtyItem(5, 1);            // 사용 중지 → 제외
		itemService.deactivateItem(qtyOff, "keeper");
		String qtyNoMin = newQtyItem(0, 0);          // 최소 0 → 제외
		String unitShort = newUnitItem(3, 4, 1, 2);  // 보관 4 − 확보 2 = 2 < 3 (사용 중 1개는 세지 않음) → 부족 1
		String unitEqual = newUnitItem(2, 3, 5, 1);  // 3 − 1 = 2 = 최소 → 제외
		String unitOverReserved = newUnitItem(1, 1, 0, 2); // 1 − 2 = −1 → 포함, 부족 2

		AssetShortageRunVO run = shortageService.run(AssetShortageService.RUN_MANUAL, "keeper");

		assertTrue(run.isSuccess(), run.getErrorInfo());
		Map<String, AssetShortageVO> items = itemsOf(run.getRunId());
		assertShort(items.get(qtyShort), 3, 5, 2);
		assertShort(items.get(unitShort), 2, 3, 1);
		assertShort(items.get(unitOverReserved), -1, 1, 2);
		assertFalse(items.containsKey(qtyEqual), "가용 = 최소 수량은 제외");
		assertFalse(items.containsKey(qtyOff), "사용 중지 품목은 제외");
		assertFalse(items.containsKey(qtyNoMin));
		assertFalse(items.containsKey(unitEqual), "개별 자산 가용 = 최소 수량은 제외");

		// 집계 가용은 재고 API의 가용과 같다
		for (String code : List.of(qtyShort, unitShort, unitOverReserved, qtyEqual, unitEqual)) {
			int avail = stockService.getAvailableQty(code);
			if (items.containsKey(code)) {
				assertEquals(avail, items.get(code).getAvailQty(), code);
			} else {
				assertTrue(avail >= jdbc().queryForObject("SELECT MIN_QTY FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, code), code);
			}
		}
		assertTrue(items.get(qtyShort).getItemNm().startsWith("부족QTY-"));

		// 실행 기록: 구분·실행자·건수
		Map<String, Object> row = jdbc().queryForMap("SELECT RUN_TY, RUN_USER_ID, ITEM_CNT FROM ASSET_SHORTAGE_RUN WHERE RUN_ID = ?", run.getRunId());
		assertEquals("MANUAL", row.get("RUN_TY"));
		assertEquals("keeper", row.get("RUN_USER_ID"));
		assertEquals(count("SELECT COUNT(*) FROM ASSET_SHORTAGE WHERE RUN_ID = ?", run.getRunId()), ((Number) row.get("ITEM_CNT")).intValue());
		assertEquals(run.getItemCnt(), ((Number) row.get("ITEM_CNT")).intValue());
	}

	private static void assertShort(AssetShortageVO row, int avail, int min, int shortQty) {
		assertNotNull(row, "부족 품목에 있어야 함");
		assertEquals(avail, row.getAvailQty());
		assertEquals(min, row.getMinQty());
		assertEquals(shortQty, row.getShortQty());
	}

	@Test
	void eachRunWritesSuccessBatchResultVisibleInBaseList() throws Exception {
		AssetShortageRunVO run = shortageService.run(AssetShortageService.RUN_MANUAL, "keeper");
		assertTrue(run.isSuccess(), run.getErrorInfo());
		assertNotNull(run.getBatchResultId());

		BatchResult key = new BatchResult();
		key.setBatchResultId(run.getBatchResultId());
		BatchResult detail = batchResultService.selectBatchResult(key);
		assertNotNull(detail, "기반 배치 결과 상세 조회에 보여야 함");
		assertEquals("01", detail.getSttus());
		assertEquals("정상", detail.getSttusNm());
		assertEquals(AssetShortageService.BATCH_OPERT_ID, detail.getBatchOpertId());
		assertEquals("야간 재고 부족 집계", detail.getBatchOpertNm());
		assertEquals(AssetShortageService.BATCH_SCHDUL_ID, detail.getBatchSchdulId());
		assertEquals(14, detail.getExecutBeginTime().length());
		assertEquals(14, detail.getExecutEndTime().length());
		assertTrue(detail.getParamtr().contains(run.getRunId()));

		// 기반 목록 화면과 같은 조회(첫 페이지, 작업명 검색)
		BatchResult search = new BatchResult();
		search.setFirstIndex(0);
		search.setRecordCountPerPage(10);
		search.setSearchCondition("0");
		search.setSearchKeyword("야간 재고 부족 집계");
		List<BatchResult> list = batchResultService.selectBatchResultList(search);
		assertTrue(list.stream().anyMatch(r -> r.getBatchResultId().equals(run.getBatchResultId()) && "01".equals(r.getSttus())),
				"기반 배치 결과 목록에 보여야 함");
		assertTrue(batchResultService.selectBatchResultListCnt(search) >= 1);

		// 기본 조건(검색 없음) 목록에도 보인다
		BatchResult all = new BatchResult();
		all.setFirstIndex(0);
		all.setRecordCountPerPage(10);
		assertTrue(batchResultService.selectBatchResultList(all).stream().anyMatch(r -> r.getBatchResultId().equals(run.getBatchResultId())));

		// 기반 BatchScheduler가 셸 작업으로 돌리지 않도록 스케줄 행은 없다
		assertEquals(0, count("SELECT COUNT(*) FROM COMTNBATCHSCHDUL WHERE BATCH_OPERT_ID = ? OR BATCH_SCHDUL_ID = ?",
				AssetShortageService.BATCH_OPERT_ID, AssetShortageService.BATCH_SCHDUL_ID));
		// 배치작업 ID 생성기는 고정 ID를 다시 내주지 않는다
		assertNotEquals(AssetShortageService.BATCH_OPERT_ID, batchOpertIdGnrService.getNextStringId());
	}

	@Test
	void latestRunIsNewest() {
		AssetShortageRunVO first = shortageService.run(AssetShortageService.RUN_MANUAL, "keeper");
		AssetShortageRunVO second = shortageService.run(AssetShortageService.RUN_MANUAL, "admin1");
		assertTrue(first.isSuccess() && second.isSuccess());

		AssetShortageRunVO latest = shortageService.selectLatestRun();
		assertEquals(second.getRunId(), latest.getRunId());
		assertEquals("MANUAL", latest.getRunTy());
		assertEquals("admin1", latest.getRunUserId());
		assertNotNull(latest.getRunUserNm());
		assertEquals(19, latest.getRunPnttm().length(), "yyyy-MM-dd HH:mm:ss");
		assertEquals(second.getItemCnt(), latest.getItemCnt());
	}

	@Test
	void zeroHitAutoRunIsStillRecorded() throws Exception {
		AssetShortageDAO noTargets = new AssetShortageDAO() {
			@Override
			public List<AssetShortageVO> selectShortageTargets() {
				return List.of();
			}
		};
		AssetShortageRunVO run = withDao(noTargets, () -> shortageService.run(AssetShortageService.RUN_AUTO, null));

		assertTrue(run.isSuccess(), run.getErrorInfo());
		Map<String, Object> row = jdbc().queryForMap("SELECT RUN_TY, RUN_USER_ID, ITEM_CNT FROM ASSET_SHORTAGE_RUN WHERE RUN_ID = ?", run.getRunId());
		assertEquals("AUTO", row.get("RUN_TY"));
		assertNull(row.get("RUN_USER_ID"));
		assertEquals(0, ((Number) row.get("ITEM_CNT")).intValue());
		assertEquals(0, count("SELECT COUNT(*) FROM ASSET_SHORTAGE WHERE RUN_ID = ?", run.getRunId()));

		BatchResult key = new BatchResult();
		key.setBatchResultId(run.getBatchResultId());
		BatchResult detail = batchResultService.selectBatchResult(key);
		assertEquals("01", detail.getSttus());
		assertEquals("SYSTEM", detail.getFrstRegisterId());
		assertEquals(run.getRunId(), shortageService.selectLatestRun().getRunId());
	}

	@Test
	void failureRecordsFailedBatchResultAndNoHalfWrittenRun() throws Exception {
		newQtyItem(5, 0); // 부족 품목이 최소 1건 있어 실행 행을 쓴 뒤 품목 쓰기에서 실패한다
		AssetShortageDAO failing = new AssetShortageDAO() {
			@Override
			public void insertShortageList(String runId, List<AssetShortageVO> items) {
				throw new IllegalStateException("테스트용 품목 저장 실패");
			}
		};
		AssetShortageRunVO run = withDao(failing, () -> shortageService.run(AssetShortageService.RUN_MANUAL, "keeper"));

		assertFalse(run.isSuccess());
		assertEquals("02", run.getSttus());
		assertNotNull(run.getRunId(), "실행 행을 쓴 뒤 실패해야 한다");
		assertEquals(0, count("SELECT COUNT(*) FROM ASSET_SHORTAGE_RUN WHERE RUN_ID = ?", run.getRunId()), "반쯤 쓴 실행이 남으면 안 됨");
		assertEquals(0, count("SELECT COUNT(*) FROM ASSET_SHORTAGE WHERE RUN_ID = ?", run.getRunId()));

		BatchResult key = new BatchResult();
		key.setBatchResultId(run.getBatchResultId());
		BatchResult detail = batchResultService.selectBatchResult(key);
		assertNotNull(detail, "실패 결과가 기반 배치 결과에 남아야 함");
		assertEquals("02", detail.getSttus());
		assertEquals("비정상", detail.getSttusNm());
		assertTrue(detail.getErrorInfo().contains("테스트용 품목 저장 실패"));
		AssetShortageRunVO latest = shortageService.selectLatestRun();
		assertTrue(latest == null || !run.getRunId().equals(latest.getRunId()));
	}

	@Test
	void rejectsBadArgumentsBeforeWriting() {
		assertThrows(AssetItemException.class, () -> shortageService.run("NIGHT", "keeper"));
		assertThrows(AssetItemException.class, () -> shortageService.run(AssetShortageService.RUN_MANUAL, null));
		assertThrows(AssetItemException.class, () -> shortageService.run(AssetShortageService.RUN_MANUAL, " "));
	}

	@Test
	void nightlyScheduleDefaultsTo0200Seoul() {
		assertEquals("0 0 2 * * *", shortageTrigger.getExpression());
		Instant next = shortageTrigger.nextExecution(new SimpleTriggerContext());
		ZonedDateTime seoul = next.atZone(ZoneId.of("Asia/Seoul"));
		assertEquals(2, seoul.getHour());
		assertEquals(0, seoul.getMinute());
		assertEquals(0, seoul.getSecond());
	}

	private interface Call<T> {
		T call() throws Exception;
	}

	/** 서비스 구현의 DAO를 잠시 바꿔 실행한다(나머지 메서드는 실제 SQL) */
	private <T> T withDao(AssetShortageDAO dao, Call<T> call) throws Exception {
		dao.setSqlSessionFactory(sqlSessionFactory);
		Object target = AopTestUtils.getUltimateTargetObject(shortageService);
		Object original = ReflectionTestUtils.getField(target, "assetShortageDAO");
		ReflectionTestUtils.setField(target, "assetShortageDAO", dao);
		try {
			return call.call();
		} finally {
			ReflectionTestUtils.setField(target, "assetShortageDAO", original);
		}
	}
}
