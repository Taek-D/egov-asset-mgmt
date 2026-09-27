package egovframework.asset.req.service;

import java.util.List;
import java.util.Map;

import egovframework.asset.item.service.AssetItemException;

/**
 * 비품 신청·승인·지급·취소(docs/business-rules.md '신청서', '동시 처리 결과'). 모든 메서드는 처리하는 사람의 EMPLYR_ID를 받고 보안 컨텍스트를 읽지 않는다.
 *
 * <p>상태: 대기(PENDING) → 승인(APPROVED) / 반려(REJECTED) / 신청취소(CANCELED), 승인 → 지급완료(DELIVERED) / 지급취소(DELIVERY_CANCELED).
 * 대기·승인만 다음 단계가 있고 나머지는 끝 상태다. 모든 상태 변경은 처리 이력(ASSET_REQ_HIST) 한 행을 남긴다(제출 행 포함).
 *
 * <p>잠금(§8): 승인·신청취소·지급·지급취소는 신청서 행 {@code FOR UPDATE} → 품목 줄 → 품목 행 코드 오름차순
 * ({@code AssetStockService.lockItems}) → 상태·가용 재확인 → 처리. 상태가 이미 바뀐 신청은 "이미 처리된 신청입니다."로 거부한다.
 *
 * <p>거부는 {@link AssetItemException}(메시지는 화면 표시용)이며 트랜잭션 전체가 롤백된다.
 */
public interface AssetReqService {

	/** 목록 범위: 내 신청 */
	String SCOPE_MY = "MY";
	/** 목록 범위: 부서장의 부서 신청(신청 당시 부서 기준, 모든 상태) */
	String SCOPE_DEPT = "DEPT";
	/** 목록 범위: 승인 대기함(지금 승인자인 대기 신청 + 관리자는 '승인자 없음' 대기 신청) */
	String SCOPE_INBOX = "INBOX";
	/** 목록 범위: 전체(관리자·자산담당자만) */
	String SCOPE_ALL = "ALL";

	/**
	 * 신청서 제출. 부서에 소속된 업무사용자만, 사용 중인 품목만, 품목 줄 1개 이상(수량 ≥ 1, 같은 품목은 한 줄로 합산).
	 * 지금 승인할 사람이 없으면 거부. 가용보다 많아도 제출된다(재고 확인은 승인 때). 바로 '대기'가 되고 이력(없음 → 대기)을 남긴다.
	 *
	 * @param vo reqResn, lines(itemCode, reqQty). 품목·수량이 모두 빈 줄은 무시한다
	 * @return 신청번호
	 */
	String submit(AssetReqVO vo, String applicantId) throws Exception;

	/**
	 * 승인 처리(승인·반려를 한 번에). 모든 품목 줄에 결과를 정해야 한다: 승인(1 ≤ 승인 수량 ≤ 신청 수량) 또는 반려(사유 필수).
	 * 승인 품목 전부의 가용을 확인해 하나라도 모자라면 아무것도 바꾸지 않고 모자란 품목 전부를 알린다.
	 * 통과하면 승인 수량만큼 확보(RESERVE)하고 헤더는 '승인', 모두 반려면 '반려'(재고 그대로).
	 * 자기 신청은 승인·반려할 수 없고, 지금 승인자(DeptHeadService.canApprove)만 처리한다.
	 *
	 * @param decisions 품목 줄마다 itemCode, lineSttus(APPROVED/REJECTED), aprvQty 또는 rejectResn
	 */
	void approve(String reqNo, List<AssetReqLineVO> decisions, String approverId) throws Exception;

	/** 신청취소: 신청자 본인만, '대기'일 때만. 재고 변화 없음. */
	void cancel(String reqNo, String applicantId) throws Exception;

	/**
	 * 지급: 자산담당자만, '승인' 신청서의 승인 품목 전부를 한 번에. 개별 자산 품목은 승인 수량과 정확히 같은 수의 보관 자산을 골라야 한다
	 * (사용·현재 사용자 = 신청자, 확보 해제 DELIVER). 수량 비품은 승인 때 재고가 이미 줄었으므로 변화 없음. 헤더 '지급완료'.
	 *
	 * @param picks 개별 자산 품목코드 → 고른 자산번호
	 */
	void deliver(String reqNo, Map<String, List<String>> picks, String keeperId) throws Exception;

	/** 지급취소: 자산담당자만, '승인'일 때만, 사유 필수. 확보분 복구(RELEASE: 수량 비품 재고 +, 개별 자산 확보 −). */
	void cancelDelivery(String reqNo, String reason, String keeperId) throws Exception;

	/**
	 * 상세(헤더·품목 줄(현재 가용 포함)·처리 이력·지금 승인자·보는 사람이 할 수 있는 처리).
	 * 볼 수 있는 사람: 신청자, 신청 당시 부서의 지금 부서장, 지금 승인자, 자산담당자, 관리자. 그 밖은 거부.
	 */
	AssetReqVO selectReqDetail(String reqNo, String viewerId) throws Exception;

	/**
	 * 범위별 목록(최신순, 페이징 firstIndex/recordCountPerPage, 검색 searchSttus·searchReqNo).
	 * 부서장이 아닌 사람의 DEPT, 관리자·자산담당자가 아닌 사람의 ALL은 거부("부서장이 아닙니다." 등).
	 *
	 * @param scope {@link #SCOPE_MY} / {@link #SCOPE_DEPT} / {@link #SCOPE_INBOX} / {@link #SCOPE_ALL}
	 */
	List<AssetReqVO> selectReqList(String scope, AssetReqVO searchVO, String userId) throws Exception;

	int selectReqListTotCnt(String scope, AssetReqVO searchVO, String userId) throws Exception;

	/** 자산담당자(ROLE_ASSET, 로그인 가능)인지 */
	boolean isKeeper(String emplyrId) throws Exception;

	/**
	 * 신청 내역 보고(엑셀 내려받기용): 신청일이 fromDe~toDe(yyyy-MM-dd, 양끝 포함)인 신청서의 품목 줄마다 한 행
	 * (신청번호, 신청일, 신청자, 부서, 품목, 신청·승인 수량, 품목 결과, 헤더 상태, 승인자, 처리일). 신청일·신청번호·품목코드 순.
	 * 범위 제한이 없으므로 자산담당자 화면에서만 부른다.
	 */
	List<AssetReqLineVO> selectReqLineReport(String fromDe, String toDe) throws Exception;
}
