package egovframework.asset.depthead.service;

import java.io.Serializable;

/**
 * 부서와 그 부서장 지정 상태. 지정 후보 목록에서는 사용자(emplyrId, userNm)만 채운다.
 */
public class DeptHeadVO implements Serializable {

	private static final long serialVersionUID = 1L;

	/** 유효한 부서장 */
	public static final String STATUS_VALID = "VALID";
	/** 지정된 사용자가 다른 부서로 옮겼다(부서 없음 포함) */
	public static final String STATUS_MOVED = "MOVED";
	/** 잠김: 잠금 표시만 켜짐 */
	public static final String STATUS_LOCKED = "LOCKED";
	/** 비활성: 잠금 표시 + 권한 행 없음 */
	public static final String STATUS_DISABLED = "DISABLED";
	/** 잠금 표시는 없지만 권한 행이 없다 */
	public static final String STATUS_NO_ROLE = "NO_ROLE";
	/** 업무사용자 계정이 없다 */
	public static final String STATUS_NO_ACCOUNT = "NO_ACCOUNT";

	private String orgnztId;
	private String orgnztNm;
	private String emplyrId;
	private String userNm;
	private String assignId;
	private String assignPnttm;
	/** 부서장 지정 상태(STATUS_*). 지정이 없으면 null */
	private String headStatus;

	public boolean isValid() {
		return STATUS_VALID.equals(headStatus);
	}

	public String getOrgnztId() {
		return orgnztId;
	}

	public void setOrgnztId(String orgnztId) {
		this.orgnztId = orgnztId;
	}

	public String getOrgnztNm() {
		return orgnztNm;
	}

	public void setOrgnztNm(String orgnztNm) {
		this.orgnztNm = orgnztNm;
	}

	public String getEmplyrId() {
		return emplyrId;
	}

	public void setEmplyrId(String emplyrId) {
		this.emplyrId = emplyrId;
	}

	public String getUserNm() {
		return userNm;
	}

	public void setUserNm(String userNm) {
		this.userNm = userNm;
	}

	public String getAssignId() {
		return assignId;
	}

	public void setAssignId(String assignId) {
		this.assignId = assignId;
	}

	public String getAssignPnttm() {
		return assignPnttm;
	}

	public void setAssignPnttm(String assignPnttm) {
		this.assignPnttm = assignPnttm;
	}

	public String getHeadStatus() {
		return headStatus;
	}

	public void setHeadStatus(String headStatus) {
		this.headStatus = headStatus;
	}
}
