package egovframework.asset.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.asset.unit.service.AssetUnitHistVO;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.asset.unit.service.AssetUnitVO;
import jakarta.annotation.Resource;

/**
 * 개별 자산: 등록(입고), 상태 전이 표, 확보 보호(§14 #7), 지급 배정, 잠금 아래 동시 수리.
 */
class AssetUnitTest extends AssetIntegrationSupport {

	private static final String STORED = AssetUnitVO.STORED;
	private static final String IN_USE = AssetUnitVO.IN_USE;
	private static final String REPAIR = AssetUnitVO.REPAIR;
	private static final String DISPOSED = AssetUnitVO.DISPOSED;

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "assetItemService")
	private AssetItemService itemService;

	@Resource(name = "assetStockService")
	private AssetStockService stockService;

	@Resource(name = "assetUnitService")
	private AssetUnitService unitService;

	@Resource(name = "txManager")
	private PlatformTransactionManager txManager;

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private String newItem(String ty) {
		AssetItemVO vo = new AssetItemVO();
		vo.setItemNm("자산" + ty + "-" + System.nanoTime());
		vo.setItemTy(ty);
		vo.setClCode("IT");
		vo.setUnitCode("SET");
		vo.setMinQty(0);
		return itemService.insertItem(vo, "keeper");
	}

	private static String newAssetNo() {
		return "T3-" + System.nanoTime();
	}

	private String register(String itemCode) {
		String assetNo = newAssetNo();
		unitService.registerUnit(unit(assetNo, itemCode, null), "keeper");
		return assetNo;
	}

	private static AssetUnitVO unit(String assetNo, String itemCode, String rm) {
		AssetUnitVO vo = new AssetUnitVO();
		vo.setAssetNo(assetNo);
		vo.setItemCode(itemCode);
		vo.setRm(rm);
		return vo;
	}

	private String newReq() {
		String reqNo = "REQ_T3" + String.format("%014d", System.nanoTime() % 100_000_000_000_000L);
		jdbc().update("INSERT INTO ASSET_REQ (REQ_NO, APPLCNT_ID, ORGNZT_ID, STTUS, REQ_PNTTM) VALUES (?, 'user1', 'ORGNZT_0000000000001', 'APPROVED', NOW())",
				reqNo);
		return reqNo;
	}

	private String sttus(String assetNo) {
		return jdbc().queryForObject("SELECT STTUS FROM ASSET_UNIT WHERE ASSET_NO = ?", String.class, assetNo);
	}

	private String userId(String assetNo) {
		return jdbc().queryForObject("SELECT USER_ID FROM ASSET_UNIT WHERE ASSET_NO = ?", String.class, assetNo);
	}

	private int unitCnt(String assetNo) {
		return jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_UNIT WHERE ASSET_NO = ?", Integer.class, assetNo);
	}

	/** 자산 이력 [이전, 이후, 사유, 신청번호, 처리자] (처리 순) */
	private List<Object[]> unitHist(String assetNo) {
		return jdbc().query(
				"SELECT BEFORE_STTUS, AFTER_STTUS, RESN, REQ_NO, PROCESS_ID FROM ASSET_UNIT_HIST WHERE ASSET_NO = ? ORDER BY UNIT_HIST_ID",
				(rs, n) -> new Object[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5) },
				assetNo);
	}

	/** 재고 변동 이력 [유형, 증감, 변동 후, 사유] (처리 순) */
	private List<Object[]> stockHist(String itemCode) {
		return jdbc().query(
				"SELECT HIST_TY, CHANGE_QTY, AFTER_QTY, RESN FROM ASSET_STOCK_HIST WHERE ITEM_CODE = ? ORDER BY STOCK_HIST_ID",
				(rs, n) -> new Object[] { rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4) }, itemCode);
	}

	private static void assertHist(Object[] row, String before, String after) {
		assertEquals(before, row[0], "before");
		assertEquals(after, row[1], "after");
	}

	/** 거부되고 상태·사용자·이력이 그대로인지 */
	private void assertRefused(String assetNo, String toSttus, String reason) {
		String before = sttus(assetNo);
		String user = userId(assetNo);
		int hist = unitHist(assetNo).size();
		assertThrows(AssetItemException.class, () -> unitService.changeStatus(assetNo, toSttus, reason, "keeper"),
				before + " -> " + toSttus);
		assertEquals(before, sttus(assetNo));
		assertEquals(user, userId(assetNo));
		assertEquals(hist, unitHist(assetNo).size());
	}

	// ---------- 등록 ----------

	@Test
	void registerCreatesStoredAssetWithInAndFirstHistory() {
		String code = newItem("UNIT");
		String a1 = newAssetNo();
		unitService.registerUnit(unit(" " + a1 + " ", code, "신규 구매"), "keeper");

		assertEquals(STORED, sttus(a1));
		assertNull(userId(a1));
		AssetUnitVO saved = unitService.selectUnit(a1);
		assertEquals(code, saved.getItemCode());
		assertEquals("신규 구매", saved.getRm());
		assertEquals("keeper", saved.getFrstRegisterId());

		List<Object[]> h = unitHist(a1);
		assertEquals(1, h.size());
		assertHist(h.get(0), null, STORED);
		assertEquals("keeper", h.get(0)[4]);

		List<Object[]> s = stockHist(code);
		assertEquals(1, s.size());
		assertEquals("IN", s.get(0)[0]);
		assertEquals(1, s.get(0)[1]);
		assertEquals(1, s.get(0)[2]);
		assertTrue(((String) s.get(0)[3]).contains(a1));

		// 두 번째 등록: 변동 후 = 가용(보관 2). 확보 1 뒤 세 번째: 보관 3 − 확보 1 = 2
		register(code);
		assertEquals(2, stockHist(code).get(1)[2]);
		stockService.reserve(code, 1, newReq(), "admin1");
		register(code);
		s = stockHist(code);
		assertEquals("IN", s.get(3)[0]);
		assertEquals(1, s.get(3)[1]);
		assertEquals(2, s.get(3)[2]);
		assertEquals(2, stockService.getAvailableQty(code));
	}

	@Test
	void duplicateAssetNoRefusedIncludingDisposed() {
		String code = newItem("UNIT");
		String other = newItem("UNIT");
		String a1 = register(code);
		AssetItemException ex = assertThrows(AssetItemException.class,
				() -> unitService.registerUnit(unit(a1, other, null), "keeper"));
		assertTrue(ex.getMessage().contains(a1), ex.getMessage());

		unitService.changeStatus(a1, DISPOSED, "분실", "keeper");
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit(a1, other, null), "keeper"));
		assertEquals(1, unitCnt(a1));
		assertEquals(code, unitService.selectUnit(a1).getItemCode());
		assertEquals(0, stockHist(other).size());
	}

	@Test
	void registerRefusesQtyDeactivatedAndBadInput() {
		String qty = newItem("QTY");
		String a1 = newAssetNo();
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit(a1, qty, null), "keeper"));

		String stopped = newItem("UNIT");
		itemService.deactivateItem(stopped, "keeper");
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit(a1, stopped, null), "keeper"));

		String code = newItem("UNIT");
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit("  ", code, null), "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit(null, code, null), "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit("X".repeat(51), code, null), "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit(a1, null, null), "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.registerUnit(unit(a1, "ITEM_NOT_EXISTS_0000", null), "keeper"));

		assertEquals(0, unitCnt(a1));
		assertEquals(0, stockHist(qty).size());
		assertEquals(0, stockHist(stopped).size());
		assertEquals(0, stockHist(code).size());
	}

	// ---------- 상태 전이 ----------

	@Test
	void allowedTransitionsAreRecordedWithoutStockHistory() {
		String code = newItem("UNIT");
		String a1 = register(code);

		unitService.changeStatus(a1, REPAIR, "액정 파손", "keeper");
		assertEquals(REPAIR, sttus(a1));
		unitService.changeStatus(a1, STORED, null, "keeper");
		assertEquals(STORED, sttus(a1));
		unitService.changeStatus(a1, REPAIR, null, "keeper");
		unitService.changeStatus(a1, DISPOSED, "수리 불가", "keeper");
		assertEquals(DISPOSED, sttus(a1));

		List<Object[]> h = unitHist(a1);
		assertEquals(5, h.size());
		assertHist(h.get(0), null, STORED);
		assertHist(h.get(1), STORED, REPAIR);
		assertEquals("액정 파손", h.get(1)[2]);
		assertHist(h.get(2), REPAIR, STORED);
		assertHist(h.get(3), STORED, REPAIR);
		assertHist(h.get(4), REPAIR, DISPOSED);
		assertEquals("수리 불가", h.get(4)[2]);

		// 지급 → 반납(사용자 비움) → 보관에서 폐기
		String a2 = register(code);
		String reqNo = newReq();
		unitService.issueUnits(reqNo, code, List.of(a2), "user1", "keeper");
		assertEquals(IN_USE, sttus(a2));
		assertEquals("user1", userId(a2));
		unitService.changeStatus(a2, STORED, "퇴직 반납", "keeper");
		assertEquals(STORED, sttus(a2));
		assertNull(userId(a2));
		unitService.changeStatus(a2, DISPOSED, "파손", "keeper");
		assertEquals(DISPOSED, sttus(a2));
		h = unitHist(a2);
		assertHist(h.get(2), IN_USE, STORED);
		assertEquals("퇴직 반납", h.get(2)[2]);
		assertHist(h.get(3), STORED, DISPOSED);

		// 재고 변동 이력은 등록 IN 두 건뿐(반납·수리·폐기는 남기지 않는다)
		List<Object[]> s = stockHist(code);
		assertEquals(2, s.size());
		assertTrue(s.stream().allMatch(r -> "IN".equals(r[0])));
		assertEquals(0, stockService.getAvailableQty(code));
	}

	@Test
	void transitionsOutsideTableAreRefused() {
		String code = newItem("UNIT");
		String stored = register(code);
		// 보관 → 사용은 지급 처리 안에서만, 보관 → 보관(반납)도 없다
		assertRefused(stored, IN_USE, null);
		assertRefused(stored, STORED, null);
		assertRefused(stored, "LOST", "없는 상태");
		assertRefused(stored, null, null);

		String inUse = register(code);
		unitService.issueUnits(newReq(), code, List.of(inUse), "user2", "keeper");
		assertRefused(inUse, REPAIR, "고장");
		assertRefused(inUse, DISPOSED, "분실");
		assertRefused(inUse, IN_USE, null);

		String repair = register(code);
		unitService.changeStatus(repair, REPAIR, null, "keeper");
		assertRefused(repair, IN_USE, null);
		assertRefused(repair, REPAIR, null);

		String disposed = register(code);
		unitService.changeStatus(disposed, DISPOSED, "분실", "keeper");
		for (String to : List.of(STORED, IN_USE, REPAIR, DISPOSED)) {
			assertRefused(disposed, to, "사유");
		}

		assertThrows(AssetItemException.class, () -> unitService.changeStatus("T3-NOT-EXISTS", REPAIR, null, "keeper"));
	}

	@Test
	void disposeNeedsReason() {
		String code = newItem("UNIT");
		String stored = register(code);
		assertRefused(stored, DISPOSED, null);
		assertRefused(stored, DISPOSED, "   ");
		String repair = register(code);
		unitService.changeStatus(repair, REPAIR, null, "keeper");
		assertRefused(repair, DISPOSED, "");
		unitService.changeStatus(repair, DISPOSED, " 수리 불가 ", "keeper");
		assertEquals("수리 불가", unitHist(repair).get(2)[2]);
	}

	// ---------- 확보 보호(§14 #7) ----------

	@Test
	void reservedAssetsCannotBeRepairedOrDisposedAway() {
		String code = newItem("UNIT");
		String a1 = register(code);
		String a2 = register(code);
		String a3 = register(code);
		stockService.reserve(code, 2, newReq(), "admin1");

		// 보관 3, 확보 2: 한 대는 수리로 뺄 수 있다(3 − 1 ≥ 2)
		unitService.changeStatus(a1, REPAIR, null, "keeper");
		// 보관 2, 확보 2: 더 빼면 보관 1 < 확보 2 → 수리·폐기 모두 거부, 아무것도 바뀌지 않는다
		AssetItemException ex = assertThrows(AssetItemException.class, () -> unitService.changeStatus(a2, REPAIR, null, "keeper"));
		assertTrue(ex.getMessage().contains("확보"), ex.getMessage());
		assertRefused(a2, REPAIR, null);
		assertRefused(a3, DISPOSED, "분실");
		assertEquals(STORED, sttus(a2));
		assertEquals(STORED, sttus(a3));
		assertEquals(0, stockService.getAvailableQty(code));

		// 수리 → 폐기는 항상 가능(보관 수를 바꾸지 않는다)
		unitService.changeStatus(a1, DISPOSED, "수리 불가", "keeper");
		assertEquals(DISPOSED, sttus(a1));
		assertEquals(2, jdbc().queryForObject("SELECT RESERVED_QTY FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, code));
		assertEquals(4, stockHist(code).size()); // IN 3건 + RESERVE 1건(수리·폐기는 재고 이력 없음)

		// 수리 완료는 항상 가능
		String a4 = register(code);
		unitService.changeStatus(a4, REPAIR, null, "keeper");
		unitService.changeStatus(a4, STORED, "수리 완료", "keeper");
		assertEquals(STORED, sttus(a4));
	}

	@Test
	void concurrentRepairsNeverEatReservation() throws Exception {
		String code = newItem("UNIT");
		List<String> assets = List.of(register(code), register(code));
		stockService.reserve(code, 1, newReq(), "admin1");

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		int ok = 0;
		int refused = 0;
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (String assetNo : assets) {
				futures.add(pool.submit(() -> {
					start.await();
					unitService.changeStatus(assetNo, REPAIR, null, "keeper");
					return null;
				}));
			}
			start.countDown();
			for (Future<?> f : futures) {
				try {
					f.get(60, TimeUnit.SECONDS);
					ok++;
				} catch (ExecutionException e) {
					assertTrue(e.getCause() instanceof AssetItemException, String.valueOf(e.getCause()));
					refused++;
				}
			}
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, ok);
		assertEquals(1, refused);
		assertEquals(0, stockService.getAvailableQty(code));
	}

	// ---------- 지급 배정(T5용) ----------

	@Test
	void issueMovesExactlyGivenAssetsThenDeliverUnits() {
		String code = newItem("UNIT");
		String a1 = register(code);
		String a2 = register(code);
		String a3 = register(code);
		String reqNo = newReq();
		stockService.reserve(code, 2, reqNo, "admin1");

		List<String> picker = unitService.selectStoredUnits(code).stream().map(AssetUnitVO::getAssetNo).toList();
		assertEquals(List.of(a1, a2, a3), picker);

		// T5 순서: (신청서 잠금) → lockItems → issueUnits → deliverUnits, 한 트랜잭션
		new TransactionTemplate(txManager).executeWithoutResult(status -> {
			stockService.lockItems(List.of(code));
			unitService.issueUnits(reqNo, code, List.of(a3, a1), "user1", "keeper");
			stockService.deliverUnits(code, 2, reqNo, "keeper");
		});

		assertEquals(IN_USE, sttus(a1));
		assertEquals(IN_USE, sttus(a3));
		assertEquals("user1", userId(a1));
		assertEquals("user1", userId(a3));
		assertEquals(STORED, sttus(a2));
		assertNull(userId(a2));
		for (String a : List.of(a1, a3)) {
			List<Object[]> h = unitHist(a);
			assertEquals(2, h.size());
			assertHist(h.get(1), STORED, IN_USE);
			assertEquals(reqNo, h.get(1)[3]);
			assertEquals("keeper", h.get(1)[4]);
		}
		assertEquals(1, unitHist(a2).size());
		assertEquals(List.of(a2), unitService.selectStoredUnits(code).stream().map(AssetUnitVO::getAssetNo).toList());
		assertEquals(0, jdbc().queryForObject("SELECT RESERVED_QTY FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, code));
		assertEquals(1, stockService.getAvailableQty(code));
		List<Object[]> s = stockHist(code);
		assertEquals("DELIVER", s.get(s.size() - 1)[0]);
	}

	@Test
	void issueRefusesNonStoredWrongItemAndBadInputChangingNothing() {
		String code = newItem("UNIT");
		String other = newItem("UNIT");
		String a1 = register(code);
		String a2 = register(code);
		String inRepair = register(code);
		unitService.changeStatus(inRepair, REPAIR, null, "keeper");
		String foreign = register(other);
		String reqNo = newReq();

		assertThrows(AssetItemException.class, () -> unitService.issueUnits(reqNo, code, List.of(a1, inRepair), "user1", "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.issueUnits(reqNo, code, List.of(a1, foreign), "user1", "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.issueUnits(reqNo, code, List.of(a1, "T3-NOT-EXISTS"), "user1", "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.issueUnits(reqNo, code, List.of(a1, a1), "user1", "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.issueUnits(reqNo, code, List.of(), "user1", "keeper"));
		assertThrows(AssetItemException.class, () -> unitService.issueUnits(reqNo, code, List.of(a1), " ", "keeper"));

		for (String a : List.of(a1, a2)) {
			assertEquals(STORED, sttus(a));
			assertNull(userId(a));
			assertEquals(1, unitHist(a).size());
		}
		assertEquals(REPAIR, sttus(inRepair));
		assertEquals(STORED, sttus(foreign));

		// 이미 지급된 자산은 다시 배정할 수 없다
		unitService.issueUnits(reqNo, code, List.of(a2), "user1", "keeper");
		assertThrows(AssetItemException.class, () -> unitService.issueUnits(newReq(), code, List.of(a2), "user2", "keeper"));
		assertEquals("user1", userId(a2));
	}

	// ---------- 조회 ----------

	@Test
	void listFiltersAndDetailHistory() {
		String code = newItem("UNIT");
		String a1 = register(code);
		String a2 = register(code);
		unitService.changeStatus(a2, REPAIR, "점검", "keeper");

		AssetUnitVO search = new AssetUnitVO();
		search.setSearchItemCode(code);
		search.setFirstIndex(0);
		search.setRecordCountPerPage(10);
		assertEquals(2, unitService.selectUnitListTotCnt(search));
		List<AssetUnitVO> list = unitService.selectUnitList(search);
		assertEquals(List.of(a1, a2), list.stream().map(AssetUnitVO::getAssetNo).toList());
		assertTrue(list.get(0).getItemNm().startsWith("자산UNIT-"));

		search.setSearchSttus(REPAIR);
		assertEquals(List.of(a2), unitService.selectUnitList(search).stream().map(AssetUnitVO::getAssetNo).toList());
		assertEquals(1, unitService.selectUnitListTotCnt(search));

		AssetUnitVO byNo = new AssetUnitVO();
		byNo.setSearchAssetNo(a1.substring(3));
		byNo.setFirstIndex(0);
		byNo.setRecordCountPerPage(10);
		assertEquals(List.of(a1), unitService.selectUnitList(byNo).stream().map(AssetUnitVO::getAssetNo).toList());

		List<AssetUnitHistVO> hist = unitService.selectUnitHistList(a2);
		assertEquals(2, hist.size());
		assertNull(hist.get(0).getBeforeSttus());
		assertEquals(REPAIR, hist.get(1).getAfterSttus());
		assertEquals("점검", hist.get(1).getResn());
		assertThrows(AssetItemException.class, () -> unitService.selectUnit("T3-NOT-EXISTS"));
	}
}
