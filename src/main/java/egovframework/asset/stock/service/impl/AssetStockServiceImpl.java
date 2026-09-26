package egovframework.asset.stock.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.cmmn.exception.FdlException;
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.springframework.stereotype.Service;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.stock.service.AssetStockHistVO;
import egovframework.asset.stock.service.AssetStockService;
import jakarta.annotation.Resource;

/**
 * 재고·가용 계산과 잠금 규약 구현. 모든 수량 변경은 품목 행 잠금 → 재확인 → 변경 → 이력 한 행.
 */
@Service("assetStockService")
public class AssetStockServiceImpl extends EgovAbstractServiceImpl implements AssetStockService {

	@Resource(name = "assetStockDAO")
	private AssetStockDAO assetStockDAO;

	@Resource(name = "assetStockHistIdGnrService")
	private EgovIdGnrService assetStockHistIdGnrService;

	@Override
	public int getAvailableQty(String itemCode) {
		Integer avail = getAvailableQtys(List.of(itemCode)).get(itemCode);
		if (avail == null) {
			throw notFound(itemCode);
		}
		return avail;
	}

	@Override
	public Map<String, Integer> getAvailableQtys(Collection<String> itemCodes) {
		Map<String, Integer> result = new HashMap<>();
		if (itemCodes.isEmpty()) {
			return result;
		}
		for (AssetItemVO vo : assetStockDAO.selectAvailable(new TreeSet<>(itemCodes))) {
			result.put(vo.getItemCode(), vo.getAvailQty());
		}
		return result;
	}

	@Override
	public Map<String, AssetItemVO> lockItems(Collection<String> itemCodes) {
		Map<String, AssetItemVO> result = new LinkedHashMap<>();
		if (itemCodes.isEmpty()) {
			return result;
		}
		TreeSet<String> codes = new TreeSet<>(itemCodes);
		for (AssetItemVO vo : assetStockDAO.selectItemsForUpdate(codes)) {
			result.put(vo.getItemCode(), vo);
		}
		for (String code : codes) {
			if (!result.containsKey(code)) {
				throw notFound(code);
			}
		}
		List<String> unitCodes = result.values().stream().filter(AssetItemVO::isUnit).map(AssetItemVO::getItemCode).toList();
		if (!unitCodes.isEmpty()) {
			for (AssetItemVO stored : assetStockDAO.selectStoredCountsLocked(unitCodes)) {
				result.get(stored.getItemCode()).setStoredQty(stored.getStoredQty());
			}
		}
		for (AssetItemVO vo : result.values()) {
			vo.setAvailQty(vo.isUnit() ? vo.getStoredQty() - vo.getReservedQty() : vo.getStockQty());
		}
		return result;
	}

	private AssetItemVO lockOne(String itemCode) {
		return lockItems(List.of(itemCode)).get(itemCode);
	}

	@Override
	public void reserve(String itemCode, int qty, String reqNo, String actorId) {
		requirePositive(qty, "확보 수량");
		AssetItemVO item = lockOne(itemCode);
		if (item.getAvailQty() < qty) {
			throw new AssetItemException(String.format("[%s] 가용 수량이 부족합니다. (가용 %d, 요청 %d, 부족 %d)",
					item.getItemNm(), item.getAvailQty(), qty, qty - item.getAvailQty()));
		}
		if (item.isUnit()) {
			change(item, item.getStockQty(), item.getReservedQty() + qty, HIST_RESERVE, -qty, null, reqNo, actorId);
		} else {
			change(item, item.getStockQty() - qty, item.getReservedQty(), HIST_RESERVE, -qty, null, reqNo, actorId);
		}
	}

	@Override
	public void release(String itemCode, int qty, String reqNo, String actorId, String reason) {
		requirePositive(qty, "복구 수량");
		AssetItemVO item = lockOne(itemCode);
		if (item.isUnit()) {
			requireReserved(item, qty);
			change(item, item.getStockQty(), item.getReservedQty() - qty, HIST_RELEASE, qty, reason, reqNo, actorId);
		} else {
			change(item, item.getStockQty() + qty, item.getReservedQty(), HIST_RELEASE, qty, reason, reqNo, actorId);
		}
	}

	@Override
	public void deliverUnits(String itemCode, int count, String reqNo, String actorId) {
		requirePositive(count, "지급 대수");
		AssetItemVO item = lockOne(itemCode);
		requireUnit(item);
		requireReserved(item, count);
		change(item, item.getStockQty(), item.getReservedQty() - count, HIST_DELIVER, 0, count + "대 지급", reqNo, actorId);
	}

	@Override
	public void recordUnitIn(String itemCode, String assetNo, String actorId) {
		AssetItemVO item = lockOne(itemCode);
		requireUnit(item);
		change(item, item.getStockQty(), item.getReservedQty(), HIST_IN, 1, "자산 등록 " + assetNo, null, actorId);
	}

	@Override
	public void receive(String itemCode, int qty, String reason, String actorId) {
		requirePositive(qty, "입고 수량");
		AssetItemVO item = lockOne(itemCode);
		requireQty(item, "입고");
		change(item, item.getStockQty() + qty, item.getReservedQty(), HIST_IN, qty, reason, null, actorId);
	}

	@Override
	public void adjust(String itemCode, int delta, String reason, String actorId) {
		if (delta == 0) {
			throw new AssetItemException("조정 수량은 0이 아니어야 합니다.");
		}
		if (reason == null || reason.isBlank()) {
			throw new AssetItemException("조정 사유를 입력하세요.");
		}
		AssetItemVO item = lockOne(itemCode);
		requireQty(item, "조정");
		change(item, item.getStockQty() + delta, item.getReservedQty(), HIST_ADJUST, delta, reason.trim(), null, actorId);
	}

	/**
	 * 잠근 품목의 수량을 바꾸고 이력 한 행을 남긴다. 변동 후 값: 수량 비품 = 재고, 개별 자산 = 가용.
	 * 결과 재고·확보·가용 가운데 하나라도 0 미만이면 거부.
	 */
	private void change(AssetItemVO item, int stockQty, int reservedQty, String histTy, int changeQty, String reason,
			String reqNo, String actorId) {
		int after = item.isUnit() ? item.getStoredQty() - reservedQty : stockQty;
		if (stockQty < 0 || reservedQty < 0 || after < 0) {
			throw new AssetItemException(String.format("[%s] 처리 후 수량이 0 미만이 되어 처리할 수 없습니다. (현재 %s %d)",
					item.getItemNm(), item.isUnit() ? "가용" : "재고", item.getAvailQty()));
		}
		assetStockDAO.updateQty(item.getItemCode(), stockQty, reservedQty, actorId);

		AssetStockHistVO hist = new AssetStockHistVO();
		hist.setStockHistId(nextHistId());
		hist.setItemCode(item.getItemCode());
		hist.setHistTy(histTy);
		hist.setChangeQty(changeQty);
		hist.setAfterQty(after);
		hist.setResn(reason == null || reason.isBlank() ? null : reason);
		hist.setReqNo(reqNo);
		hist.setProcessId(actorId);
		assetStockDAO.insertHist(hist);
	}

	private String nextHistId() {
		try {
			return assetStockHistIdGnrService.getNextStringId();
		} catch (FdlException e) {
			throw new IllegalStateException("재고 이력 번호를 만들 수 없습니다.", e);
		}
	}

	private static void requirePositive(int qty, String what) {
		if (qty < 1) {
			throw new AssetItemException(what + "은(는) 1 이상이어야 합니다.");
		}
	}

	private static void requireUnit(AssetItemVO item) {
		if (!item.isUnit()) {
			throw new AssetItemException("[" + item.getItemNm() + "] 개별 자산 품목이 아닙니다.");
		}
	}

	private static void requireQty(AssetItemVO item, String what) {
		if (item.isUnit()) {
			throw new AssetItemException("[" + item.getItemNm() + "] 개별 자산은 " + what + " 대신 자산 등록으로 관리합니다.");
		}
	}

	private static void requireReserved(AssetItemVO item, int qty) {
		if (item.getReservedQty() < qty) {
			throw new AssetItemException(String.format("[%s] 확보 수량(%d)보다 많이 해제할 수 없습니다. (요청 %d)",
					item.getItemNm(), item.getReservedQty(), qty));
		}
	}

	private static AssetItemException notFound(String itemCode) {
		return new AssetItemException("품목이 없습니다: " + itemCode);
	}

	@Override
	public List<AssetStockHistVO> selectHistList(AssetStockHistVO searchVO) {
		return assetStockDAO.selectHistList(searchVO);
	}

	@Override
	public int selectHistListTotCnt(AssetStockHistVO searchVO) {
		return assetStockDAO.selectHistListTotCnt(searchVO);
	}
}
