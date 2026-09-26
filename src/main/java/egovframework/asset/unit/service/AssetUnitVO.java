package egovframework.asset.unit.service;

import java.util.Date;

import egovframework.com.cmm.ComDefaultVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 개별 자산(검색 조건 겸용: searchItemCode, searchSttus, searchAssetNo).
 */
@Getter
@Setter
public class AssetUnitVO extends ComDefaultVO {

	private static final long serialVersionUID = 1L;

	/** 상태: 보관 */
	public static final String STORED = "STORED";
	/** 상태: 사용 */
	public static final String IN_USE = "IN_USE";
	/** 상태: 수리 */
	public static final String REPAIR = "REPAIR";
	/** 상태: 폐기(끝 상태) */
	public static final String DISPOSED = "DISPOSED";

	private String assetNo;
	private String itemCode;
	private String itemNm;
	/** 품목 사용 여부 Y / N */
	private String useAt;
	/** STORED / IN_USE / REPAIR / DISPOSED */
	private String sttus;
	/** 현재 사용자(EMPLYR_ID). 사용 상태에서만 값이 있다 */
	private String userId;
	private String userNm;
	/** 비고 */
	private String rm;
	private String frstRegisterId;
	private Date frstRegistPnttm;
	private String lastUpdusrId;
	private Date lastUpdtPnttm;

	/** 검색: 품목코드 */
	private String searchItemCode;
	/** 검색: 상태 */
	private String searchSttus;
	/** 검색: 자산번호 일부 */
	private String searchAssetNo;
}
