package egovframework.asset.req.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import egovframework.asset.req.service.AssetReqHistVO;
import egovframework.asset.req.service.AssetReqLineVO;
import egovframework.asset.req.service.AssetReqVO;
import jakarta.annotation.Resource;

/**
 * 신청서 DAO.
 */
@Repository("assetReqDAO")
public class AssetReqDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	public List<AssetReqVO> selectReqList(AssetReqVO searchVO) {
		return selectList("AssetReq.selectReqList", searchVO);
	}

	public int selectReqListTotCnt(AssetReqVO searchVO) {
		return selectOne("AssetReq.selectReqListTotCnt", searchVO);
	}

	/** 업무사용자면 (APPLCNT_ID, 실제로 있는 부서 ORGNZT_ID 또는 null), 아니면 null */
	public AssetReqVO selectApplicant(String emplyrId) {
		return selectOne("AssetReq.selectApplicant", emplyrId);
	}

	/** 로그인 가능하고 authorCode 권한 행이 있는지 */
	public boolean hasAuthor(String emplyrId, String authorCode) {
		Map<String, Object> param = new HashMap<>();
		param.put("emplyrId", emplyrId);
		param.put("authorCode", authorCode);
		return (Integer) selectOne("AssetReq.selectAuthorCnt", param) > 0;
	}

	public void insertReq(AssetReqVO vo) {
		insert("AssetReq.insertReq", vo);
	}

	public void insertLine(AssetReqLineVO vo) {
		insert("AssetReq.insertLine", vo);
	}

	public void insertHist(AssetReqHistVO vo) {
		insert("AssetReq.insertHist", vo);
	}

	public AssetReqVO selectReqForUpdate(String reqNo) {
		return selectOne("AssetReq.selectReqForUpdate", reqNo);
	}

	public List<AssetReqLineVO> selectLinesForUpdate(String reqNo) {
		return selectList("AssetReq.selectLinesForUpdate", reqNo);
	}

	public void updateLine(AssetReqLineVO vo) {
		update("AssetReq.updateLine", vo);
	}

	/** @param approval true면 승인자·승인 시각도 기록(승인·반려) */
	public void updateSttus(String reqNo, String sttus, String actorId, boolean approval) {
		Map<String, Object> param = new HashMap<>();
		param.put("reqNo", reqNo);
		param.put("sttus", sttus);
		param.put("actorId", actorId);
		param.put("approval", approval);
		update("AssetReq.updateSttus", param);
	}

	public AssetReqVO selectReq(String reqNo) {
		return selectOne("AssetReq.selectReq", reqNo);
	}

	public List<AssetReqLineVO> selectLines(String reqNo) {
		return selectList("AssetReq.selectLines", reqNo);
	}

	public List<AssetReqHistVO> selectHistList(String reqNo) {
		return selectList("AssetReq.selectHistList", reqNo);
	}

	public List<AssetReqLineVO> selectReqLineReport(String fromDe, String toDe) {
		Map<String, Object> param = new HashMap<>();
		param.put("fromDe", fromDe);
		param.put("toDe", toDe);
		return selectList("AssetReq.selectReqLineReport", param);
	}
}
