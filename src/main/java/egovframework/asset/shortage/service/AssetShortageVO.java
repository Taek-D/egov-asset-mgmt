package egovframework.asset.shortage.service;

import egovframework.com.cmm.ComDefaultVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 재고 부족 품목(실행별, 조회 조건 겸용).
 */
@Getter
@Setter
public class AssetShortageVO extends ComDefaultVO {

	private static final long serialVersionUID = 1L;

	private String runId;
	private String itemCode;
	private String itemNm;
	/** QTY / UNIT */
	private String itemTy;
	private int availQty;
	private int minQty;
	/** 부족분 = 최소 수량 − 가용 */
	private int shortQty;
}
