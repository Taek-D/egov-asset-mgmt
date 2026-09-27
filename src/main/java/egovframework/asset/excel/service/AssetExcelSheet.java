package egovframework.asset.excel.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/**
 * 내려받을 시트 한 장: 파일 이름(한글, 확장자 없음)과 영문 대체 이름(확장자 없음), 한글 머리글, 데이터 행(숫자는 Integer, 빈 칸은 null).
 */
@Getter
public class AssetExcelSheet implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 영문 대체 이름(Content-Disposition의 filename) */
	private final String fileName;
	/** 한글 이름(Content-Disposition의 filename*) */
	private final String displayName;
	private final List<String> headers;
	private final List<List<Object>> rows = new ArrayList<>();

	public AssetExcelSheet(String displayName, String fileName, List<String> headers) {
		this.displayName = displayName;
		this.fileName = fileName;
		this.headers = List.copyOf(headers);
	}

	public void addRow(Object... values) {
		rows.add(java.util.Arrays.asList(values));
	}
}
