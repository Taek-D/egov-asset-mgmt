package egovframework.asset.req.service;

import java.io.Serializable;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 신청서 처리 이력 한 행. 제출 행은 이전 상태가 없다(null).
 */
@Getter
@Setter
public class AssetReqHistVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String reqHistId;
	private String reqNo;
	private String beforeSttus;
	private String afterSttus;
	private String resn;
	private String processId;
	private String processNm;
	private Date processPnttm;
}
