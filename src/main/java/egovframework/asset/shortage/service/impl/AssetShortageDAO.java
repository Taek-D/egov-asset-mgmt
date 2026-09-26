package egovframework.asset.shortage.service.impl;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import egovframework.asset.shortage.service.AssetShortageRunVO;
import egovframework.asset.shortage.service.AssetShortageVO;
import jakarta.annotation.Resource;

/**
 * 재고 부족 집계 DAO.
 */
@Repository("assetShortageDAO")
public class AssetShortageDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	/** 사용 중이면서 가용 &lt; 최소 수량인 품목(잠그지 않는 읽기) */
	public List<AssetShortageVO> selectShortageTargets() {
		return selectList("AssetShortage.selectShortageTargets");
	}

	public void insertRun(AssetShortageRunVO run) {
		insert("AssetShortage.insertRun", run);
	}

	public void insertShortageList(String runId, List<AssetShortageVO> items) {
		insert("AssetShortage.insertShortageList", Map.of("runId", runId, "items", items));
	}

	public AssetShortageRunVO selectLatestRun() {
		return selectOne("AssetShortage.selectLatestRun");
	}

	public List<AssetShortageVO> selectShortageList(AssetShortageVO searchVO) {
		return selectList("AssetShortage.selectShortageList", searchVO);
	}

	public int selectShortageListTotCnt(AssetShortageVO searchVO) {
		return selectOne("AssetShortage.selectShortageListTotCnt", searchVO);
	}
}
