package egovframework.asset.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import egovframework.asset.stock.service.AssetStockHistVO;
import egovframework.asset.stock.service.AssetStockService;
import jakarta.annotation.Resource;

/**
 * 재고 변동: 입고·조정, 가용 계산, 확보·복구·지급 이력, 동시 확보.
 */
class AssetStockTest extends AssetIntegrationSupport {

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "assetItemService")
	private AssetItemService itemService;

	@Resource(name = "assetStockService")
	private AssetStockService stockService;

	@Resource(name = "txManager")
	private PlatformTransactionManager txManager;

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private String newItem(String ty) {
		AssetItemVO vo = new AssetItemVO();
		vo.setItemNm("재고" + ty + "-" + System.nanoTime());
		vo.setItemTy(ty);
		vo.setClCode("SUPPLY");
		vo.setUnitCode("EA");
		vo.setMinQty(0);
		return itemService.insertItem(vo, "keeper");
	}

	private void addUnits(String itemCode, int count, String sttus) {
		for (int i = 0; i < count; i++) {
			jdbc().update("INSERT INTO ASSET_UNIT (ASSET_NO, ITEM_CODE, STTUS, FRST_REGISTER_ID, FRST_REGIST_PNTTM) VALUES (?, ?, ?, 'keeper', NOW())",
					"T2-" + System.nanoTime(), itemCode, sttus);
		}
	}

	private int stock(String itemCode) {
		return jdbc().queryForObject("SELECT STOCK_QTY FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, itemCode);
	}

	private int reserved(String itemCode) {
		return jdbc().queryForObject("SELECT RESERVED_QTY FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, itemCode);
	}

	/** 품목의 이력 [유형, 증감, 변동 후, 신청번호, 사유, 처리자] (처리 순) */
	private List<Object[]> hist(String itemCode) {
		return jdbc().query(
				"SELECT HIST_TY, CHANGE_QTY, AFTER_QTY, REQ_NO, RESN, PROCESS_ID FROM ASSET_STOCK_HIST WHERE ITEM_CODE = ? ORDER BY STOCK_HIST_ID",
				(rs, n) -> new Object[] { rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6) },
				itemCode);
	}

	private static void assertHist(Object[] row, String ty, int change, int after) {
		assertEquals(ty, row[0]);
		assertEquals(change, row[1], "change of " + ty);
		assertEquals(after, row[2], "after of " + ty);
	}

	private String newReq() {
		String reqNo = "REQ_T2" + String.format("%014d", System.nanoTime() % 100_000_000_000_000L);
		jdbc().update("INSERT INTO ASSET_REQ (REQ_NO, APPLCNT_ID, ORGNZT_ID, STTUS, REQ_PNTTM) VALUES (?, 'user1', 'ORGNZT_0000000000001', 'APPROVED', NOW())",
				reqNo);
		return reqNo;
	}

	@Test
	void receiveIncreasesStockAndRecordsIn() {
		String code = newItem("QTY");
		stockService.receive(code, 7, "구매", "keeper");
		stockService.receive(code, 3, null, "keeper");
		assertEquals(10, stock(code));
		List<Object[]> h = hist(code);
		assertEquals(2, h.size());
		assertHist(h.get(0), "IN", 7, 7);
		assertHist(h.get(1), "IN", 3, 10);
		assertEquals("구매", h.get(0)[4]);
		assertEquals("keeper", h.get(0)[5]);

		assertThrows(AssetItemException.class, () -> stockService.receive(code, 0, null, "keeper"));
		String unit = newItem("UNIT");
		assertThrows(AssetItemException.class, () -> stockService.receive(unit, 1, null, "keeper"));
		assertEquals(10, stock(code));
	}

	@Test
	void adjustNeedsReasonAndCannotGoNegative() {
		String code = newItem("QTY");
		stockService.receive(code, 5, null, "keeper");
		stockService.adjust(code, 2, "실사 결과 추가", "keeper");
		stockService.adjust(code, -4, "파손", "keeper");
		assertEquals(3, stock(code));

		assertThrows(AssetItemException.class, () -> stockService.adjust(code, -1, " ", "keeper"));
		assertThrows(AssetItemException.class, () -> stockService.adjust(code, -1, null, "keeper"));
		assertThrows(AssetItemException.class, () -> stockService.adjust(code, 0, "없음", "keeper"));
		AssetItemException ex = assertThrows(AssetItemException.class, () -> stockService.adjust(code, -4, "과다", "keeper"));
		assertTrue(ex.getMessage().contains("0 미만"), ex.getMessage());
		assertEquals(3, stock(code));

		List<Object[]> h = hist(code);
		assertEquals(3, h.size());
		assertHist(h.get(1), "ADJUST", 2, 7);
		assertHist(h.get(2), "ADJUST", -4, 3);
		assertEquals("파손", h.get(2)[4]);
	}

	@Test
	void availabilityForQtyAndUnit() {
		String qty = newItem("QTY");
		stockService.receive(qty, 4, null, "keeper");
		assertEquals(4, stockService.getAvailableQty(qty));

		String unit = newItem("UNIT");
		addUnits(unit, 3, "STORED");
		addUnits(unit, 1, "IN_USE");
		addUnits(unit, 1, "REPAIR");
		assertEquals(3, stockService.getAvailableQty(unit));
		stockService.reserve(unit, 2, newReq(), "admin1");
		assertEquals(1, stockService.getAvailableQty(unit));

		assertEquals(Map.of(qty, 4, unit, 1), stockService.getAvailableQtys(List.of(qty, unit)));

		Map<String, AssetItemVO> locked = stockService.lockItems(List.of(unit, qty));
		assertEquals(List.of(qty, unit).stream().sorted().toList(), new ArrayList<>(locked.keySet()));
		AssetItemVO u = locked.get(unit);
		assertEquals("UNIT", u.getItemTy());
		assertEquals(3, u.getStoredQty());
		assertEquals(2, u.getReservedQty());
		assertEquals(1, u.getAvailQty());
		assertEquals(4, locked.get(qty).getAvailQty());

		assertThrows(AssetItemException.class, () -> stockService.lockItems(List.of(qty, "ITEM_NOT_EXISTS_0000")));
	}

	@Test
	void reserveRefusesShortageAndChangesNothing() {
		String qty = newItem("QTY");
		stockService.receive(qty, 2, null, "keeper");
		String req = newReq();
		AssetItemException ex = assertThrows(AssetItemException.class, () -> stockService.reserve(qty, 3, req, "admin1"));
		assertTrue(ex.getMessage().contains("부족 1"), ex.getMessage());
		assertEquals(2, stock(qty));
		assertEquals(1, hist(qty).size());

		String unit = newItem("UNIT");
		addUnits(unit, 1, "STORED");
		assertThrows(AssetItemException.class, () -> stockService.reserve(unit, 2, req, "admin1"));
		assertEquals(0, reserved(unit));
		assertEquals(0, hist(unit).size());
	}

	@Test
	void qtyReserveAndReleaseHistory() {
		String code = newItem("QTY");
		String reqNo = newReq();
		stockService.receive(code, 10, null, "keeper");
		stockService.reserve(code, 4, reqNo, "admin1");
		assertEquals(6, stock(code));
		stockService.release(code, 4, reqNo, "keeper", "지급 취소");
		assertEquals(10, stock(code));

		List<Object[]> h = hist(code);
		assertHist(h.get(1), "RESERVE", -4, 6);
		assertEquals(reqNo, h.get(1)[3]);
		assertHist(h.get(2), "RELEASE", 4, 10);
		assertEquals("지급 취소", h.get(2)[4]);
	}

	@Test
	void reserveWorksForDeactivatedItem() {
		String code = newItem("QTY");
		stockService.receive(code, 3, null, "keeper");
		itemService.deactivateItem(code, "keeper");
		stockService.reserve(code, 3, newReq(), "admin1");
		assertEquals(0, stock(code));
	}

	@Test
	void unitReserveReleaseDeliverHistory() {
		String code = newItem("UNIT");
		String reqNo = newReq();
		addUnits(code, 3, "STORED");
		stockService.reserve(code, 2, reqNo, "admin1");
		assertEquals(2, reserved(code));
		stockService.release(code, 1, reqNo, "keeper", null);
		assertEquals(1, reserved(code));

		// 지급: 호출자가 자산 1대를 IN_USE로 바꾼 뒤 deliverUnits
		jdbc().update("UPDATE ASSET_UNIT SET STTUS = 'IN_USE' WHERE ITEM_CODE = ? AND STTUS = 'STORED' LIMIT 1", code);
		stockService.deliverUnits(code, 1, reqNo, "keeper");
		assertEquals(0, reserved(code));

		List<Object[]> h = hist(code);
		assertEquals(3, h.size());
		assertHist(h.get(0), "RESERVE", -2, 1);
		assertHist(h.get(1), "RELEASE", 1, 2);
		assertHist(h.get(2), "DELIVER", 0, 2);
		assertEquals(reqNo, h.get(2)[3]);

		// 확보보다 많이 해제·지급할 수 없다
		assertThrows(AssetItemException.class, () -> stockService.release(code, 1, reqNo, "keeper", null));
		assertThrows(AssetItemException.class, () -> stockService.deliverUnits(code, 1, reqNo, "keeper"));
		assertEquals(0, reserved(code));
		assertEquals(3, hist(code).size());
	}

	@Test
	void recordUnitInAddsOnePerAsset() {
		String code = newItem("UNIT");
		addUnits(code, 1, "STORED");
		stockService.recordUnitIn(code, "NB-T2-1", "keeper");
		addUnits(code, 1, "STORED");
		stockService.recordUnitIn(code, "NB-T2-2", "keeper");
		List<Object[]> h = hist(code);
		assertHist(h.get(0), "IN", 1, 1);
		assertHist(h.get(1), "IN", 1, 2);
		assertTrue(((String) h.get(1)[4]).contains("NB-T2-2"));
		String qty = newItem("QTY");
		assertThrows(AssetItemException.class, () -> stockService.recordUnitIn(qty, "X", "keeper"));
	}

	@Test
	void historyListFiltersByItem() {
		String code = newItem("QTY");
		stockService.receive(code, 1, null, "keeper");
		stockService.receive(code, 2, null, "keeper");
		AssetStockHistVO search = new AssetStockHistVO();
		search.setItemCode(code);
		search.setFirstIndex(0);
		search.setRecordCountPerPage(10);
		assertEquals(2, stockService.selectHistListTotCnt(search));
		List<AssetStockHistVO> list = stockService.selectHistList(search);
		assertEquals(2, list.get(0).getChangeQty()); // 최신순
		assertEquals(code, list.get(0).getItemCode());
	}

	@Test
	void lockedAvailabilityIsNotStaleInsideCallerTransaction() {
		String code = newItem("UNIT");
		addUnits(code, 2, "STORED");
		String reqNo = newReq();
		TransactionTemplate tx = new TransactionTemplate(txManager);
		assertThrows(AssetItemException.class, () -> tx.executeWithoutResult(status -> {
			// 호출자가 먼저 일반 조회를 해 스냅숏이 생긴 뒤
			assertEquals(2, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_UNIT WHERE ITEM_CODE = ? AND STTUS = 'STORED'",
					Integer.class, code));
			// 다른 트랜잭션이 보관 자산 1대를 수리로 돌리고 커밋하면
			Thread other = new Thread(() -> jdbc().update("UPDATE ASSET_UNIT SET STTUS = 'REPAIR' WHERE ITEM_CODE = ? LIMIT 1", code));
			other.start();
			try {
				other.join();
			} catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
			// 합류한 확보는 최신 보관 수(1)로 판단해 2대 확보를 거부한다
			stockService.reserve(code, 2, reqNo, "admin1");
		}));
		assertEquals(0, reserved(code));
	}

	@Test
	void concurrentReservesNeverOversell() throws Exception {
		String code = newItem("QTY");
		stockService.receive(code, 5, null, "keeper");
		List<String> reqs = List.of(newReq(), newReq());

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		int ok = 0;
		int refused = 0;
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (String reqNo : reqs) {
				futures.add(pool.submit(() -> {
					start.await();
					stockService.reserve(code, 3, reqNo, "admin1");
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
		assertEquals(2, stock(code));
		assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_STOCK_HIST WHERE ITEM_CODE = ? AND HIST_TY = 'RESERVE'",
				Integer.class, code));
	}
}
