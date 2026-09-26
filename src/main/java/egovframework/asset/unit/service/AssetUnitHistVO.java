package egovframework.asset.unit.service;

import java.io.Serializable;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 개별 자산 상태 이력 한 행. 등록 시 첫 행은 이전 상태가 없다(null).
 */
@Getter
@Setter
public class AssetUnitHistVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String unitHistId;
	private String assetNo;
	private String beforeSttus;
	private String afterSttus;
	private String resn;
	/** 관련 신청서(지급일 때) */
	private String reqNo;
	private String processId;
	private String processNm;
	private Date processPnttm;
}
