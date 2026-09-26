package egovframework.asset.excel.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/**
 * 내려받을 시트 한 장: 파일 이름(영문, 확장자 없음), 한글 머리글, 데이터 행(숫자는 Integer, 빈 칸은 null).
 */
@Getter
public class AssetExcelSheet implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String fileName;
	private final List<String> headers;
	private final List<List<Object>> rows = new ArrayList<>();

	public AssetExcelSheet(String fileName, List<String> headers) {
		this.fileName = fileName;
		this.headers = List.copyOf(headers);
	}

	public void addRow(Object... values) {
		rows.add(java.util.Arrays.asList(values));
	}
}
