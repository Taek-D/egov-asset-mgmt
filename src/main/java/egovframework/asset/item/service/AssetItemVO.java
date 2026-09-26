package egovframework.asset.item.service;

import egovframework.com.cmm.ComDefaultVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 품목(검색 조건 겸용). 재고·가용 필드는 조회 결과로만 채워진다.
 */
@Getter
@Setter
public class AssetItemVO extends ComDefaultVO {

	private static final long serialVersionUID = 1L;

	/** 품목 유형: 수량 비품 */
	public static final String TY_QTY = "QTY";
	/** 품목 유형: 개별 자산 */
	public static final String TY_UNIT = "UNIT";

	private String itemCode;
	private String itemNm;
	/** QTY / UNIT */
	private String itemTy;
	/** 분류(공통코드 AST001) */
	private String clCode;
	private String clCodeNm;
	/** 단위(공통코드 AST002) */
	private String unitCode;
	private String unitCodeNm;
	private Integer minQty;
	/** Y / N */
	private String useAt;
	/** 재고 수량(수량 비품) */
	private int stockQty;
	/** 확보 수량(개별 자산) */
	private int reservedQty;
	/** 보관(STORED) 상태 자산 수(개별 자산) */
	private int storedQty;
	/** 가용 수량: 수량 비품 = 재고, 개별 자산 = 보관 − 확보 */
	private int availQty;
	/** 재고 변동·신청·개별 자산 이력 존재 여부(있으면 유형 변경·삭제 불가) */
	private boolean hasHist;

	/** 검색: 유형 */
	private String searchItemTy;

	public boolean isUnit() {
		return TY_UNIT.equals(itemTy);
	}
}
