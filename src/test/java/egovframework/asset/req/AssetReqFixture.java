package egovframework.asset.req;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;

import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.req.service.AssetReqLineVO;
import egovframework.asset.req.service.AssetReqVO;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.asset.unit.service.AssetUnitVO;

/**
 * 신청 흐름 테스트 픽스처: 부서·사용자·부서장·품목·자산을 고유 값으로 만들고 {@link #cleanUp()}에서 부서·사용자·부서장 행을 지운다.
 * (품목·신청·이력 행은 남는다 — 다른 테스트는 표 전체 건수를 보지 않는다)
 */
class AssetReqFixture {

	private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000_000L);

	final JdbcTemplate jdbc;
	private final AssetItemService itemService;
	private final AssetStockService stockService;
	private final AssetUnitService unitService;
	private final List<String> depts = new ArrayList<>();
	private final List<String> users = new ArrayList<>();

	AssetReqFixture(JdbcTemplate jdbc, AssetItemService itemService, AssetStockService stockService, AssetUnitService unitService) {
		this.jdbc = jdbc;
		this.itemService = itemService;
		this.stockService = stockService;
		this.unitService = unitService;
	}

	static long next() {
		return SEQ.incrementAndGet();
	}

	void cleanUp() {
		for (String d : depts) {
			jdbc.update("DELETE FROM ASSET_DEPT_HEAD WHERE ORGNZT_ID = ?", d);
		}
		for (String u : users) {
			jdbc.update("DELETE FROM ASSET_DEPT_HEAD WHERE EMPLYR_ID = ?", u);
			jdbc.update("DELETE FROM COMTNEMPLYRSCRTYESTBS WHERE SCRTY_DTRMN_TRGET_ID = (SELECT ESNTL_ID FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?)", u);
			jdbc.update("DELETE FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?", u);
		}
		for (String d : depts) {
			jdbc.update("DELETE FROM COMTNORGNZTINFO WHERE ORGNZT_ID = ?", d);
		}
	}

	String dept() {
		long n = next();
		String id = String.format("ORGT5%015d", n);
		jdbc.update("INSERT INTO COMTNORGNZTINFO (ORGNZT_ID, ORGNZT_NM, ORGNZT_DC) VALUES (?, ?, 'T5 테스트')", id, "T5부서" + (n % 1_000_000));
		depts.add(id);
		return id;
	}

	String user(String orgnztId, String authorCode) {
		long n = next();
		String id = String.format("t5u%012d", n);
		String esntl = String.format("ESNT5%015d", n);
		jdbc.update("INSERT INTO COMTNEMPLYRINFO (EMPLYR_ID, ORGNZT_ID, USER_NM, PASSWORD, HOUSE_ADRES, PASSWORD_HINT, PASSWORD_CNSR, "
				+ "HOUSE_END_TELNO, AREA_NO, HOUSE_MIDDLE_TELNO, ZIP, EMPLYR_STTUS_CODE, ESNTL_ID, SBSCRB_DE) "
				+ "VALUES (?, ?, 'T5사용자', '!test', '가상로', 'P01', '테스트', '0000', '02', '0000', '000000', 'P', ?, SYSDATE())",
				id, orgnztId, esntl);
		users.add(id);
		jdbc.update("INSERT INTO COMTNEMPLYRSCRTYESTBS (SCRTY_DTRMN_TRGET_ID, MBER_TY_CODE, AUTHOR_CODE) VALUES (?, 'USR03', ?)", esntl,
				authorCode);
		return id;
	}

	void head(String orgnztId, String emplyrId) {
		jdbc.update("INSERT INTO ASSET_DEPT_HEAD (ORGNZT_ID, EMPLYR_ID, ASSIGN_ID, ASSIGN_PNTTM) VALUES (?, ?, 'admin1', SYSDATE())",
				orgnztId, emplyrId);
	}

	void move(String emplyrId, String orgnztId) {
		jdbc.update("UPDATE COMTNEMPLYRINFO SET ORGNZT_ID = ? WHERE EMPLYR_ID = ?", orgnztId, emplyrId);
	}

	void lock(String emplyrId) {
		jdbc.update("UPDATE COMTNEMPLYRINFO SET LOCK_AT = 'Y' WHERE EMPLYR_ID = ?", emplyrId);
	}

	/** 지금 로그인 가능한 관리자 전원(시드 포함) */
	List<String> activeAdmins() {
		return jdbc.queryForList("SELECT e.EMPLYR_ID FROM COMTNEMPLYRINFO e JOIN COMTNEMPLYRSCRTYESTBS r ON r.SCRTY_DTRMN_TRGET_ID = e.ESNTL_ID "
				+ "AND r.AUTHOR_CODE = 'ROLE_ADMIN' WHERE e.EMPLYR_STTUS_CODE = 'P' AND IFNULL(e.LOCK_AT, 'N') <> 'Y'", String.class);
	}

	/** 관리자들을 잠그고, 원래 LOCK_AT 값을 돌려준다(복구용) */
	List<Map<String, Object>> lockAdmins(List<String> ids) {
		List<Map<String, Object>> saved = new ArrayList<>();
		for (String id : ids) {
			saved.add(jdbc.queryForMap("SELECT EMPLYR_ID, LOCK_AT FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?", id));
			lock(id);
		}
		return saved;
	}

	void restoreLocks(List<Map<String, Object>> saved) {
		for (Map<String, Object> row : saved) {
			jdbc.update("UPDATE COMTNEMPLYRINFO SET LOCK_AT = ? WHERE EMPLYR_ID = ?", row.get("LOCK_AT"), row.get("EMPLYR_ID"));
		}
	}

	String qtyItem(int stock) {
		String code = item(AssetItemVO.TY_QTY);
		if (stock > 0) {
			stockService.receive(code, stock, "T5 초기", "keeper");
		}
		return code;
	}

	/** 개별 자산 품목과 보관 자산 n대. 자산번호는 {@link #assetNos} */
	String unitItem(int units) {
		String code = item(AssetItemVO.TY_UNIT);
		for (int i = 0; i < units; i++) {
			AssetUnitVO vo = new AssetUnitVO();
			vo.setAssetNo("T5-" + next());
			vo.setItemCode(code);
			unitService.registerUnit(vo, "keeper");
		}
		return code;
	}

	List<String> assetNos(String itemCode) {
		return jdbc.queryForList("SELECT ASSET_NO FROM ASSET_UNIT WHERE ITEM_CODE = ? ORDER BY ASSET_NO", String.class, itemCode);
	}

	private String item(String ty) {
		AssetItemVO vo = new AssetItemVO();
		vo.setItemNm("신청" + ty + "-" + next());
		vo.setItemTy(ty);
		vo.setClCode("OFFICE");
		vo.setUnitCode("EA");
		vo.setMinQty(0);
		return itemService.insertItem(vo, "keeper");
	}

	static AssetReqVO req(String resn, AssetReqLineVO... lines) {
		AssetReqVO vo = new AssetReqVO();
		vo.setReqResn(resn);
		vo.setLines(new ArrayList<>(List.of(lines)));
		return vo;
	}

	static AssetReqLineVO line(String itemCode, Integer qty) {
		AssetReqLineVO l = new AssetReqLineVO();
		l.setItemCode(itemCode);
		l.setReqQty(qty);
		return l;
	}

	static AssetReqLineVO ok(String itemCode, Integer aprvQty) {
		AssetReqLineVO l = new AssetReqLineVO();
		l.setItemCode(itemCode);
		l.setLineSttus(AssetReqLineVO.APPROVED);
		l.setAprvQty(aprvQty);
		return l;
	}

	static AssetReqLineVO no(String itemCode, String resn) {
		AssetReqLineVO l = new AssetReqLineVO();
		l.setItemCode(itemCode);
		l.setLineSttus(AssetReqLineVO.REJECTED);
		l.setRejectResn(resn);
		return l;
	}

	// ---------- 조회 ----------

	String sttus(String reqNo) {
		return jdbc.queryForObject("SELECT STTUS FROM ASSET_REQ WHERE REQ_NO = ?", String.class, reqNo);
	}

	int reqCnt(String applicantId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM ASSET_REQ WHERE APPLCNT_ID = ?", Integer.class, applicantId);
	}

	int stock(String itemCode) {
		return jdbc.queryForObject("SELECT STOCK_QTY FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, itemCode);
	}

	int reserved(String itemCode) {
		return jdbc.queryForObject("SELECT RESERVED_QTY FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, itemCode);
	}

	/** 품목 줄 [품목, 신청, 승인, 결과, 반려 사유] 품목코드순 */
	List<List<Object>> lines(String reqNo) {
		return jdbc.query("SELECT ITEM_CODE, REQ_QTY, APRV_QTY, LINE_STTUS, REJECT_RESN FROM ASSET_REQ_LINE WHERE REQ_NO = ? ORDER BY ITEM_CODE",
				(rs, n) -> {
					List<Object> row = new ArrayList<>();
					row.add(rs.getString(1));
					row.add(rs.getInt(2));
					row.add(rs.getObject(3) == null ? null : rs.getInt(3));
					row.add(rs.getString(4));
					row.add(rs.getString(5));
					return row;
				}, reqNo);
	}

	/** 처리 이력 [이전, 이후, 처리자, 사유] 처리 순 */
	List<List<String>> hist(String reqNo) {
		return jdbc.query("SELECT BEFORE_STTUS, AFTER_STTUS, PROCESS_ID, RESN FROM ASSET_REQ_HIST WHERE REQ_NO = ? ORDER BY REQ_HIST_ID",
				(rs, n) -> {
					List<String> row = new ArrayList<>();
					row.add(rs.getString(1));
					row.add(rs.getString(2));
					row.add(rs.getString(3));
					row.add(rs.getString(4));
					return row;
				}, reqNo);
	}

	/** 이 신청의 재고 변동 이력 [품목, 유형, 증감, 변동 후] 처리 순 */
	List<List<Object>> stockHist(String reqNo) {
		return jdbc.query("SELECT ITEM_CODE, HIST_TY, CHANGE_QTY, AFTER_QTY FROM ASSET_STOCK_HIST WHERE REQ_NO = ? ORDER BY STOCK_HIST_ID",
				(rs, n) -> List.<Object>of(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)), reqNo);
	}
}
