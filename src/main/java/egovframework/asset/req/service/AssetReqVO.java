package egovframework.asset.req.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import egovframework.asset.unit.service.AssetUnitVO;
import egovframework.com.cmm.ComDefaultVO;
import lombok.Getter;
import lombok.Setter;

/**
 * 신청서 헤더(목록·상세·작성·승인 입력 겸용). 조회 범위 필드(scope*)는 서비스가 채우며 화면 입력으로 바꿀 수 없다.
 */
@Getter
@Setter
public class AssetReqVO extends ComDefaultVO {

	private static final long serialVersionUID = 1L;

	public static final String PENDING = "PENDING";
	public static final String REJECTED = "REJECTED";
	public static final String APPROVED = "APPROVED";
	public static final String DELIVERED = "DELIVERED";
	public static final String CANCELED = "CANCELED";
	public static final String DELIVERY_CANCELED = "DELIVERY_CANCELED";

	/** 헤더 상태 → 화면 이름 */
	public static final Map<String, String> STTUS_NM = Map.of(PENDING, "대기", REJECTED, "반려", APPROVED, "승인(지급 대기)",
			DELIVERED, "지급완료", CANCELED, "신청취소", DELIVERY_CANCELED, "지급취소");

	private String reqNo;
	private String applcntId;
	private String applcntNm;
	/** 신청 당시 부서 */
	private String orgnztId;
	private String orgnztNm;
	private String reqResn;
	private String sttus;
	private Date reqPnttm;
	/** 승인·반려한 사람 */
	private String aprvId;
	private String aprvNm;
	private Date aprvPnttm;
	private String lastProcessId;
	private String lastProcessNm;
	private Date lastProcessPnttm;

	/** 목록: 첫 품목명, 품목 줄 수 */
	private String firstItemNm;
	private int lineCnt;
	/** 대기 신청인데 지금 승인할 사람이 없음 */
	private boolean noApprover;

	/** 상세: 품목 줄, 처리 이력, 지금 승인자, 보는 사람이 할 수 있는 처리 */
	private List<AssetReqLineVO> lines = new ArrayList<>();
	private List<AssetReqHistVO> histList = new ArrayList<>();
	private List<String> approverIds = new ArrayList<>();
	private boolean approvable;
	private boolean cancelable;
	private boolean deliverable;

	/** 지급 화면: 개별 자산 품목별 보관 자산 */
	private Map<String, List<AssetUnitVO>> storedUnits;

	/** 검색: 상태, 신청번호 일부 */
	private String searchSttus;
	private String searchReqNo;

	/** 조회 범위(서비스가 설정) */
	private String scopeApplcntId;
	private String scopeOrgnztId;
	private String scopeInboxUserId;
	private boolean scopeInboxAdmin;
}
