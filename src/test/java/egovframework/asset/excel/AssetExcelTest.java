package egovframework.asset.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.excel.service.AssetExcelResult;
import egovframework.asset.excel.service.AssetExcelService;
import egovframework.asset.excel.service.AssetExcelSheet;
import egovframework.asset.excel.web.AssetExcelController;
import egovframework.asset.excel.web.AssetExcelView;
import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.req.service.AssetReqLineVO;
import egovframework.asset.req.service.AssetReqService;
import egovframework.asset.req.service.AssetReqVO;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.asset.unit.service.AssetUnitVO;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.service.EgovUserDetailsService;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 엑셀 일괄등록(전부 반영 또는 전부 미반영, 틀린 줄 목록)과 내려받기 3종.
 */
class AssetExcelTest extends AssetIntegrationSupport {

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "assetExcelService")
	private AssetExcelService excelService;

	@Resource(name = "assetItemService")
	private AssetItemService itemService;

	@Resource(name = "assetUnitService")
	private AssetUnitService unitService;

	@Resource(name = "assetReqService")
	private AssetReqService reqService;

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private static String uniq(String prefix) {
		return prefix + System.nanoTime();
	}

	// ---------- 품목 일괄등록 ----------

	@Test
	void itemUploadSavesAllWithZeroStockAndGeneratedCodes() throws Exception {
		String a = uniq("엑셀품목A-");
		String b = uniq("엑셀품목B-");
		AssetExcelResult r = excelService.uploadItems(xlsx(
				row("품목명", "유형", "분류코드", "단위코드", "최소 수량"),
				row(a, "수량", "OFFICE", "EA", 3),
				row(b, "개별", "IT", "SET", null)), "keeper");

		assertTrue(r.isOk(), () -> "errors: " + describe(r));
		assertEquals(2, r.getSavedCnt());
		Map<String, Object> qa = item(a);
		assertTrue(((String) qa.get("ITEM_CODE")).matches("ITEM_\\d{15}"));
		assertEquals("QTY", qa.get("ITEM_TY"));
		assertEquals("OFFICE", qa.get("CL_CODE"));
		assertEquals("EA", qa.get("UNIT_CODE"));
		assertEquals(3, ((Number) qa.get("MIN_QTY")).intValue());
		assertEquals(0, ((Number) qa.get("STOCK_QTY")).intValue());
		assertEquals("Y", qa.get("USE_AT"));
		Map<String, Object> qb = item(b);
		assertEquals("UNIT", qb.get("ITEM_TY"));
		assertEquals(0, ((Number) qb.get("MIN_QTY")).intValue());
		assertEquals(0, ((Number) qb.get("STOCK_QTY")).intValue());
		assertEquals(0, ((Number) qb.get("RESERVED_QTY")).intValue());
		assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_STOCK_HIST WHERE ITEM_CODE IN (?, ?)", Integer.class,
				qa.get("ITEM_CODE"), qb.get("ITEM_CODE")));
	}

	@Test
	void itemUploadWithBadRowsSavesNothingAndListsEveryBadRow() throws Exception {
		String existing = uniq("엑셀기존-");
		newItem(existing, AssetItemVO.TY_QTY);
		String inactiveCode = "X" + (System.nanoTime() % 100_000_000_000L);
		jdbc().update("INSERT INTO COMTCCMMNDETAILCODE (CODE_ID, CODE, CODE_NM, CODE_DC, USE_AT, FRST_REGIST_PNTTM, FRST_REGISTER_ID) "
				+ "VALUES ('AST001', ?, '중지분류', '테스트', 'N', SYSDATE(), 'SYSTEM')", inactiveCode);
		String a = uniq("엑셀정상A-");
		String b = uniq("엑셀정상B-");
		try {
			AssetExcelResult r = excelService.uploadItems(xlsx(
					row("품목명", "유형", "분류코드", "단위코드", "최소 수량"),
					row(a, "수량", "OFFICE", "EA", 1), // 2행 정상
					row("", "수량", "OFFICE", "EA", 0), // 3행 품목명 누락
					row(uniq("엑셀c-"), "수량", "NOPE", "EA", 0), // 4행 분류코드 없음
					row(uniq("엑셀d-"), "박스", "OFFICE", "EA", 0), // 5행 유형 불일치
					row(existing, "수량", "OFFICE", "EA", 0), // 6행 사용 중 품목과 이름 중복
					row(a.toUpperCase(), "개별", "IT", "SET", 0), // 7행 파일 안 이름 중복(2행)
					row(uniq("엑셀e-"), "수량", "OFFICE", "EA", -1), // 8행 음수 최소 수량
					row(uniq("엑셀f-"), "수량", inactiveCode, "EA", 0), // 9행 사용 중지된 분류코드
					row(uniq("엑셀g-"), "수량", "OFFICE", "KG", "두개"), // 10행 단위코드 없음 + 최소 수량 숫자 아님
					row(null, null, null, null, null), // 11행 빈 줄(무시)
					row(b, null, "OFFICE", "EA", 0), // 12행 유형 누락
					row(null, null, null, null, null)), "keeper"); // 끝의 빈 줄(무시)

			assertEquals(0, r.getSavedCnt());
			assertEquals(List.of(
					"3: 품목명 누락",
					"4: 분류코드 없음: NOPE",
					"5: 유형은 수량 또는 개별로 입력하세요: 박스",
					"6: 품목명 중복: 사용 중인 품목에 같은 이름이 있습니다",
					"7: 품목명 중복: 파일의 2행과 같습니다",
					"8: 최소 수량은 0 이상의 정수로 입력하세요: -1",
					"9: 분류코드 없음: " + inactiveCode,
					"10: 단위코드 없음: KG",
					"10: 최소 수량은 0 이상의 정수로 입력하세요: 두개",
					"12: 유형 누락"), describe(r));
			assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_ITEM WHERE ITEM_NM IN (?, ?)", Integer.class, a, b));
		} finally {
			jdbc().update("DELETE FROM COMTCCMMNDETAILCODE WHERE CODE_ID = 'AST001' AND CODE = ?", inactiveCode);
		}
	}

	@Test
	void itemUploadRollsBackEarlierRowsWhenWritingFails() throws Exception {
		// 검사는 통과하지만(소문자 비교로는 다른 이름) DB 규칙(악센트 무시)으로는 같은 이름 → 저장 중 2행이 거부된다
		long n = System.nanoTime();
		String a = "엑셀café-" + n;
		String b = "엑셀cafe-" + n;
		assertThrows(AssetItemException.class, () -> excelService.uploadItems(xlsx(
				row("품목명", "유형", "분류코드", "단위코드", "최소 수량"),
				row(a, "수량", "OFFICE", "EA", 0),
				row(b, "수량", "OFFICE", "EA", 0)), "keeper"));
		assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_ITEM WHERE ITEM_NM IN (?, ?)", Integer.class, a, b));
	}

	@Test
	void itemUploadAcceptsXls() throws Exception {
		String a = uniq("엑셀xls-");
		AssetExcelResult r = excelService.uploadItems(xls(
				row("품목명", "유형", "분류코드", "단위코드", "최소 수량"),
				row(a, "수량", "SUPPLY", "BOX", 2)), "keeper");
		assertTrue(r.isOk(), () -> describe(r).toString());
		assertEquals(1, r.getSavedCnt());
		assertEquals(2, ((Number) item(a).get("MIN_QTY")).intValue());
	}

	@Test
	void uploadRejectsNonExcelAndEmptyFiles() {
		assertThrows(AssetItemException.class,
				() -> excelService.uploadItems(new ByteArrayInputStream("품목명,유형".getBytes()), "keeper"));
		assertThrows(AssetItemException.class,
				() -> excelService.uploadUnits(xlsx(row("품목코드", "자산번호", "비고")), "keeper"));
	}

	// ---------- 자산 일괄등록 ----------

	@Test
	void unitUploadStoresEveryAssetWithHistory() throws Exception {
		String u1 = newItem(uniq("엑셀자산1-"), AssetItemVO.TY_UNIT);
		String u2 = newItem(uniq("엑셀자산2-"), AssetItemVO.TY_UNIT);
		String n1 = uniq("T6-");
		String n2 = uniq("T6-");
		String n3 = uniq("T6-");
		// 코드 오름차순이 아닌 순서로 섞어 둔다(잠금은 서비스가 코드순으로 먼저 잡는다)
		AssetExcelResult r = excelService.uploadUnits(xlsx(
				row("품목코드", "자산번호", "비고"),
				row(u2, n1, "창고 A"),
				row(u1, n2, null),
				row(u2.toLowerCase(), n3, "")), "keeper");

		assertTrue(r.isOk(), () -> describe(r).toString());
		assertEquals(3, r.getSavedCnt());
		assertEquals(u2, unitCol(n1, "ITEM_CODE"));
		assertEquals("STORED", unitCol(n1, "STTUS"));
		assertEquals("창고 A", unitCol(n1, "RM"));
		assertEquals(u1, unitCol(n2, "ITEM_CODE"));
		assertEquals(u2, unitCol(n3, "ITEM_CODE"));
		for (String no : List.of(n1, n2, n3)) {
			assertEquals(1, jdbc().queryForObject(
					"SELECT COUNT(*) FROM ASSET_UNIT_HIST WHERE ASSET_NO = ? AND BEFORE_STTUS IS NULL AND AFTER_STTUS = 'STORED'", Integer.class, no));
		}
		assertEquals(List.of(1, 1), inHist(u2));
		assertEquals(List.of(1), inHist(u1));
	}

	@Test
	void unitUploadWithBadRowsSavesNothingAndListsEveryBadRow() throws Exception {
		String unit = newItem(uniq("엑셀자산-"), AssetItemVO.TY_UNIT);
		String qty = newItem(uniq("엑셀수량-"), AssetItemVO.TY_QTY);
		String stopped = newItem(uniq("엑셀중지-"), AssetItemVO.TY_UNIT);
		itemService.deactivateItem(stopped, "keeper");
		String disposed = uniq("T6D-");
		AssetUnitVO d = new AssetUnitVO();
		d.setAssetNo(disposed);
		d.setItemCode(unit);
		unitService.registerUnit(d, "keeper");
		unitService.changeStatus(disposed, AssetUnitVO.DISPOSED, "파손", "keeper");

		String good = uniq("T6G-");
		AssetExcelResult r = excelService.uploadUnits(xlsx(
				row("품목코드", "자산번호", "비고"),
				row(unit, good, "정상"), // 2행 정상
				row("ITEM_NOPE", uniq("T6-"), null), // 3행 품목코드 없음
				row(qty, uniq("T6-"), null), // 4행 수량 비품
				row(stopped, uniq("T6-"), null), // 5행 사용 중지 품목
				row(unit, disposed.toLowerCase(), null), // 6행 폐기 자산 번호와 중복
				row(unit, good, null), // 7행 파일 안 중복(2행)
				row(unit, null, "번호 없음"), // 8행 자산번호 누락
				row(null, uniq("T6-"), null)), "keeper"); // 9행 품목코드 누락

		assertEquals(0, r.getSavedCnt());
		assertEquals(List.of(
				"3: 품목코드 없음: ITEM_NOPE",
				"4: 유형 불일치: 개별 자산 품목이 아닙니다: " + qty,
				"5: 사용 중지된 품목입니다: " + stopped,
				"6: 자산번호 중복: 이미 등록된 번호입니다(폐기 포함)",
				"7: 자산번호 중복: 파일의 2행과 같습니다",
				"8: 자산번호 누락",
				"9: 품목코드 누락"), describe(r));
		assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_UNIT WHERE ASSET_NO = ?", Integer.class, good));
		assertEquals(List.of(1), inHist(unit)); // 폐기 자산 등록분만
	}

	// ---------- 내려받기 ----------

	@Test
	void stockDownloadHasSpecHeaderAndEveryItem() throws Exception {
		String unit = newItem(uniq("엑셀재고-"), AssetItemVO.TY_UNIT);
		for (int i = 0; i < 2; i++) {
			AssetUnitVO vo = new AssetUnitVO();
			vo.setAssetNo(uniq("T6S-"));
			vo.setItemCode(unit);
			unitService.registerUnit(vo, "keeper");
		}
		AssetExcelSheet sheet = excelService.stockSheet();
		assertEquals(List.of("품목코드", "품목명", "유형", "분류", "단위", "재고 또는 보관 수", "확보 수량", "가용", "최소 수량", "사용 여부"),
				sheet.getHeaders());
		assertEquals(jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_ITEM", Integer.class), sheet.getRows().size());
		List<Object> mine = find(sheet, 0, unit);
		assertEquals(List.of(unit, itemName(unit), "개별 자산", "정보기기", "대", 2, 0, 2, 0, "사용"), mine);

		// 실제 내려받기 파일: .xlsx, 한글 머리글, 행 수
		MockHttpServletResponse resp = render(sheet);
		assertTrue(resp.getHeader("Content-Disposition").contains(sheet.getFileName() + ".xlsx"));
		try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(resp.getContentAsByteArray()))) {
			Sheet s = wb.getSheetAt(0);
			assertEquals(sheet.getHeaders(), cells(s.getRow(0), 10));
			assertEquals(sheet.getRows().size(), s.getLastRowNum());
		}
	}

	@Test
	void unitLedgerDownloadHasSpecHeaderAndEveryAsset() throws Exception {
		String unit = newItem(uniq("엑셀대장-"), AssetItemVO.TY_UNIT);
		String no = uniq("T6L-");
		AssetUnitVO vo = new AssetUnitVO();
		vo.setAssetNo(no);
		vo.setItemCode(unit);
		vo.setRm("대장 비고");
		unitService.registerUnit(vo, "keeper");

		AssetExcelSheet sheet = excelService.unitSheet();
		assertEquals(List.of("자산번호", "품목", "상태", "현재 사용자", "비고"), sheet.getHeaders());
		assertEquals(jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_UNIT", Integer.class), sheet.getRows().size());
		assertEquals(java.util.Arrays.asList(no, itemName(unit), "보관", null, "대장 비고"), find(sheet, 0, no));
	}

	@Test
	void requestDownloadHasOneRowPerLineInDateRange() throws Exception {
		String q1 = newItem(uniq("엑셀신청1-"), AssetItemVO.TY_QTY);
		String q2 = newItem(uniq("엑셀신청2-"), AssetItemVO.TY_QTY);
		String reqA = reqService.submit(req(line(q1, 2), line(q2, 1)), "user1");
		String reqB = reqService.submit(req(line(q1, 1)), "user1");
		String reqC = reqService.submit(req(line(q2, 3)), "user1");
		// 신청일을 다른 테스트와 겹치지 않는 과거 날짜로 옮긴다(C는 범위 밖)
		jdbc().update("UPDATE ASSET_REQ SET REQ_PNTTM = '1999-02-03 10:00:00' WHERE REQ_NO = ?", reqA);
		jdbc().update("UPDATE ASSET_REQ SET REQ_PNTTM = '1999-02-28 23:59:00' WHERE REQ_NO = ?", reqB);
		jdbc().update("UPDATE ASSET_REQ SET REQ_PNTTM = '1999-03-01 00:00:00' WHERE REQ_NO = ?", reqC);

		AssetExcelSheet sheet = excelService.reqSheet("1999-02-01", "1999-02-28");
		assertEquals(List.of("신청번호", "신청일", "신청자", "부서", "품목", "신청 수량", "승인 수량", "품목 결과", "헤더 상태", "승인자", "처리일"),
				sheet.getHeaders());
		int inRange = jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_REQ r JOIN ASSET_REQ_LINE l ON l.REQ_NO = r.REQ_NO "
				+ "WHERE r.REQ_PNTTM >= '1999-02-01' AND r.REQ_PNTTM < '1999-03-01'", Integer.class);
		assertEquals(inRange, sheet.getRows().size());
		List<Object> reqNos = new ArrayList<>();
		sheet.getRows().forEach(row -> reqNos.add(row.get(0)));
		assertEquals(2, reqNos.stream().filter(reqA::equals).count());
		assertEquals(1, reqNos.stream().filter(reqB::equals).count());
		assertEquals(0, reqNos.stream().filter(reqC::equals).count());

		List<Object> first = sheet.getRows().get(reqNos.indexOf(reqA));
		assertEquals("1999-02-03 10:00", first.get(1));
		assertEquals("한신청", first.get(2));
		assertEquals(itemName(q1), first.get(4));
		assertEquals(2, first.get(5));
		assertEquals("미결", first.get(7));
		assertEquals("대기", first.get(8));

		assertThrows(AssetItemException.class, () -> excelService.reqSheet("1999-03-01", "1999-02-01"));
		assertThrows(AssetItemException.class, () -> excelService.reqSheet("1999/02/01", "1999-02-28"));
	}

	@Test
	void templatesCarryUploadHeaders() {
		assertEquals(List.of("품목명", "유형", "분류코드", "단위코드", "최소 수량"), excelService.itemTemplate().getHeaders());
		assertEquals(List.of("품목코드", "자산번호", "비고"), excelService.unitTemplate().getHeaders());
	}

	// ---------- 화면(컨트롤러) ----------

	@Test
	void controllerUploadsMultipartAndStreamsDownloads() throws Exception {
		AssetExcelController controller = new AssetExcelController();
		ReflectionTestUtils.setField(controller, "assetExcelService", excelService);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		EgovUserDetailsHelper helper = new EgovUserDetailsHelper();
		EgovUserDetailsService original = helper.getEgovUserDetailsService();
		LoginVO keeper = new LoginVO();
		keeper.setId("keeper");
		helper.setEgovUserDetailsService(new EgovUserDetailsService() {
			@Override
			public Object getAuthenticatedUser() {
				return keeper;
			}

			@Override
			public List<String> getAuthorities() {
				return List.of("ROLE_ASSET");
			}

			@Override
			public Boolean isAuthenticated() {
				return true;
			}
		});
		try {
			String a = uniq("엑셀화면-");
			byte[] ok = xlsx(row("품목명", "유형", "분류코드", "단위코드", "최소 수량"), row(a, "수량", "OFFICE", "EA", 0)).readAllBytes();
			mvc.perform(multipart("/asset/excel/uploadItems.do").file(new MockMultipartFile("file", "items.xlsx", null, ok)))
					.andExpect(status().isOk()).andExpect(view().name("egovframework/asset/excel/AssetExcelMain"))
					.andExpect(model().attribute("message", "품목 1건을 등록했습니다."));
			assertEquals("keeper", item(a).get("FRST_REGISTER_ID"));

			// 같은 파일을 다시 올리면 이름 중복으로 틀린 줄 목록
			MvcResult again = mvc.perform(multipart("/asset/excel/uploadItems.do").file(new MockMultipartFile("file", "items.xlsx", null, ok)))
					.andExpect(status().isOk()).andReturn();
			assertEquals(List.of("2: 품목명 중복: 사용 중인 품목에 같은 이름이 있습니다"),
					describe((AssetExcelResult) again.getModelAndView().getModel().get("result")));

			mvc.perform(get("/asset/excel/stock.do")).andExpect(status().isOk())
					.andExpect(header().string("Content-Disposition", "attachment; filename=\"stock_status_" + seoulToday()
							+ ".xlsx\"; filename*=UTF-8''%EC%9E%AC%EA%B3%A0%ED%98%84%ED%99%A9_" + seoulToday() + ".xlsx"));
			mvc.perform(get("/asset/excel/requests.do").param("fromDe", "2026-02-01").param("toDe", "2026-01-01"))
					.andExpect(status().isOk()).andExpect(view().name("egovframework/asset/excel/AssetExcelMain"))
					.andExpect(model().attribute("message", "기간의 시작일이 종료일보다 늦습니다."));
		} finally {
			helper.setEgovUserDetailsService(original);
		}
	}

	@Test
	void downloadFileNamesAreKoreanWithAsciiFallback() throws Exception {
		AssetExcelController controller = new AssetExcelController();
		ReflectionTestUtils.setField(controller, "assetExcelService", excelService);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		String d = seoulToday();

		assertDisposition(mvc, get("/asset/excel/stock.do"),
				"attachment; filename=\"stock_status_" + d + ".xlsx\"; filename*=UTF-8''%EC%9E%AC%EA%B3%A0%ED%98%84%ED%99%A9_" + d + ".xlsx");
		assertDisposition(mvc, get("/asset/excel/units.do"),
				"attachment; filename=\"asset_ledger_" + d + ".xlsx\"; filename*=UTF-8''%EC%9E%90%EC%82%B0%EB%8C%80%EC%9E%A5_" + d + ".xlsx");
		assertDisposition(mvc, get("/asset/excel/requests.do").param("fromDe", "2026-09-01").param("toDe", "2026-09-27"),
				"attachment; filename=\"requests_20260901-20260927.xlsx\"; "
						+ "filename*=UTF-8''%EC%8B%A0%EC%B2%AD%EB%82%B4%EC%97%AD_20260901-20260927.xlsx");
		assertDisposition(mvc, get("/asset/excel/template.do").param("type", "item"),
				"attachment; filename=\"item_upload_template.xlsx\"; "
						+ "filename*=UTF-8''%ED%92%88%EB%AA%A9_%EC%9D%BC%EA%B4%84%EB%93%B1%EB%A1%9D_%EC%96%91%EC%8B%9D.xlsx");
		assertDisposition(mvc, get("/asset/excel/template.do").param("type", "unit"),
				"attachment; filename=\"asset_upload_template.xlsx\"; "
						+ "filename*=UTF-8''%EC%9E%90%EC%82%B0_%EC%9D%BC%EA%B4%84%EB%93%B1%EB%A1%9D_%EC%96%91%EC%8B%9D.xlsx");
	}

	/** 헤더 값이 정확히 같고, 인코딩하지 않은 비ASCII 문자가 없으며, 본문은 여전히 .xlsx다. */
	private static void assertDisposition(MockMvc mvc, RequestBuilder req, String expected)
			throws Exception {
		MockHttpServletResponse resp = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse();
		List<String> values = resp.getHeaders("Content-Disposition");
		assertEquals(List.of(expected), values);
		assertTrue(values.get(0).chars().allMatch(c -> c < 0x80), values.get(0));
		try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(resp.getContentAsByteArray()))) {
			assertEquals("data", wb.getSheetAt(0).getSheetName());
		}
	}

	private static String seoulToday() {
		return LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
	}

	// ---------- 도우미 ----------

	private String newItem(String name, String ty) {
		AssetItemVO vo = new AssetItemVO();
		vo.setItemNm(name);
		vo.setItemTy(ty);
		vo.setClCode("IT");
		vo.setUnitCode("SET");
		vo.setMinQty(0);
		return itemService.insertItem(vo, "keeper");
	}

	private Map<String, Object> item(String name) {
		return jdbc().queryForMap("SELECT * FROM ASSET_ITEM WHERE ITEM_NM = ?", name);
	}

	private String itemName(String code) {
		return jdbc().queryForObject("SELECT ITEM_NM FROM ASSET_ITEM WHERE ITEM_CODE = ?", String.class, code);
	}

	private String unitCol(String assetNo, String col) {
		return jdbc().queryForObject("SELECT " + col + " FROM ASSET_UNIT WHERE ASSET_NO = ?", String.class, assetNo);
	}

	/** 품목의 IN 이력 증감(처리 순) */
	private List<Integer> inHist(String itemCode) {
		return jdbc().queryForList("SELECT CHANGE_QTY FROM ASSET_STOCK_HIST WHERE ITEM_CODE = ? AND HIST_TY = 'IN' ORDER BY STOCK_HIST_ID",
				Integer.class, itemCode);
	}

	private static List<String> describe(AssetExcelResult r) {
		List<String> out = new ArrayList<>();
		r.getErrors().forEach(e -> out.add(e.getRowNo() + ": " + e.getReason()));
		return out;
	}

	private static List<Object> find(AssetExcelSheet sheet, int col, Object key) {
		return sheet.getRows().stream().filter(row -> key.equals(row.get(col))).findFirst()
				.orElseThrow(() -> new AssertionError("row not found: " + key));
	}

	private static AssetReqVO req(AssetReqLineVO... lines) {
		AssetReqVO vo = new AssetReqVO();
		vo.setReqResn("엑셀 테스트");
		vo.setLines(new ArrayList<>(List.of(lines)));
		return vo;
	}

	private static AssetReqLineVO line(String itemCode, int qty) {
		AssetReqLineVO l = new AssetReqLineVO();
		l.setItemCode(itemCode);
		l.setReqQty(qty);
		return l;
	}

	private static MockHttpServletResponse render(AssetExcelSheet sheet) throws Exception {
		Map<String, Object> model = new HashMap<>();
		model.put("sheet", sheet);
		model.put("filename", sheet.getFileName());
		MockHttpServletResponse resp = new MockHttpServletResponse();
		new AssetExcelView().render(model, new MockHttpServletRequest(), resp);
		return resp;
	}

	private static List<String> cells(Row row, int n) {
		DataFormatter f = new DataFormatter();
		List<String> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(f.formatCellValue(row.getCell(i)));
		}
		return out;
	}

	private static Object[] row(Object... values) {
		return values;
	}

	private static InputStream xlsx(Object[]... rows) throws Exception {
		return write(new XSSFWorkbook(), rows);
	}

	private static InputStream xls(Object[]... rows) throws Exception {
		return write(new HSSFWorkbook(), rows);
	}

	private static InputStream write(Workbook wb, Object[]... rows) throws Exception {
		try (wb) {
			Sheet s = wb.createSheet("data");
			for (int i = 0; i < rows.length; i++) {
				Row row = s.createRow(i);
				for (int c = 0; c < rows[i].length; c++) {
					Object v = rows[i][c];
					if (v instanceof Number n) {
						row.createCell(c).setCellValue(n.doubleValue());
					} else if (v != null) {
						row.createCell(c).setCellValue(v.toString());
					}
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			wb.write(out);
			return new ByteArrayInputStream(out.toByteArray());
		}
	}
}
