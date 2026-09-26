package egovframework.asset.req;

import static egovframework.asset.req.AssetReqFixture.line;
import static egovframework.asset.req.AssetReqFixture.no;
import static egovframework.asset.req.AssetReqFixture.ok;
import static egovframework.asset.req.AssetReqFixture.req;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.depthead.service.DeptHeadService;
import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.req.service.AssetReqLineVO;
import egovframework.asset.req.service.AssetReqService;
import egovframework.asset.req.service.AssetReqVO;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.asset.unit.service.AssetUnitService;
import jakarta.annotation.Resource;

/**
 * 신청·승인·지급·취소 흐름(명세 §3, §7, §8, §14 #1~#6, #8).
 * 부서·사용자·부서장은 테스트마다 만들고 지운다. 자산담당자는 시드 keeper를 쓴다.
 */
class AssetReqTest extends AssetIntegrationSupport {

	private static final String KEEPER = "keeper";
	private static final String ALREADY = "이미 처리된 신청입니다.";

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "assetReqService")
	private AssetReqService reqService;

	@Resource(name = "assetItemService")
	private AssetItemService itemService;

	@Resource(name = "assetStockService")
	private AssetStockService stockService;

	@Resource(name = "assetUnitService")
	private AssetUnitService unitService;

	@Resource(name = "deptHeadService")
	private DeptHeadService deptHeadService;

	private AssetReqFixture f;

	@BeforeEach
	void setUp() {
		f = new AssetReqFixture(new JdbcTemplate(dataSource), itemService, stockService, unitService);
	}

	@AfterEach
	void cleanUp() {
		f.cleanUp();
	}

	private static Map<String, List<String>> picks(Object... kv) {
		Map<String, List<String>> m = new java.util.HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			@SuppressWarnings("unchecked")
			List<String> v = (List<String>) kv[i + 1];
			m.put((String) kv[i], v);
		}
		return m;
	}

	private static List<Object> row(Object... values) {
		return Arrays.asList(values);
	}

	private void assertRefused(String expectedPart, org.junit.jupiter.api.function.Executable call) {
		AssetItemException e = assertThrows(AssetItemException.class, call);
		assertTrue(e.getMessage().contains(expectedPart), e.getMessage());
	}

	// ---------- §14 #1 수량 비품: 제출(중복 합산) → 품목별 승인·감량·반려 → 지급 ----------

	@Test
	void qtyRequestApprovePerLineAndDeliver() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q1 = f.qtyItem(10);
		String q2 = f.qtyItem(5);
		String q3 = f.qtyItem(7);

		// 같은 품목 두 줄은 합산, 빈 줄은 무시
		String reqNo = reqService.submit(req("업무용", line(q1, 4), line(q2, 3), line(null, null), line(q3, 2), line(q1, 1)), applicant);

		assertEquals(AssetReqVO.PENDING, f.sttus(reqNo));
		assertEquals(d, f.jdbc.queryForObject("SELECT ORGNZT_ID FROM ASSET_REQ WHERE REQ_NO = ?", String.class, reqNo));
		assertEquals(List.of(row(q1, 5, null, "PENDING", null), row(q2, 3, null, "PENDING", null), row(q3, 2, null, "PENDING", null)),
				f.lines(reqNo));
		assertEquals(List.of(Arrays.asList(null, "PENDING", applicant, null)), f.hist(reqNo));
		assertEquals(10, f.stock(q1));

		reqService.approve(reqNo, List.of(ok(q1, 3), no(q2, "이번 분기 배정 초과"), ok(q3, 2)), head);

		assertEquals(AssetReqVO.APPROVED, f.sttus(reqNo));
		assertEquals(head, f.jdbc.queryForObject("SELECT APRV_ID FROM ASSET_REQ WHERE REQ_NO = ?", String.class, reqNo));
		assertEquals(List.of(row(q1, 5, 3, "APPROVED", null), row(q2, 3, null, "REJECTED", "이번 분기 배정 초과"), row(q3, 2, 2, "APPROVED", null)),
				f.lines(reqNo));
		assertEquals(7, f.stock(q1));
		assertEquals(5, f.stock(q2));
		assertEquals(5, f.stock(q3));
		assertEquals(List.of(List.of(q1, "RESERVE", -3, 7), List.of(q3, "RESERVE", -2, 5)), f.stockHist(reqNo));

		reqService.deliver(reqNo, Map.of(), KEEPER);

		assertEquals(AssetReqVO.DELIVERED, f.sttus(reqNo));
		assertEquals(7, f.stock(q1));
		assertEquals(5, f.stock(q3));
		assertEquals(2, f.stockHist(reqNo).size());
		assertEquals(List.of(Arrays.asList(null, "PENDING", applicant, null), Arrays.asList("PENDING", "APPROVED", head, null),
				Arrays.asList("APPROVED", "DELIVERED", KEEPER, null)), f.hist(reqNo));

		// 두 번째 지급·승인·지급취소는 거부, 아무것도 바뀌지 않는다
		assertRefused(ALREADY, () -> reqService.deliver(reqNo, Map.of(), KEEPER));
		assertRefused(ALREADY, () -> reqService.approve(reqNo, List.of(ok(q1, 3), no(q2, "x"), ok(q3, 2)), head));
		assertRefused(ALREADY, () -> reqService.cancelDelivery(reqNo, "사유", KEEPER));
		assertEquals(3, f.hist(reqNo).size());
		assertEquals(7, f.stock(q1));
	}

	// ---------- §14 #2 개별 자산: 확보 → 자산 골라 지급, 개수 틀리면 거부 ----------

	@Test
	void unitRequestReserveAndDeliverChosenAssets() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String u = f.unitItem(3);
		List<String> assets = f.assetNos(u);

		String reqNo = reqService.submit(req(null, line(u, 2)), applicant);
		reqService.approve(reqNo, List.of(ok(u, 2)), head);

		assertEquals(2, f.reserved(u));
		assertEquals(1, stockService.getAvailableQty(u));
		assertEquals(List.of(List.of(u, "RESERVE", -2, 1)), f.stockHist(reqNo));

		// 승인 2대인데 1대·3대를 고르면 거부, 아무것도 바뀌지 않는다
		assertRefused("승인 수량", () -> reqService.deliver(reqNo, picks(u, assets.subList(0, 1)), KEEPER));
		assertRefused("승인 수량", () -> reqService.deliver(reqNo, picks(u, assets), KEEPER));
		assertRefused("승인 수량", () -> reqService.deliver(reqNo, Map.of(), KEEPER));
		// 승인되지 않은 품목의 자산을 고르면 거부
		String other = f.unitItem(1);
		assertRefused("", () -> reqService.deliver(reqNo, picks(u, assets.subList(0, 2), other, f.assetNos(other)), KEEPER));
		assertEquals(AssetReqVO.APPROVED, f.sttus(reqNo));
		assertEquals(2, f.reserved(u));
		assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM ASSET_UNIT WHERE ITEM_CODE = ? AND STTUS = 'IN_USE'", Integer.class, u));

		reqService.deliver(reqNo, picks(u, assets.subList(0, 2)), KEEPER);

		for (String a : assets.subList(0, 2)) {
			assertEquals("IN_USE", f.jdbc.queryForObject("SELECT STTUS FROM ASSET_UNIT WHERE ASSET_NO = ?", String.class, a));
			assertEquals(applicant, f.jdbc.queryForObject("SELECT USER_ID FROM ASSET_UNIT WHERE ASSET_NO = ?", String.class, a));
			assertEquals(reqNo, f.jdbc.queryForObject(
					"SELECT REQ_NO FROM ASSET_UNIT_HIST WHERE ASSET_NO = ? AND AFTER_STTUS = 'IN_USE'", String.class, a));
		}
		assertEquals("STORED", f.jdbc.queryForObject("SELECT STTUS FROM ASSET_UNIT WHERE ASSET_NO = ?", String.class, assets.get(2)));
		assertEquals(0, f.reserved(u));
		assertEquals(1, stockService.getAvailableQty(u));
		assertEquals(List.of(List.of(u, "RESERVE", -2, 1), List.of(u, "DELIVER", 0, 1)), f.stockHist(reqNo));
		assertEquals(AssetReqVO.DELIVERED, f.sttus(reqNo));
		assertEquals(Arrays.asList("APPROVED", "DELIVERED", KEEPER, null), f.hist(reqNo).get(2));
	}

	// ---------- §14 #3 한 품목이라도 모자라면 아무것도 바뀌지 않고, 모자란 품목 전부를 알린다 ----------

	@Test
	void approvalWithShortageChangesNothingAndListsAllShortItems() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q1 = f.qtyItem(2);
		String q2 = f.qtyItem(1);
		String q3 = f.qtyItem(10);
		String u = f.unitItem(1);
		String reqNo = reqService.submit(req(null, line(q1, 3), line(q2, 4), line(q3, 1), line(u, 2)), applicant);
		String q1Nm = itemService.selectItem(q1).getItemNm();
		String q2Nm = itemService.selectItem(q2).getItemNm();
		String q3Nm = itemService.selectItem(q3).getItemNm();
		String uNm = itemService.selectItem(u).getItemNm();

		AssetItemException e = assertThrows(AssetItemException.class,
				() -> reqService.approve(reqNo, List.of(ok(q1, 3), ok(q2, 4), ok(q3, 1), ok(u, 2)), head));

		String msg = e.getMessage();
		assertTrue(msg.contains(q1Nm + ": 가용 2, 승인 3 → 1개 부족"), msg);
		assertTrue(msg.contains(q2Nm + ": 가용 1, 승인 4 → 3개 부족"), msg);
		assertTrue(msg.contains(uNm + ": 가용 1, 승인 2 → 1개 부족"), msg);
		assertFalse(msg.contains(q3Nm), msg);
		assertEquals(AssetReqVO.PENDING, f.sttus(reqNo));
		assertNull(f.jdbc.queryForObject("SELECT APRV_ID FROM ASSET_REQ WHERE REQ_NO = ?", String.class, reqNo));
		for (List<Object> l : f.lines(reqNo)) {
			assertEquals("PENDING", l.get(3));
			assertNull(l.get(2));
		}
		assertEquals(2, f.stock(q1));
		assertEquals(1, f.stock(q2));
		assertEquals(10, f.stock(q3));
		assertEquals(0, f.reserved(u));
		assertEquals(0, f.stockHist(reqNo).size());
		assertEquals(1, f.hist(reqNo).size());
	}

	// ---------- §14 #4 같은 품목 두 승인이 동시에: 하나만 성공, 가용 0 이상 ----------

	@Test
	void concurrentApprovalsNeverOverdrawStock() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(5);
		String r1 = reqService.submit(req(null, line(q, 3)), f.user(d, "ROLE_USER"));
		String r2 = reqService.submit(req(null, line(q, 3)), f.user(d, "ROLE_USER"));

		List<Outcome> out = race(() -> {
			reqService.approve(r1, List.of(ok(q, 3)), head);
			return null;
		}, () -> {
			reqService.approve(r2, List.of(ok(q, 3)), head);
			return null;
		});

		assertEquals(1, out.stream().filter(o -> o.error == null).count(), out.toString());
		assertEquals(2, f.stock(q));
		assertEquals(1, List.of(f.sttus(r1), f.sttus(r2)).stream().filter(AssetReqVO.APPROVED::equals).count());
		assertEquals(1, List.of(f.sttus(r1), f.sttus(r2)).stream().filter(AssetReqVO.PENDING::equals).count());
	}

	// ---------- §14 #5 승인과 신청취소가 동시에: 하나만 성공, 늦은 쪽은 "이미 처리된 신청입니다" ----------

	@Test
	void approveAndCancelRaceExactlyOneWins() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(100);
		for (int round = 0; round < 5; round++) {
			String reqNo = reqService.submit(req(null, line(q, 1)), applicant);
			int before = f.stock(q);

			List<Outcome> out = race(() -> {
				reqService.approve(reqNo, List.of(ok(q, 1)), head);
				return null;
			}, () -> {
				reqService.cancel(reqNo, applicant);
				return null;
			});

			List<Outcome> failed = out.stream().filter(o -> o.error != null).toList();
			assertEquals(1, failed.size(), out.toString());
			assertTrue(failed.get(0).error instanceof AssetItemException, out.toString());
			assertEquals(ALREADY, failed.get(0).error.getMessage());
			if (AssetReqVO.APPROVED.equals(f.sttus(reqNo))) {
				assertEquals(before - 1, f.stock(q));
			} else {
				assertEquals(AssetReqVO.CANCELED, f.sttus(reqNo));
				assertEquals(before, f.stock(q));
			}
			assertEquals(2, f.hist(reqNo).size());
		}
	}

	// ---------- §14 #6 자기 승인 금지, 승인자 결정 ----------

	@Test
	void selfApprovalRefusedOnApproveAndRejectPaths() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(10);
		// 부서장 본인의 신청 → 승인자는 관리자들
		String reqNo = reqService.submit(req(null, line(q, 1)), head);
		assertRefused("자기 신청", () -> reqService.approve(reqNo, List.of(ok(q, 1)), head));
		assertRefused("자기 신청", () -> reqService.approve(reqNo, List.of(no(q, "반려")), head));

		// 관리자 본인의 신청(부서장 없는 부서) → 본인은 불가, 다른 관리자는 가능
		String d2 = f.dept();
		String adminA = f.user(d2, "ROLE_ADMIN");
		String adminB = f.user(d2, "ROLE_ADMIN");
		String r2 = reqService.submit(req(null, line(q, 1)), adminA);
		assertRefused("자기 신청", () -> reqService.approve(r2, List.of(ok(q, 1)), adminA));
		assertRefused("자기 신청", () -> reqService.approve(r2, List.of(no(q, "반려")), adminA));
		assertEquals(AssetReqVO.PENDING, f.sttus(r2));
		assertEquals(1, f.hist(r2).size());
		assertEquals(10, f.stock(q));

		reqService.approve(r2, List.of(ok(q, 1)), adminB);
		assertEquals(AssetReqVO.APPROVED, f.sttus(r2));
		// 부서장 본인 신청은 관리자가 처리
		reqService.approve(reqNo, List.of(no(q, "불필요")), adminB);
		assertEquals(AssetReqVO.REJECTED, f.sttus(reqNo));
	}

	@Test
	void approverIsDecidedAtApprovalTime() throws Exception {
		String d = f.dept();
		String other = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		String admin = f.user(other, "ROLE_ADMIN");
		String stranger = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(10);

		// 유효한 부서장이 있으면 관리자·다른 직원은 승인할 수 없다
		String r1 = reqService.submit(req(null, line(q, 1)), applicant);
		assertRefused("권한", () -> reqService.approve(r1, List.of(ok(q, 1)), admin));
		assertRefused("권한", () -> reqService.approve(r1, List.of(ok(q, 1)), stranger));

		// 부서장이 다른 부서로 옮기면 → 관리자가 승인, 옛 부서장은 불가
		f.move(head, other);
		assertRefused("권한", () -> reqService.approve(r1, List.of(ok(q, 1)), head));
		reqService.approve(r1, List.of(ok(q, 1)), admin);
		assertEquals(AssetReqVO.APPROVED, f.sttus(r1));

		// 부서장이 잠기면 → 관리자
		f.move(head, d);
		String r2 = reqService.submit(req(null, line(q, 1)), applicant);
		f.lock(head);
		assertRefused("권한", () -> reqService.approve(r2, List.of(ok(q, 1)), head));
		reqService.approve(r2, List.of(no(q, "재고 조정 중")), admin);
		assertEquals(AssetReqVO.REJECTED, f.sttus(r2));

		// 부서장 없는 부서 → 관리자
		String d3 = f.dept();
		String r3 = reqService.submit(req(null, line(q, 1)), f.user(d3, "ROLE_USER"));
		reqService.approve(r3, List.of(ok(q, 1)), admin);
		assertEquals(AssetReqVO.APPROVED, f.sttus(r3));
	}

	@Test
	void noApproverBlocksSubmitAndIsLabeledForAdmins() throws Exception {
		String d = f.dept();
		String applicant = f.user(d, "ROLE_USER");
		String adminA = f.user(d, "ROLE_ADMIN");
		String q = f.qtyItem(10);
		// 관리자 A 본인 신청: 제출 시점에는 다른 관리자들이 승인자
		String own = reqService.submit(req(null, line(q, 1)), adminA);

		List<String> others = new ArrayList<>(f.activeAdmins());
		others.remove(adminA);
		List<Map<String, Object>> saved = f.lockAdmins(others);
		try {
			// 부서장 없음 + 신청자 아닌 관리자 없음 → 신청자 A 말고는 승인할 사람이 없다
			assertRefused("승인할 사람이 없", () -> reqService.submit(req(null, line(q, 1)), adminA));
			assertEquals(1, f.reqCnt(adminA));

			// 제출 후 승인자가 없어진 대기 신청: 관리자 목록·대기함에 '승인자 없음', 본인 승인은 여전히 불가
			AssetReqVO inboxRow = find(reqService.selectReqList(AssetReqService.SCOPE_INBOX, all(), adminA), own);
			assertTrue(inboxRow != null && inboxRow.isNoApprover());
			assertTrue(find(reqService.selectReqList(AssetReqService.SCOPE_ALL, all(), adminA), own).isNoApprover());
			assertTrue(find(reqService.selectReqList(AssetReqService.SCOPE_ALL, all(), KEEPER), own).isNoApprover());
			assertRefused("자기 신청", () -> reqService.approve(own, List.of(ok(q, 1)), adminA));

			// 관리자도 모두 없으면 일반 직원 제출도 막힌다
			f.lock(adminA);
			assertRefused("승인할 사람이 없", () -> reqService.submit(req(null, line(q, 1)), applicant));
			assertEquals(0, f.reqCnt(applicant));
		} finally {
			f.restoreLocks(saved);
		}
		assertFalse(find(reqService.selectReqList(AssetReqService.SCOPE_ALL, all(), KEEPER), own).isNoApprover());
	}

	// ---------- §14 #8 지급취소: 확보분 복구, 사유 필수 ----------

	@Test
	void deliveryCancelRestoresReservationWithReleaseHistory() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		String admin = f.user(d, "ROLE_ADMIN");
		f.head(d, head);
		String q = f.qtyItem(10);
		String u = f.unitItem(2);
		String reqNo = reqService.submit(req(null, line(q, 4), line(u, 1)), applicant);
		reqService.approve(reqNo, List.of(ok(q, 4), ok(u, 1)), head);
		assertEquals(6, f.stock(q));
		assertEquals(1, f.reserved(u));

		assertRefused("사유", () -> reqService.cancelDelivery(reqNo, " ", KEEPER));
		assertRefused("자산담당자", () -> reqService.cancelDelivery(reqNo, "사유", head));
		assertRefused("자산담당자", () -> reqService.cancelDelivery(reqNo, "사유", admin));
		assertRefused("자산담당자", () -> reqService.deliver(reqNo, picks(u, f.assetNos(u).subList(0, 1)), admin));
		assertEquals(AssetReqVO.APPROVED, f.sttus(reqNo));

		reqService.cancelDelivery(reqNo, "신청자 퇴직", KEEPER);

		assertEquals(10, f.stock(q));
		assertEquals(0, f.reserved(u));
		assertEquals(AssetReqVO.DELIVERY_CANCELED, f.sttus(reqNo));
		List<List<Object>> sh = f.stockHist(reqNo);
		assertEquals(4, sh.size());
		assertTrue(sh.contains(List.of(q, "RELEASE", 4, 10)), sh.toString());
		assertTrue(sh.contains(List.of(u, "RELEASE", 1, 2)), sh.toString());
		assertEquals(Arrays.asList("APPROVED", "DELIVERY_CANCELED", KEEPER, "신청자 퇴직"), f.hist(reqNo).get(2));
		assertRefused(ALREADY, () -> reqService.deliver(reqNo, picks(u, f.assetNos(u).subList(0, 1)), KEEPER));
		assertRefused(ALREADY, () -> reqService.cancelDelivery(reqNo, "사유", KEEPER));
	}

	// ---------- 승인 입력 규칙, 모두 반려 ----------

	@Test
	void approvalInputRulesAndAllRejected() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q1 = f.qtyItem(10);
		String q2 = f.qtyItem(10);
		String reqNo = reqService.submit(req(null, line(q1, 3), line(q2, 2)), applicant);

		assertRefused("미결", () -> reqService.approve(reqNo, List.of(ok(q1, 3)), head));
		assertRefused("미결", () -> reqService.approve(reqNo, List.of(ok(q1, 3), line(q2, null)), head));
		assertRefused("승인 수량", () -> reqService.approve(reqNo, List.of(ok(q1, 4), ok(q2, 2)), head));
		assertRefused("승인 수량", () -> reqService.approve(reqNo, List.of(ok(q1, 0), ok(q2, 2)), head));
		assertRefused("승인 수량", () -> reqService.approve(reqNo, List.of(ok(q1, null), ok(q2, 2)), head));
		assertRefused("반려 사유", () -> reqService.approve(reqNo, List.of(ok(q1, 3), no(q2, " ")), head));
		assertEquals(1, f.hist(reqNo).size());

		reqService.approve(reqNo, List.of(no(q1, "예산 없음"), no(q2, "중복 신청")), head);

		assertEquals(AssetReqVO.REJECTED, f.sttus(reqNo));
		assertEquals(head, f.jdbc.queryForObject("SELECT APRV_ID FROM ASSET_REQ WHERE REQ_NO = ?", String.class, reqNo));
		assertEquals(List.of(row(q1, 3, null, "REJECTED", "예산 없음"), row(q2, 2, null, "REJECTED", "중복 신청")), f.lines(reqNo));
		assertEquals(10, f.stock(q1));
		assertEquals(0, f.stockHist(reqNo).size());
		assertEquals(Arrays.asList("PENDING", "REJECTED", head, null), f.hist(reqNo).get(1));
		assertRefused(ALREADY, () -> reqService.cancel(reqNo, applicant));
	}

	// ---------- 제출 규칙 ----------

	@Test
	void submitRules() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(1);
		String off = f.qtyItem(5);
		itemService.deactivateItem(off, KEEPER);

		assertRefused("품목", () -> reqService.submit(req(null), applicant));
		assertRefused("품목", () -> reqService.submit(req(null, line(null, null)), applicant));
		assertRefused("수량", () -> reqService.submit(req(null, line(q, 0)), applicant));
		assertRefused("수량", () -> reqService.submit(req(null, line(q, null)), applicant));
		assertRefused("품목", () -> reqService.submit(req(null, line(null, 2)), applicant));
		assertRefused("사용 중지", () -> reqService.submit(req(null, line(q, 1), line(off, 1)), applicant));
		assertRefused("품목이 없", () -> reqService.submit(req(null, line("ITEM_NOT_EXISTING_01", 1)), applicant));
		assertRefused("직원", () -> reqService.submit(req(null, line(q, 1)), "no-such-user-t5"));
		String deptless = f.user(d, "ROLE_USER");
		f.jdbc.update("UPDATE COMTNEMPLYRINFO SET ORGNZT_ID = NULL WHERE EMPLYR_ID = ?", deptless);
		// (부서 행이 지워지면 FK가 ORGNZT_ID를 NULL로 만든다 — 같은 경우)
		assertRefused("부서", () -> reqService.submit(req(null, line(q, 1)), deptless));
		assertEquals(0, f.reqCnt(applicant));

		// 가용보다 많아도 제출된다(재고 확인은 승인 때)
		String reqNo = reqService.submit(req("많이", line(q, 9)), applicant);
		assertEquals(AssetReqVO.PENDING, f.sttus(reqNo));
	}

	@Test
	void deactivatedItemStillApprovedAndDeliveredForExistingRequest() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(5);
		String u = f.unitItem(1);
		String reqNo = reqService.submit(req(null, line(q, 2), line(u, 1)), applicant);
		itemService.deactivateItem(q, KEEPER);
		itemService.deactivateItem(u, KEEPER);

		reqService.approve(reqNo, List.of(ok(q, 2), ok(u, 1)), head);
		reqService.deliver(reqNo, picks(u, f.assetNos(u)), KEEPER);

		assertEquals(AssetReqVO.DELIVERED, f.sttus(reqNo));
		assertEquals(3, f.stock(q));
		assertEquals(0, f.reserved(u));
	}

	@Test
	void cancelRules() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(5);
		String reqNo = reqService.submit(req(null, line(q, 2)), applicant);

		assertRefused("본인", () -> reqService.cancel(reqNo, head));
		assertRefused("승인된 신청", () -> reqService.deliver(reqNo, Map.of(), KEEPER));
		reqService.cancel(reqNo, applicant);

		assertEquals(AssetReqVO.CANCELED, f.sttus(reqNo));
		assertEquals(Arrays.asList("PENDING", "CANCELED", applicant, null), f.hist(reqNo).get(1));
		assertEquals(5, f.stock(q));
		assertRefused(ALREADY, () -> reqService.approve(reqNo, List.of(ok(q, 2)), head));
		assertRefused(ALREADY, () -> reqService.cancel(reqNo, applicant));

		// 승인된 신청은 신청자가 취소할 수 없다
		String r2 = reqService.submit(req(null, line(q, 1)), applicant);
		reqService.approve(r2, List.of(ok(q, 1)), head);
		assertRefused(ALREADY, () -> reqService.cancel(r2, applicant));
	}

	// ---------- 조회 범위 ----------

	@Test
	void viewScopes() throws Exception {
		String d = f.dept();
		String d2 = f.dept();
		String head = f.user(d, "ROLE_USER");
		String head2 = f.user(d2, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		String colleague = f.user(d, "ROLE_USER");
		String admin = f.user(d2, "ROLE_ADMIN");
		f.head(d, head);
		f.head(d2, head2);
		String q = f.qtyItem(10);
		String mine = reqService.submit(req(null, line(q, 1)), applicant);
		String theirs = reqService.submit(req(null, line(q, 1)), colleague);

		// 신청자: 자기 신청만
		List<String> my = reqNos(reqService.selectReqList(AssetReqService.SCOPE_MY, all(), applicant));
		assertTrue(my.contains(mine));
		assertFalse(my.contains(theirs));
		assertEquals(my.size(), reqService.selectReqListTotCnt(AssetReqService.SCOPE_MY, all(), applicant));
		AssetReqVO detail = reqService.selectReqDetail(mine, applicant);
		assertEquals(1, detail.getLines().size());
		assertEquals(1, detail.getHistList().size());
		assertTrue(detail.isCancelable());
		assertFalse(detail.isApprovable());
		assertEquals(List.of(head), detail.getApproverIds());
		assertRefused("권한", () -> reqService.selectReqDetail(theirs, applicant));
		// 화면 입력으로 범위를 넓힐 수 없다
		AssetReqVO forged = all();
		forged.setScopeApplcntId(colleague);
		assertFalse(reqNos(reqService.selectReqList(AssetReqService.SCOPE_MY, forged, applicant)).contains(theirs));

		// 부서장: 신청 당시 부서 기준. 신청자가 부서를 옮겨도 그대로
		f.move(applicant, d2);
		List<String> dept = reqNos(reqService.selectReqList(AssetReqService.SCOPE_DEPT, all(), head));
		assertTrue(dept.contains(mine) && dept.contains(theirs));
		assertTrue(reqService.selectReqDetail(mine, head).isApprovable());
		assertFalse(reqNos(reqService.selectReqList(AssetReqService.SCOPE_DEPT, all(), head2)).contains(mine));
		assertRefused("권한", () -> reqService.selectReqDetail(mine, head2));
		assertRefused("부서장이 아닙니다", () -> reqService.selectReqList(AssetReqService.SCOPE_DEPT, all(), colleague));

		// 자산담당자·관리자: 전체
		assertTrue(reqNos(reqService.selectReqList(AssetReqService.SCOPE_ALL, all(), KEEPER)).contains(mine));
		assertTrue(reqNos(reqService.selectReqList(AssetReqService.SCOPE_ALL, all(), admin)).contains(mine));
		assertRefused("권한", () -> reqService.selectReqList(AssetReqService.SCOPE_ALL, all(), applicant));
		assertFalse(reqService.selectReqDetail(mine, admin).isApprovable());
		reqService.selectReqDetail(mine, KEEPER);

		// 상태 검색
		AssetReqVO pendingOnly = all();
		pendingOnly.setSearchSttus(AssetReqVO.APPROVED);
		assertFalse(reqNos(reqService.selectReqList(AssetReqService.SCOPE_ALL, pendingOnly, KEEPER)).contains(mine));
		reqService.approve(mine, List.of(ok(q, 1)), head);
		assertTrue(reqNos(reqService.selectReqList(AssetReqService.SCOPE_ALL, pendingOnly, KEEPER)).contains(mine));
		assertTrue(reqService.selectReqDetail(mine, KEEPER).isDeliverable());
		assertFalse(reqService.selectReqDetail(mine, admin).isDeliverable());
	}

	/** 승인 대기함 = 지금 승인자인 대기 신청(DeptHeadService.canApprove와 같은 판정) */
	@Test
	void inboxMatchesCurrentApprover() throws Exception {
		String dHead = f.dept();
		String dNoHead = f.dept();
		String dSelf = f.dept();
		String dMoved = f.dept();
		String elsewhere = f.dept();
		String head = f.user(dHead, "ROLE_USER");
		String selfHead = f.user(dSelf, "ROLE_USER");
		String movedHead = f.user(dMoved, "ROLE_USER");
		String admin = f.user(elsewhere, "ROLE_ADMIN");
		String keeperLike = f.user(elsewhere, "ROLE_ASSET");
		f.head(dHead, head);
		f.head(dSelf, selfHead);
		f.head(dMoved, movedHead);
		String q = f.qtyItem(10);

		List<String[]> reqs = new ArrayList<>();
		for (String[] p : new String[][] { { dHead, f.user(dHead, "ROLE_USER") }, { dHead, head }, { dNoHead, f.user(dNoHead, "ROLE_USER") },
				{ dSelf, selfHead }, { dMoved, f.user(dMoved, "ROLE_USER") }, { dNoHead, admin } }) {
			reqs.add(new String[] { reqService.submit(req(null, line(q, 1)), p[1]), p[0], p[1] });
		}
		f.move(movedHead, elsewhere);
		String approved = reqService.submit(req(null, line(q, 1)), f.user(dHead, "ROLE_USER"));
		reqService.approve(approved, List.of(ok(q, 1)), head);

		for (String viewer : List.of(head, selfHead, movedHead, admin, keeperLike)) {
			List<String> inbox = reqNos(reqService.selectReqList(AssetReqService.SCOPE_INBOX, all(), viewer));
			assertFalse(inbox.contains(approved), viewer);
			for (String[] r : reqs) {
				assertEquals(deptHeadService.canApprove(viewer, r[1], r[2]), inbox.contains(r[0]), viewer + " / " + r[2]);
			}
		}
		assertTrue(reqNos(reqService.selectReqList(AssetReqService.SCOPE_INBOX, all(), head)).contains(reqs.get(0)[0]));
		assertTrue(reqNos(reqService.selectReqList(AssetReqService.SCOPE_INBOX, all(), admin)).contains(reqs.get(1)[0]));
	}

	// ---------- 신청 내역 보고(엑셀 내려받기용) ----------

	@Test
	void reqLineReportForDateRange() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q1 = f.qtyItem(10);
		String q2 = f.qtyItem(10);
		String reqNo = reqService.submit(req(null, line(q1, 2), line(q2, 1)), applicant);
		reqService.approve(reqNo, List.of(ok(q1, 1), no(q2, "불필요")), head);
		// 신청 시각은 DB 시각(Asia/Seoul) 기준이므로 날짜도 DB에서 얻는다
		String today = f.jdbc.queryForObject("SELECT DATE_FORMAT(CURDATE(), '%Y-%m-%d')", String.class);

		List<AssetReqLineVO> rows = reqService.selectReqLineReport(today, today).stream().filter(r -> reqNo.equals(r.getReqNo())).toList();
		assertEquals(2, rows.size());
		AssetReqLineVO r1 = rows.get(0);
		assertEquals(q1, r1.getItemCode());
		assertEquals(applicant, r1.getApplcntId());
		assertEquals(d, r1.getOrgnztId());
		assertTrue(r1.getOrgnztNm().startsWith("T5부서"));
		assertEquals(2, r1.getReqQty());
		assertEquals(1, r1.getAprvQty());
		assertEquals("APPROVED", r1.getLineSttus());
		assertEquals(AssetReqVO.APPROVED, r1.getReqSttus());
		assertEquals(head, r1.getAprvId());
		assertTrue(r1.getReqPnttm() != null && r1.getLastProcessPnttm() != null && r1.getItemNm() != null);
		assertEquals("REJECTED", rows.get(1).getLineSttus());
		String yesterday = LocalDate.parse(today).minusDays(1).toString();
		assertTrue(reqService.selectReqLineReport(yesterday, yesterday).stream().noneMatch(r -> reqNo.equals(r.getReqNo())));
	}

	// ---------- 도우미 ----------

	private static AssetReqVO all() {
		AssetReqVO vo = new AssetReqVO();
		vo.setFirstIndex(0);
		vo.setRecordCountPerPage(100000);
		return vo;
	}

	private static List<String> reqNos(List<AssetReqVO> list) {
		return list.stream().map(AssetReqVO::getReqNo).toList();
	}

	private static AssetReqVO find(List<AssetReqVO> list, String reqNo) {
		return list.stream().filter(r -> reqNo.equals(r.getReqNo())).findFirst().orElse(null);
	}

	private record Outcome(Throwable error) {
	}

	/** 두 작업을 별도 스레드(각자 트랜잭션)에서 동시에 시작한다 */
	@SafeVarargs
	private static List<Outcome> race(Callable<Void>... tasks) throws InterruptedException {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Void>> futures = new ArrayList<>();
			for (Callable<Void> t : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					return t.call();
				}));
			}
			start.countDown();
			List<Outcome> out = new ArrayList<>();
			for (Future<Void> fu : futures) {
				try {
					fu.get(60, TimeUnit.SECONDS);
					out.add(new Outcome(null));
				} catch (ExecutionException e) {
					out.add(new Outcome(e.getCause()));
				} catch (java.util.concurrent.TimeoutException e) {
					throw new AssertionError("동시 처리 시간 초과", e);
				}
			}
			return out;
		} finally {
			pool.shutdownNow();
		}
	}
}
