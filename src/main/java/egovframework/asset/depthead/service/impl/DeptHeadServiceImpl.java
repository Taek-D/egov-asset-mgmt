package egovframework.asset.depthead.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;

import egovframework.asset.depthead.service.DeptHeadException;
import egovframework.asset.depthead.service.DeptHeadService;
import egovframework.asset.depthead.service.DeptHeadVO;
import egovframework.com.cmm.ComDefaultVO;
import jakarta.annotation.Resource;

/**
 * 부서장 지정과 승인자 결정 구현.
 */
@Service("deptHeadService")
public class DeptHeadServiceImpl extends EgovAbstractServiceImpl implements DeptHeadService {

	@Resource(name = "deptHeadDAO")
	private DeptHeadDAO deptHeadDAO;

	@Override
	public List<String> selectApproverIds(String orgnztId, String applicantId) throws Exception {
		String head = deptHeadDAO.selectValidHeadId(orgnztId);
		if (head != null && !head.equals(applicantId)) {
			return List.of(head);
		}
		List<String> admins = new ArrayList<>(deptHeadDAO.selectActiveAdminIds());
		admins.remove(applicantId);
		return admins;
	}

	@Override
	public boolean hasApprover(String orgnztId, String applicantId) throws Exception {
		return !selectApproverIds(orgnztId, applicantId).isEmpty();
	}

	@Override
	public boolean canApprove(String userId, String orgnztId, String applicantId) throws Exception {
		if (userId == null || userId.equals(applicantId)) {
			return false;
		}
		return selectApproverIds(orgnztId, applicantId).contains(userId);
	}

	@Override
	public String selectValidHeadId(String orgnztId) throws Exception {
		return deptHeadDAO.selectValidHeadId(orgnztId);
	}

	@Override
	public String selectValidHeadOrgnztId(String emplyrId) throws Exception {
		List<String> ids = deptHeadDAO.selectValidHeadOrgnztIds(emplyrId);
		return ids.isEmpty() ? null : ids.get(0);
	}

	@Override
	public boolean isActiveAdmin(String emplyrId) throws Exception {
		return deptHeadDAO.selectActiveAdminIds().contains(emplyrId);
	}

	@Override
	public List<DeptHeadVO> selectDeptHeadList(ComDefaultVO searchVO) throws Exception {
		return deptHeadDAO.selectDeptHeadList(searchVO);
	}

	@Override
	public int selectDeptHeadListCnt(ComDefaultVO searchVO) throws Exception {
		return deptHeadDAO.selectDeptHeadListCnt(searchVO);
	}

	@Override
	public List<DeptHeadVO> selectHeadCandidateList(String orgnztId) throws Exception {
		return deptHeadDAO.selectHeadCandidateList(orgnztId, null);
	}

	@Override
	public void assignDeptHead(String orgnztId, String emplyrId, String assignId) throws Exception {
		if (orgnztId == null || deptHeadDAO.selectDeptCnt(orgnztId) == 0) {
			throw new DeptHeadException("부서가 없습니다.");
		}
		if (emplyrId == null || deptHeadDAO.selectHeadCandidateList(orgnztId, emplyrId).isEmpty()) {
			throw new DeptHeadException("그 부서 소속이면서 로그인할 수 있는(잠기거나 비활성이 아닌) 직원만 부서장으로 지정할 수 있습니다.");
		}
		deptHeadDAO.mergeDeptHead(orgnztId, emplyrId, assignId);
	}

	@Override
	public void unassignDeptHead(String orgnztId) throws Exception {
		deptHeadDAO.deleteDeptHead(orgnztId);
	}
}
