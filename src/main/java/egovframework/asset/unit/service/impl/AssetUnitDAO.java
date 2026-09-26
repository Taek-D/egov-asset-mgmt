package egovframework.asset.unit.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import egovframework.asset.unit.service.AssetUnitHistVO;
import egovframework.asset.unit.service.AssetUnitVO;
import jakarta.annotation.Resource;

/**
 * 개별 자산 DAO.
 */
@Repository("assetUnitDAO")
public class AssetUnitDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	public List<AssetUnitVO> selectUnitList(AssetUnitVO searchVO) {
		return selectList("AssetUnit.selectUnitList", searchVO);
	}

	public int selectUnitListTotCnt(AssetUnitVO searchVO) {
		return selectOne("AssetUnit.selectUnitListTotCnt", searchVO);
	}

	public AssetUnitVO selectUnit(String assetNo) {
		return selectOne("AssetUnit.selectUnit", assetNo);
	}

	/** 자산 행들을 ASSET_NO 오름차순으로 잠근다(품목 잠금 뒤에만 부른다) */
	public List<AssetUnitVO> selectUnitsForUpdate(Collection<String> assetNos) {
		return selectList("AssetUnit.selectUnitsForUpdate", assetNos);
	}

	public int selectAssetNoCnt(String assetNo) {
		return selectOne("AssetUnit.selectAssetNoCnt", assetNo);
	}

	public List<AssetUnitVO> selectStoredUnits(String itemCode) {
		return selectList("AssetUnit.selectStoredUnits", itemCode);
	}

	public void insertUnit(AssetUnitVO vo, String actorId) {
		Map<String, Object> param = new HashMap<>();
		param.put("vo", vo);
		param.put("actorId", actorId);
		insert("AssetUnit.insertUnit", param);
	}

	public int updateSttus(String assetNo, String sttus, String userId, String actorId) {
		Map<String, Object> param = new HashMap<>();
		param.put("assetNo", assetNo);
		param.put("sttus", sttus);
		param.put("userId", userId);
		param.put("actorId", actorId);
		return update("AssetUnit.updateSttus", param);
	}

	public void insertHist(AssetUnitHistVO vo) {
		insert("AssetUnit.insertHist", vo);
	}

	public List<AssetUnitHistVO> selectHistList(String assetNo) {
		return selectList("AssetUnit.selectHistList", assetNo);
	}
}
