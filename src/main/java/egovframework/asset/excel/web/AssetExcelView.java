package egovframework.asset.excel.web;

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

/**
 * 모델의 {@code sheet}({@link AssetExcelSheet})를 .xlsx로 내려보낸다. 파일 이름은 모델의 {@code filename}(확장자 없이).
 */
public class AssetExcelView extends AbstractPOIExcelView {

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
