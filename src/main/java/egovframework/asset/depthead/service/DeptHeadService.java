package egovframework.asset.depthead.service;

import java.util.List;

import egovframework.com.cmm.ComDefaultVO;

/**
 * 부서장 지정과 승인자 결정(명세 §3, §7).
 *
 * <p>로그인 가능 = 업무사용자(COMTNEMPLYRINFO)이고 잠금 표시(LOCK_AT='Y')가 없고 권한 행(COMTNEMPLYRSCRTYESTBS)이 있다.
 * 유효한 부서장 = 부서에 지정된 사용자가 <b>지금도</b> 그 부서 소속이고 로그인 가능하다. 무효한 지정 기록은 자동으로 지우지 않는다.
 * 관리자 = ROLE_ADMIN 권한 행이 있고 로그인 가능한 업무사용자.
 *
 * <p>승인자는 조회·승인하는 그 시점에 정한다(저장하지 않는다). 신청서의 부서(ASSET_REQ.ORGNZT_ID, 신청 당시 부서)와 신청자로 정한다:
 * <ol>
 * <li>그 부서에 지금 유효한 부서장이 있고 그 사람이 신청자가 아니면 → 그 부서장 한 명</li>
 * <li>아니면 → 신청자가 아닌 관리자 전원</li>
 * </ol>
 * 누구도 자기 신청을 승인할 수 없다.
 */
public interface DeptHeadService {

	// ---------- 승인자 결정(요청 흐름에서 사용) ----------

	/**
	 * 지금 이 신청을 승인할 수 있는 사람들.
	 *
	 * @param orgnztId 신청 당시 부서(ASSET_REQ.ORGNZT_ID)
	 * @param applicantId 신청자 EMPLYR_ID
	 * @return 승인 가능한 EMPLYR_ID 목록(중복 없음, EMPLYR_ID 오름차순). 신청자는 절대 들어 있지 않다. 비어 있으면 승인할 사람이 없다
	 */
	List<String> selectApproverIds(String orgnztId, String applicantId) throws Exception;

	/**
	 * 승인할 사람이 한 명이라도 있는지. 제출 차단(없으면 제출 금지)과 '승인자 없음' 표시에 쓴다.
	 *
	 * @return {@code !selectApproverIds(orgnztId, applicantId).isEmpty()}
	 */
	boolean hasApprover(String orgnztId, String applicantId) throws Exception;

	/**
	 * userId가 지금 (orgnztId, applicantId) 신청을 승인할 수 있는지. userId가 신청자면 항상 false(자기 승인 금지).
	 * 승인 처리 직전에 신청 행을 잠근 뒤 다시 확인하는 용도로 쓴다.
	 *
	 * @param userId 승인하려는 사람 EMPLYR_ID
	 * @param orgnztId 신청 당시 부서
	 * @param applicantId 신청자 EMPLYR_ID
	 */
	boolean canApprove(String userId, String orgnztId, String applicantId) throws Exception;

	/**
	 * 부서의 지금 유효한 부서장.
	 *
	 * @return EMPLYR_ID. 지정이 없거나 무효(부서 이동·잠김·비활성 등)면 null. 신청자인지는 따지지 않는다
	 */
	String selectValidHeadId(String orgnztId) throws Exception;

	/**
	 * 이 사용자가 지금 유효한 부서장으로 있는 부서(부서 신청 조회·승인 대기함용). 사용자는 한 부서에만 소속되므로 많아야 하나다.
	 *
	 * @return ORGNZT_ID, 유효한 부서장이 아니면 null
	 */
	String selectValidHeadOrgnztId(String emplyrId) throws Exception;

	/**
	 * 로그인 가능한 관리자(ROLE_ADMIN)인지. 잠기거나 비활성이면 false.
	 */
	boolean isActiveAdmin(String emplyrId) throws Exception;

	// ---------- 부서장 지정 화면(관리자) ----------

	/**
	 * 전체 부서와 지정된 부서장, 지정 상태(DeptHeadVO.headStatus: 지정 없음 null, 유효 VALID, 그 외 무효 사유). 부서 ID 순, 페이징.
	 *
	 * @param searchVO firstIndex, recordCountPerPage만 쓴다
	 */
	List<DeptHeadVO> selectDeptHeadList(ComDefaultVO searchVO) throws Exception;

	/** 전체 부서 수 */
	int selectDeptHeadListCnt(ComDefaultVO searchVO) throws Exception;

	/**
	 * 부서장으로 지정할 수 있는 사람: 지금 그 부서 소속이고 로그인 가능한 업무사용자(emplyrId, userNm), EMPLYR_ID 순.
	 */
	List<DeptHeadVO> selectHeadCandidateList(String orgnztId) throws Exception;

	/**
	 * 부서장 지정. 이미 부서장이 있으면 바꾼다(부서당 최대 1명).
	 *
	 * @param assignId 지정하는 관리자 EMPLYR_ID
	 * @throws DeptHeadException 부서가 없거나, 대상이 그 부서 소속 업무사용자가 아니거나, 로그인할 수 없는 계정일 때
	 */
	void assignDeptHead(String orgnztId, String emplyrId, String assignId) throws Exception;

	/** 부서장 해제(지정 기록 삭제). 지정이 없으면 아무 일도 하지 않는다 */
	void unassignDeptHead(String orgnztId) throws Exception;
}
