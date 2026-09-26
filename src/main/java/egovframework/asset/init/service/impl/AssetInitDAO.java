package egovframework.asset.init.service.impl;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

/**
 * 기동 시 초기화 DAO.
 */
@Repository("assetInitDAO")
public class AssetInitDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	public int selectSetupApplied(String setupKey) {
		return selectOne("AssetInit.selectSetupApplied", setupKey);
	}

	public void insertSetup(String setupKey) {
		insert("AssetInit.insertSetup", setupKey);
	}

	public List<String> selectPendingDemoUserIds() {
		return selectList("AssetInit.selectPendingDemoUserIds");
	}

	public int updateDemoPassword(String emplyrId, String password) {
		return update("AssetInit.updateDemoPassword", Map.of("emplyrId", emplyrId, "password", password));
	}
}
