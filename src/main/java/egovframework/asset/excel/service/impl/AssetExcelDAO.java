package egovframework.asset.excel.service.impl;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

import egovframework.asset.item.service.AssetItemVO;
import jakarta.annotation.Resource;

/**
 * 엑셀 일괄등록 검사용 조회 DAO. 목록 인자는 비어 있으면 안 된다(호출자가 거른다).
 */
@Repository("assetExcelDAO")
public class AssetExcelDAO extends EgovAbstractMapper {

	@Override
	@Resource(name = "egov.sqlSession")
	public void setSqlSessionFactory(SqlSessionFactory sqlSession) {
		super.setSqlSessionFactory(sqlSession);
	}

	/** 사용 중인 공통 상세코드 */
	public List<String> selectActiveCodes(String codeId) {
		return selectList("AssetExcel.selectActiveCodes", codeId);
	}

	/** 사용 중 품목 가운데 이름이 같은 것(DB 대조 규칙: 대소문자 무시) */
	public List<String> selectActiveItemNames(Collection<String> names) {
		return selectList("AssetExcel.selectActiveItemNames", names);
	}

	/** 이미 있는 자산번호(폐기 포함) */
	public List<String> selectExistingAssetNos(Collection<String> assetNos) {
		return selectList("AssetExcel.selectExistingAssetNos", assetNos);
	}

	/** 품목코드·이름·유형·사용 여부 */
	public List<AssetItemVO> selectItems(Collection<String> itemCodes) {
		return selectList("AssetExcel.selectItems", itemCodes);
	}
}
