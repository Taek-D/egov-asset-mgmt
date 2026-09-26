package egovframework.asset.req.service;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * 신청 품목 한 줄(작성·승인 입력 겸용). 신청 내역 보고(selectReqLineReport)에서는 헤더 필드(req*)도 채운다.
 */
@Getter
@Setter
public class AssetReqLineVO implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String PENDING = "PENDING";
	public static final String APPROVED = "APPROVED";
	public static final String REJECTED = "REJECTED";

	/** 품목 결과 → 화면 이름 */
	public static final Map<String, String> STTUS_NM = Map.of(PENDING, "미결", APPROVED, "승인", REJECTED, "반려");

	private String reqNo;
	private String itemCode;
	private String itemNm;
	/** QTY / UNIT */
	private String itemTy;
	private String useAt;
	private Integer reqQty;
	private Integer aprvQty;
	/** 품목 결과 PENDING / APPROVED / REJECTED (승인 입력에서는 APPROVED 또는 REJECTED) */
	private String lineSttus;
	private String rejectResn;
	/** 화면 표시용 현재 가용 수량 */
	private Integer availQty;

	/** 신청 내역 보고용 헤더 필드 */
	private Date reqPnttm;
	private String applcntId;
	private String applcntNm;
	private String orgnztId;
	private String orgnztNm;
	private String reqSttus;
	private String aprvId;
	private String aprvNm;
	private Date lastProcessPnttm;

	public boolean isUnit() {
		return "UNIT".equals(itemTy);
	}
}
