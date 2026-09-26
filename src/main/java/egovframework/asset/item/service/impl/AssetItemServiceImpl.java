package egovframework.asset.item.service.impl;

import java.util.List;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.cmmn.exception.FdlException;
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.stock.service.AssetStockService;
import jakarta.annotation.Resource;

/**
 * 품목 관리 구현. 변경은 품목 행을 잠근 뒤 확인하고 바꾼다(잠금은 재고 서비스의 규약을 그대로 쓴다).
 */
@Service("assetItemService")
public class AssetItemServiceImpl extends EgovAbstractServiceImpl implements AssetItemService {

	@Resource(name = "assetItemDAO")
	private AssetItemDAO assetItemDAO;

	@Resource(name = "assetStockService")
	private AssetStockService assetStockService;

	@Resource(name = "assetItemIdGnrService")
	private EgovIdGnrService assetItemIdGnrService;

	@Override
	public List<AssetItemVO> selectItemList(AssetItemVO searchVO) {
		return assetItemDAO.selectItemList(searchVO);
	}

	@Override
	public int selectItemListTotCnt(AssetItemVO searchVO) {
		return assetItemDAO.selectItemListTotCnt(searchVO);
	}

	@Override
	public AssetItemVO selectItem(String itemCode) {
		AssetItemVO vo = assetItemDAO.selectItem(itemCode);
		if (vo == null) {
			throw new AssetItemException("품목이 없습니다: " + itemCode);
		}
		return vo;
	}

	@Override
	public String insertItem(AssetItemVO vo, String actorId) {
		normalize(vo);
		requireNameFree(vo.getItemNm(), null);
		try {
			vo.setItemCode(assetItemIdGnrService.getNextStringId());
		} catch (FdlException e) {
			throw new IllegalStateException("품목코드를 만들 수 없습니다.", e);
		}
		try {
			assetItemDAO.insertItem(vo, actorId);
		} catch (DuplicateKeyException e) {
			throw nameTaken(vo.getItemNm());
		}
		return vo.getItemCode();
	}

	@Override
	public void updateItem(AssetItemVO vo, String actorId) {
		normalize(vo);
		AssetItemVO current = lock(vo.getItemCode());
		if (!current.getItemTy().equals(vo.getItemTy()) && assetItemDAO.selectHistCntLocked(vo.getItemCode()) > 0) {
			throw new AssetItemException("재고 변동·신청·자산 이력이 있는 품목은 유형을 바꿀 수 없습니다.");
		}
		if ("Y".equals(current.getUseAt())) {
			requireNameFree(vo.getItemNm(), vo.getItemCode());
		}
		try {
			assetItemDAO.updateItem(vo, actorId);
		} catch (DuplicateKeyException e) {
			throw nameTaken(vo.getItemNm());
		}
	}

	@Override
	public void deactivateItem(String itemCode, String actorId) {
		lock(itemCode);
		assetItemDAO.updateUseAt(itemCode, "N", actorId);
	}

	@Override
	public void activateItem(String itemCode, String actorId) {
		AssetItemVO current = lock(itemCode);
		if ("Y".equals(current.getUseAt())) {
			return;
		}
		requireNameFree(current.getItemNm(), itemCode);
		try {
			assetItemDAO.updateUseAt(itemCode, "Y", actorId);
		} catch (DuplicateKeyException e) {
			throw nameTaken(current.getItemNm());
		}
	}

	@Override
	public void deleteItem(String itemCode) {
		lock(itemCode);
		if (assetItemDAO.selectHistCntLocked(itemCode) > 0) {
			throw new AssetItemException("재고 변동·신청·자산 이력이 있는 품목은 삭제할 수 없습니다. 사용 중지로 처리하세요.");
		}
		assetItemDAO.deleteItem(itemCode);
	}

	private AssetItemVO lock(String itemCode) {
		return assetStockService.lockItems(List.of(itemCode)).get(itemCode);
	}

	private void requireNameFree(String itemNm, String selfCode) {
		if (assetItemDAO.selectActiveNameCnt(itemNm, selfCode) > 0) {
			throw nameTaken(itemNm);
		}
	}

	private static AssetItemException nameTaken(String itemNm) {
		return new AssetItemException("사용 중인 품목 가운데 같은 이름(" + itemNm + ")이 이미 있습니다.");
	}

	/** 입력 검증·정리(서비스 경계) */
	private static void normalize(AssetItemVO vo) {
		String name = vo.getItemNm() == null ? "" : vo.getItemNm().trim();
		if (name.isEmpty() || name.length() > 100) {
			throw new AssetItemException("품목명은 1~100자로 입력하세요.");
		}
		vo.setItemNm(name);
		if (!AssetItemVO.TY_QTY.equals(vo.getItemTy()) && !AssetItemVO.TY_UNIT.equals(vo.getItemTy())) {
			throw new AssetItemException("품목 유형을 선택하세요.");
		}
		if (vo.getMinQty() == null) {
			vo.setMinQty(0);
		}
		if (vo.getMinQty() < 0) {
			throw new AssetItemException("최소 수량은 0 이상이어야 합니다.");
		}
		vo.setClCode(blankToNull(vo.getClCode()));
		vo.setUnitCode(blankToNull(vo.getUnitCode()));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
