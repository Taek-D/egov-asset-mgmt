package egovframework.asset.depthead.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import egovframework.asset.depthead.service.DeptHeadVO;
import egovframework.com.cmm.ComDefaultVO;
import jakarta.annotation.Resource;

/**
 * 부서장 지정 DAO.
 */
@Repository("deptHeadDAO")
public class DeptHeadDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	public List<DeptHeadVO> selectDeptHeadList(ComDefaultVO searchVO) {
		return selectList("DeptHead.selectDeptHeadList", searchVO);
	}

	public int selectDeptHeadListCnt(ComDefaultVO searchVO) {
		return selectOne("DeptHead.selectDeptHeadListCnt", searchVO);
	}

	public int selectDeptCnt(String orgnztId) {
		return selectOne("DeptHead.selectDeptCnt", orgnztId);
	}

	public String selectValidHeadId(String orgnztId) {
		return selectOne("DeptHead.selectValidHeadId", orgnztId);
	}

	public List<String> selectValidHeadOrgnztIds(String emplyrId) {
		return selectList("DeptHead.selectValidHeadOrgnztIds", emplyrId);
	}

	public List<String> selectActiveAdminIds() {
		return selectList("DeptHead.selectActiveAdminIds");
	}

	/** emplyrId가 null이면 부서의 후보 전원 */
	public List<DeptHeadVO> selectHeadCandidateList(String orgnztId, String emplyrId) {
		Map<String, Object> param = new HashMap<>();
		param.put("orgnztId", orgnztId);
		param.put("emplyrId", emplyrId);
		return selectList("DeptHead.selectHeadCandidateList", param);
	}

	public void mergeDeptHead(String orgnztId, String emplyrId, String assignId) {
		insert("DeptHead.mergeDeptHead", Map.of("orgnztId", orgnztId, "emplyrId", emplyrId, "assignId", assignId));
	}

	public void deleteDeptHead(String orgnztId) {
		delete("DeptHead.deleteDeptHead", orgnztId);
	}
}
