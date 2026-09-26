package egovframework.asset.stock.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.stock.service.AssetStockHistVO;
import jakarta.annotation.Resource;

/**
 * 재고 변동 DAO.
 */
@Repository("assetStockDAO")
public class AssetStockDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	public List<AssetItemVO> selectItemsForUpdate(Collection<String> itemCodes) {
		return selectList("AssetStock.selectItemsForUpdate", itemCodes);
	}

	public List<AssetItemVO> selectStoredCountsLocked(Collection<String> itemCodes) {
		return selectList("AssetStock.selectStoredCountsLocked", itemCodes);
	}

	public List<AssetItemVO> selectAvailable(Collection<String> itemCodes) {
		return selectList("AssetStock.selectAvailable", itemCodes);
	}

	public int updateQty(String itemCode, int stockQty, int reservedQty, String actorId) {
		return update("AssetStock.updateQty",
				Map.of("itemCode", itemCode, "stockQty", stockQty, "reservedQty", reservedQty, "actorId", actorId));
	}

	public void insertHist(AssetStockHistVO vo) {
		insert("AssetStock.insertHist", vo);
	}

	public List<AssetStockHistVO> selectHistList(AssetStockHistVO searchVO) {
		return selectList("AssetStock.selectHistList", searchVO);
	}

	public int selectHistListTotCnt(AssetStockHistVO searchVO) {
		return selectOne("AssetStock.selectHistListTotCnt", searchVO);
	}
}
