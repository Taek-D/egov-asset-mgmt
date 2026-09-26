package egovframework.asset.item.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import egovframework.asset.item.service.AssetItemVO;
import jakarta.annotation.Resource;

/**
 * 품목 DAO.
 */
@Repository("assetItemDAO")
public class AssetItemDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	public List<AssetItemVO> selectItemList(AssetItemVO searchVO) {
		return selectList("AssetItem.selectItemList", searchVO);
	}

	public int selectItemListTotCnt(AssetItemVO searchVO) {
		return selectOne("AssetItem.selectItemListTotCnt", searchVO);
	}

	public AssetItemVO selectItem(String itemCode) {
		return selectOne("AssetItem.selectItem", itemCode);
	}

	/** @param itemCode 제외할 자기 품목코드(없으면 null) */
	public int selectActiveNameCnt(String itemNm, String itemCode) {
		Map<String, Object> param = new HashMap<>();
		param.put("itemNm", itemNm);
		param.put("itemCode", itemCode);
		return selectOne("AssetItem.selectActiveNameCnt", param);
	}

	public int selectHistCntLocked(String itemCode) {
		return selectOne("AssetItem.selectHistCntLocked", itemCode);
	}

	public void insertItem(AssetItemVO vo, String actorId) {
		insert("AssetItem.insertItem", Map.of("vo", vo, "actorId", actorId));
	}

	public void updateItem(AssetItemVO vo, String actorId) {
		update("AssetItem.updateItem", Map.of("vo", vo, "actorId", actorId));
	}

	public void updateUseAt(String itemCode, String useAt, String actorId) {
		update("AssetItem.updateUseAt", Map.of("itemCode", itemCode, "useAt", useAt, "actorId", actorId));
	}

	public void deleteItem(String itemCode) {
		delete("AssetItem.deleteItem", itemCode);
	}
}
