package egovframework.asset.excel.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egovframe.rte.fdl.excel.util.AbstractPOIExcelView;

import egovframework.asset.excel.service.AssetExcelSheet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * 모델의 {@code sheet}({@link AssetExcelSheet})를 .xlsx로 내려보낸다. 모델의 {@code filename}은 영문 대체 이름(확장자 없이)이다.
 * 실행환경 뷰는 Content-Disposition에 filename만 인코딩 없이 쓰고(final 메서드), 곧바로 응답을 확정한다.
 * 그래서 그 헤더 쓰기를 응답 래퍼에서 가로채 영문 filename과 한글 filename*(RFC 6266/5987)로 바꿔 쓴다.
 */
public class AssetExcelView extends AbstractPOIExcelView {

	private static final String CONTENT_DISPOSITION = "Content-Disposition";

	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		AssetExcelSheet sheet = (AssetExcelSheet) model.get("sheet");
		String disposition = "attachment; filename=\"" + sheet.getFileName() + ".xlsx\"; filename*=UTF-8''"
				+ URLEncoder.encode(sheet.getDisplayName() + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
		super.render(model, request, new HttpServletResponseWrapper(response) {
			@Override
			public void setHeader(String name, String value) {
				super.setHeader(name, CONTENT_DISPOSITION.equalsIgnoreCase(name) ? disposition : value);
			}

			@Override
			public void addHeader(String name, String value) {
				super.addHeader(name, CONTENT_DISPOSITION.equalsIgnoreCase(name) ? disposition : value);
			}
		});
	}

	@Override
	protected void buildExcelDocument(Map<String, Object> model, XSSFWorkbook wb, HttpServletRequest request, HttpServletResponse response) {
		AssetExcelSheet data = (AssetExcelSheet) model.get("sheet");
		XSSFSheet sheet = wb.createSheet("data");
		sheet.setDefaultColumnWidth(16);

		CellStyle bold = wb.createCellStyle();
		Font font = wb.createFont();
		font.setBold(true);
		bold.setFont(font);
		XSSFRow header = sheet.createRow(0);
		for (int c = 0; c < data.getHeaders().size(); c++) {
			XSSFCell cell = header.createCell(c);
			cell.setCellValue(data.getHeaders().get(c));
			cell.setCellStyle(bold);
		}

		int r = 1;
		for (List<Object> values : data.getRows()) {
			XSSFRow row = sheet.createRow(r++);
			for (int c = 0; c < values.size(); c++) {
				Object v = values.get(c);
				if (v instanceof Number n) {
					row.createCell(c).setCellValue(n.doubleValue());
				} else if (v != null) {
					row.createCell(c).setCellValue(v.toString());
				}
			}
		}
	}
}
