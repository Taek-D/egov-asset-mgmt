package egovframework.asset.excel.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.excel.EgovExcelService;
import org.springframework.stereotype.Service;

import egovframework.asset.excel.service.AssetExcelResult;
import egovframework.asset.excel.service.AssetExcelService;
import egovframework.asset.excel.service.AssetExcelSheet;
import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.req.service.AssetReqLineVO;
import egovframework.asset.req.service.AssetReqService;
import egovframework.asset.req.service.AssetReqVO;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.asset.unit.service.AssetUnitVO;
import jakarta.annotation.Resource;

/**
 * 엑셀 일괄등록·내려받기 구현. 일괄등록 메서드 하나가 한 트랜잭션이다(검사 → 틀린 줄이 없을 때만 전부 저장).
 * 통합 비교는 DB 규칙(대소문자 무시)에 맞춰 소문자로 한다.
 */
@Service("assetExcelService")
public class AssetExcelServiceImpl extends EgovAbstractServiceImpl implements AssetExcelService {

	private static final Map<String, String> ITEM_TY = Map.of("수량", AssetItemVO.TY_QTY, "개별", AssetItemVO.TY_UNIT,
			"수량 비품", AssetItemVO.TY_QTY, "개별 자산", AssetItemVO.TY_UNIT);
	private static final Map<String, String> ITEM_TY_NM = Map.of(AssetItemVO.TY_QTY, "수량 비품", AssetItemVO.TY_UNIT, "개별 자산");
	private static final Map<String, String> UNIT_STTUS_NM = Map.of(AssetUnitVO.STORED, "보관", AssetUnitVO.IN_USE, "사용",
			AssetUnitVO.REPAIR, "수리", AssetUnitVO.DISPOSED, "폐기");

	/** 기본 공통컴포넌트의 엑셀 서비스(파일 읽기만 쓴다. 매핑 클래스는 쓰지 않음) */
	@Resource(name = "excelZipService")
	private EgovExcelService excelService;

	@Resource(name = "assetExcelDAO")
	private AssetExcelDAO assetExcelDAO;

	@Resource(name = "assetItemService")
	private AssetItemService assetItemService;

	@Resource(name = "assetStockService")
	private AssetStockService assetStockService;

	@Resource(name = "assetUnitService")
	private AssetUnitService assetUnitService;

	@Resource(name = "assetReqService")
	private AssetReqService assetReqService;

	/** 데이터 한 줄: 엑셀 행 번호와 앞뒤 공백을 뗀 칸 값(빈 칸은 "") */
	private record Line(int rowNo, String[] v) {
	}

	@Override
	public AssetExcelResult uploadItems(InputStream in, String actorId) {
		List<Line> lines = read(in, ITEM_HEADERS.size());
		Set<String> clCodes = new HashSet<>(assetExcelDAO.selectActiveCodes("AST001"));
		Set<String> unitCodes = new HashSet<>(assetExcelDAO.selectActiveCodes("AST002"));
		Set<String> takenNames = lower(query(column(lines, 0), assetExcelDAO::selectActiveItemNames));

		AssetExcelResult result = new AssetExcelResult();
		Map<String, Integer> firstRow = new HashMap<>();
		List<AssetItemVO> items = new ArrayList<>();
		for (Line l : lines) {
			String nm = l.v[0];
			String ty = l.v[1];
			String cl = l.v[2];
			String unit = l.v[3];
			String min = l.v[4];
			if (nm.isEmpty()) {
				result.addError(l.rowNo, "품목명 누락");
			} else if (nm.length() > 100) {
				result.addError(l.rowNo, "품목명은 100자 이내로 입력하세요");
			}
			if (ty.isEmpty()) {
				result.addError(l.rowNo, "유형 누락");
			} else if (!ITEM_TY.containsKey(ty)) {
				result.addError(l.rowNo, "유형은 수량 또는 개별로 입력하세요: " + ty);
			}
			if (!cl.isEmpty() && !clCodes.contains(cl)) {
				result.addError(l.rowNo, "분류코드 없음: " + cl);
			}
			if (!unit.isEmpty() && !unitCodes.contains(unit)) {
				result.addError(l.rowNo, "단위코드 없음: " + unit);
			}
			Integer minQty = min.isEmpty() ? Integer.valueOf(0) : parseNonNegative(min);
			if (minQty == null) {
				result.addError(l.rowNo, "최소 수량은 0 이상의 정수로 입력하세요: " + min);
			}
			if (!nm.isEmpty()) {
				String key = nm.toLowerCase(Locale.ROOT);
				Integer first = firstRow.putIfAbsent(key, l.rowNo);
				if (takenNames.contains(key)) {
					result.addError(l.rowNo, "품목명 중복: 사용 중인 품목에 같은 이름이 있습니다");
				} else if (first != null) {
					result.addError(l.rowNo, "품목명 중복: 파일의 " + first + "행과 같습니다");
				}
			}
			AssetItemVO vo = new AssetItemVO();
			vo.setItemNm(nm);
			vo.setItemTy(ITEM_TY.get(ty));
			vo.setClCode(cl);
			vo.setUnitCode(unit);
			vo.setMinQty(minQty);
			items.add(vo);
		}
		if (!result.isOk()) {
			return result;
		}
		for (AssetItemVO vo : items) {
			assetItemService.insertItem(vo, actorId);
		}
		result.setSavedCnt(items.size());
		return result;
	}

	@Override
	public AssetExcelResult uploadUnits(InputStream in, String actorId) {
		List<Line> lines = read(in, UNIT_HEADERS.size());
		Map<String, AssetItemVO> itemsByCode = new HashMap<>();
		for (AssetItemVO item : query(column(lines, 0), assetExcelDAO::selectItems)) {
			itemsByCode.put(item.getItemCode().toLowerCase(Locale.ROOT), item);
		}
		Set<String> takenNos = lower(query(column(lines, 1), assetExcelDAO::selectExistingAssetNos));

		AssetExcelResult result = new AssetExcelResult();
		Map<String, Integer> firstRow = new HashMap<>();
		List<AssetUnitVO> units = new ArrayList<>();
		for (Line l : lines) {
			String code = l.v[0];
			String no = l.v[1];
			String rm = l.v[2];
			AssetItemVO item = itemsByCode.get(code.toLowerCase(Locale.ROOT));
			if (code.isEmpty()) {
				result.addError(l.rowNo, "품목코드 누락");
			} else if (item == null) {
				result.addError(l.rowNo, "품목코드 없음: " + code);
			} else if (!item.isUnit()) {
				result.addError(l.rowNo, "유형 불일치: 개별 자산 품목이 아닙니다: " + code);
			} else if (!"Y".equals(item.getUseAt())) {
				result.addError(l.rowNo, "사용 중지된 품목입니다: " + code);
			}
			if (no.isEmpty()) {
				result.addError(l.rowNo, "자산번호 누락");
			} else if (no.length() > 50) {
				result.addError(l.rowNo, "자산번호는 50자 이내로 입력하세요");
			} else {
				String key = no.toLowerCase(Locale.ROOT);
				Integer first = firstRow.putIfAbsent(key, l.rowNo);
				if (takenNos.contains(key)) {
					result.addError(l.rowNo, "자산번호 중복: 이미 등록된 번호입니다(폐기 포함)");
				} else if (first != null) {
					result.addError(l.rowNo, "자산번호 중복: 파일의 " + first + "행과 같습니다");
				}
			}
			if (rm.length() > 500) {
				result.addError(l.rowNo, "비고는 500자 이내로 입력하세요");
			}
			AssetUnitVO vo = new AssetUnitVO();
			vo.setItemCode(item == null ? code : item.getItemCode());
			vo.setAssetNo(no);
			vo.setRm(rm);
			units.add(vo);
		}
		if (!result.isOk()) {
			return result;
		}
		// 잠금 규약: 관련 품목 전부를 코드 오름차순으로 먼저 잠근다(registerUnit이 같은 품목을 다시 잠가도 무해)
		Set<String> codes = new TreeSet<>();
		units.forEach(u -> codes.add(u.getItemCode()));
		assetStockService.lockItems(codes);
		for (AssetUnitVO vo : units) {
			assetUnitService.registerUnit(vo, actorId);
		}
		result.setSavedCnt(units.size());
		return result;
	}

	@Override
	public AssetExcelSheet stockSheet() {
		AssetItemVO search = new AssetItemVO();
		search.setFirstIndex(0);
		search.setRecordCountPerPage(Integer.MAX_VALUE);
		AssetExcelSheet sheet = new AssetExcelSheet("stock_status",
				List.of("품목코드", "품목명", "유형", "분류", "단위", "재고 또는 보관 수", "확보 수량", "가용", "최소 수량", "사용 여부"));
		for (AssetItemVO i : assetItemService.selectItemList(search)) {
			sheet.addRow(i.getItemCode(), i.getItemNm(), ITEM_TY_NM.get(i.getItemTy()), i.getClCodeNm(), i.getUnitCodeNm(),
					i.isUnit() ? i.getStoredQty() : i.getStockQty(), i.getReservedQty(), i.getAvailQty(), i.getMinQty(),
					"Y".equals(i.getUseAt()) ? "사용" : "사용 중지");
		}
		return sheet;
	}

	@Override
	public AssetExcelSheet unitSheet() {
		AssetUnitVO search = new AssetUnitVO();
		search.setFirstIndex(0);
		search.setRecordCountPerPage(Integer.MAX_VALUE);
		AssetExcelSheet sheet = new AssetExcelSheet("asset_ledger", List.of("자산번호", "품목", "상태", "현재 사용자", "비고"));
		for (AssetUnitVO u : assetUnitService.selectUnitList(search)) {
			sheet.addRow(u.getAssetNo(), u.getItemNm(), UNIT_STTUS_NM.get(u.getSttus()), u.getUserNm(), u.getRm());
		}
		return sheet;
	}

	@Override
	public AssetExcelSheet reqSheet(String fromDe, String toDe) throws Exception {
		LocalDate from = date(fromDe);
		LocalDate to = date(toDe);
		if (from.isAfter(to)) {
			throw new AssetItemException("기간의 시작일이 종료일보다 늦습니다.");
		}
		AssetExcelSheet sheet = new AssetExcelSheet("requests_" + from + "_" + to,
				List.of("신청번호", "신청일", "신청자", "부서", "품목", "신청 수량", "승인 수량", "품목 결과", "헤더 상태", "승인자", "처리일"));
		for (AssetReqLineVO l : assetReqService.selectReqLineReport(from.toString(), to.toString())) {
			sheet.addRow(l.getReqNo(), dateTime(l.getReqPnttm()), l.getApplcntNm(), l.getOrgnztNm(), l.getItemNm(), l.getReqQty(),
					l.getAprvQty(), AssetReqLineVO.STTUS_NM.get(l.getLineSttus()), AssetReqVO.STTUS_NM.get(l.getReqSttus()),
					l.getAprvNm(), dateTime(l.getLastProcessPnttm()));
		}
		return sheet;
	}

	@Override
	public AssetExcelSheet itemTemplate() {
		return new AssetExcelSheet("item_upload_template", ITEM_HEADERS);
	}

	@Override
	public AssetExcelSheet unitTemplate() {
		return new AssetExcelSheet("asset_upload_template", UNIT_HEADERS);
	}

	/** 첫 시트의 2행부터 읽는다(1행은 머리글). 모든 칸이 빈 줄은 건너뛴다. */
	private List<Line> read(InputStream in, int cols) {
		InputStream is;
		FileMagic magic;
		try {
			is = FileMagic.prepareToCheckMagic(in);
			magic = FileMagic.valueOf(is);
		} catch (IOException e) {
			throw notExcel();
		}
		if (magic != FileMagic.OLE2 && magic != FileMagic.OOXML) {
			throw notExcel();
		}
		List<Line> lines = new ArrayList<>();
		DataFormatter fmt = new DataFormatter();
		try (Workbook wb = magic == FileMagic.OLE2 ? excelService.loadWorkbook(is) : excelService.loadWorkbook(is, new XSSFWorkbook())) {
			Sheet sheet = wb.getSheetAt(0);
			for (int i = 1; i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;
				}
				String[] v = new String[cols];
				boolean blank = true;
				for (int c = 0; c < cols; c++) {
					v[c] = fmt.formatCellValue(row.getCell(c)).trim();
					blank &= v[c].isEmpty();
				}
				if (!blank) {
					lines.add(new Line(i + 1, v));
				}
			}
		} catch (IOException | RuntimeException e) {
			throw notExcel();
		}
		if (lines.isEmpty()) {
			throw new AssetItemException("등록할 줄이 없습니다. 1행은 머리글, 2행부터 내용을 적으세요.");
		}
		return lines;
	}

	private static AssetItemException notExcel() {
		return new AssetItemException("엑셀 파일(.xlsx, .xls)을 읽을 수 없습니다.");
	}

	private static Set<String> column(List<Line> lines, int col) {
		Set<String> out = new HashSet<>();
		for (Line l : lines) {
			if (!l.v[col].isEmpty()) {
				out.add(l.v[col]);
			}
		}
		return out;
	}

	/** IN 조회는 값이 있을 때만 */
	private static <T> List<T> query(Collection<String> keys, Function<Collection<String>, List<T>> fn) {
		return keys.isEmpty() ? List.of() : fn.apply(keys);
	}

	private static Set<String> lower(List<String> values) {
		Set<String> out = new HashSet<>();
		values.forEach(s -> out.add(s.toLowerCase(Locale.ROOT)));
		return out;
	}

	private static Integer parseNonNegative(String s) {
		try {
			int n = Integer.parseInt(s);
			return n < 0 ? null : n;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static LocalDate date(String s) {
		if (s == null || s.isBlank()) {
			throw new AssetItemException("기간(시작일·종료일)을 입력하세요.");
		}
		try {
			return LocalDate.parse(s.trim());
		} catch (DateTimeParseException e) {
			throw new AssetItemException("기간은 yyyy-MM-dd 형식으로 입력하세요: " + s);
		}
	}

	private static String dateTime(Date d) {
		return d == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(d);
	}
}
