package egovframework.asset.excel.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 일괄등록 결과: 저장한 건수, 또는 틀린 줄 목록(이때 저장 건수는 0).
 */
@Getter
public class AssetExcelResult implements Serializable {

	private static final long serialVersionUID = 1L;

	@Setter
	private int savedCnt;
	private final List<RowError> errors = new ArrayList<>();

	public boolean isOk() {
		return errors.isEmpty();
	}

	public void addError(int rowNo, String reason) {
		errors.add(new RowError(rowNo, reason));
	}

	/** 틀린 줄: 엑셀의 실제 행 번호(머리글 = 1행)와 이유 */
	@Getter
	public static class RowError implements Serializable {

		private static final long serialVersionUID = 1L;

		private final int rowNo;
		private final String reason;

		public RowError(int rowNo, String reason) {
			this.rowNo = rowNo;
			this.reason = reason;
		}
	}
}
