package egovframework.asset.stock.service;

import java.util.Date;

import egovframework.com.cmm.ComDefaultVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 재고 변동 이력(검색 조건 겸용: itemCode).
 * 수량 비품 행은 재고 수량 기준, 개별 자산 행은 가용 기준으로 증감·변동 후 값을 남긴다.
 */
@Getter
@Setter
public class AssetStockHistVO extends ComDefaultVO {

	private static final long serialVersionUID = 1L;

	private String stockHistId;
	private String itemCode;
	private String itemNm;
	private String itemTy;
	/** IN / ADJUST / RESERVE / RELEASE / DELIVER */
	private String histTy;
	private int changeQty;
	private int afterQty;
	private String resn;
	private String reqNo;
	private String processId;
	private Date processPnttm;
}
