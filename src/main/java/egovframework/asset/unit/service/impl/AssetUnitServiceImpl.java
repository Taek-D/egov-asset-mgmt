package egovframework.asset.unit.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.cmmn.exception.FdlException;
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.asset.unit.service.AssetUnitHistVO;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.asset.unit.service.AssetUnitVO;
import jakarta.annotation.Resource;

/**
 * 개별 자산 구현. 모든 변경은 품목 잠금 → 자산 행 잠금 → 재확인 → 변경 → 자산 이력 한 행.
 */
@Service("assetUnitService")
public class AssetUnitServiceImpl extends EgovAbstractServiceImpl implements AssetUnitService {

	private static final String STORED = AssetUnitVO.STORED;
	private static final String IN_USE = AssetUnitVO.IN_USE;
	private static final String REPAIR = AssetUnitVO.REPAIR;
	private static final String DISPOSED = AssetUnitVO.DISPOSED;

	/** 자산담당자가 직접 할 수 있는 전이(보관 → 사용은 지급 {@link #issueUnits}에서만) */
	private static final Map<String, Set<String>> ALLOWED = Map.of(
			IN_USE, Set.of(STORED),
			STORED, Set.of(REPAIR, DISPOSED),
			REPAIR, Set.of(STORED, DISPOSED));

	private static final Map<String, String> STTUS_NM = Map.of(STORED, "보관", IN_USE, "사용", REPAIR, "수리", DISPOSED, "폐기");

	@Resource(name = "assetUnitDAO")
	private AssetUnitDAO assetUnitDAO;

	@Resource(name = "assetStockService")
	private AssetStockService assetStockService;

	@Resource(name = "assetUnitHistIdGnrService")
	private EgovIdGnrService assetUnitHistIdGnrService;

	@Override
	public List<AssetUnitVO> selectUnitList(AssetUnitVO searchVO) {
		return assetUnitDAO.selectUnitList(searchVO);
	}

	@Override
	public int selectUnitListTotCnt(AssetUnitVO searchVO) {
		return assetUnitDAO.selectUnitListTotCnt(searchVO);
	}

	@Override
	public AssetUnitVO selectUnit(String assetNo) {
		AssetUnitVO vo = assetUnitDAO.selectUnit(assetNo);
		if (vo == null) {
			throw notFound(assetNo);
		}
		return vo;
	}

	@Override
	public List<AssetUnitHistVO> selectUnitHistList(String assetNo) {
		return assetUnitDAO.selectHistList(assetNo);
	}

	@Override
	public List<AssetUnitVO> selectStoredUnits(String itemCode) {
		return assetUnitDAO.selectStoredUnits(itemCode);
	}

	@Override
	public void registerUnit(AssetUnitVO vo, String actorId) {
		String assetNo = vo.getAssetNo() == null ? "" : vo.getAssetNo().trim();
		if (assetNo.isEmpty() || assetNo.length() > 50) {
			throw new AssetItemException("자산번호는 1~50자로 입력하세요.");
		}
		if (vo.getItemCode() == null || vo.getItemCode().isBlank()) {
			throw new AssetItemException("품목을 선택하세요.");
		}
		String rm = vo.getRm() == null || vo.getRm().isBlank() ? null : vo.getRm().trim();
		if (rm != null && rm.length() > 500) {
			throw new AssetItemException("비고는 500자 이내로 입력하세요.");
		}
		vo.setAssetNo(assetNo);
		vo.setRm(rm);

		AssetItemVO item = assetStockService.lockItems(List.of(vo.getItemCode())).get(vo.getItemCode());
		if (!item.isUnit()) {
			throw new AssetItemException("[" + item.getItemNm() + "] 개별 자산 품목이 아닙니다. 수량 비품은 품목 화면에서 입고합니다.");
		}
		if (!"Y".equals(item.getUseAt())) {
			throw new AssetItemException("[" + item.getItemNm() + "] 사용 중지된 품목에는 자산을 등록할 수 없습니다.");
		}
		if (assetUnitDAO.selectAssetNoCnt(assetNo) > 0) {
			throw assetNoTaken(assetNo);
		}
		try {
			assetUnitDAO.insertUnit(vo, actorId);
		} catch (DuplicateKeyException e) {
			// 동시에 같은 번호를 등록한 경우(다른 품목이면 품목 잠금이 막아 주지 않는다)
			throw assetNoTaken(assetNo);
		}
		insertHist(assetNo, null, STORED, "등록", null, actorId);
		assetStockService.recordUnitIn(vo.getItemCode(), assetNo, actorId);
	}

	@Override
	public void changeStatus(String assetNo, String toSttus, String reason, String actorId) {
		String resn = reason == null || reason.isBlank() ? null : reason.trim();
		if (DISPOSED.equals(toSttus) && resn == null) {
			throw new AssetItemException("폐기 사유를 입력하세요. (분실·파손도 폐기로 처리하고 사유를 적습니다)");
		}
		// 품목코드는 바뀌지 않으므로 잠그지 않고 읽어도 된다. 자산 행은 품목 잠금 뒤에 잠근다
		AssetUnitVO current = selectUnit(assetNo);
		AssetItemVO item = assetStockService.lockItems(List.of(current.getItemCode())).get(current.getItemCode());
		AssetUnitVO unit = assetUnitDAO.selectUnitsForUpdate(List.of(assetNo)).get(0);

		String from = unit.getSttus();
		if (toSttus == null || !ALLOWED.getOrDefault(from, Set.of()).contains(toSttus)) {
			throw new AssetItemException(String.format("[%s] %s 상태에서 %s(으)로 바꿀 수 없습니다.%s", assetNo, nm(from), nm(toSttus),
					IN_USE.equals(from) ? " 사용 중인 자산은 먼저 반납하세요." : ""));
		}
		// 보관 자산을 빼는 전이는 확보 수량을 침범하지 못한다(수리 → 폐기는 보관 수를 바꾸지 않으므로 항상 가능)
		if (STORED.equals(from) && item.getStoredQty() - 1 < item.getReservedQty()) {
			throw new AssetItemException(String.format("[%s] 승인으로 확보된 자산이라 %s 처리할 수 없습니다. (보관 %d, 확보 %d)",
					item.getItemNm(), nm(toSttus), item.getStoredQty(), item.getReservedQty()));
		}
		assetUnitDAO.updateSttus(assetNo, toSttus, null, actorId);
		insertHist(assetNo, from, toSttus, resn, null, actorId);
	}

	@Override
	public void issueUnits(String reqNo, String itemCode, List<String> assetNos, String applicantId, String actorId) {
		if (reqNo == null || reqNo.isBlank() || applicantId == null || applicantId.isBlank()) {
			throw new AssetItemException("신청서와 신청자가 있어야 지급할 수 있습니다.");
		}
		if (assetNos == null || assetNos.isEmpty()) {
			throw new AssetItemException("지급할 자산을 선택하세요.");
		}
		if (new HashSet<>(assetNos).size() != assetNos.size()) {
			throw new AssetItemException("같은 자산을 두 번 선택했습니다.");
		}
		AssetItemVO item = assetStockService.lockItems(List.of(itemCode)).get(itemCode);
		List<AssetUnitVO> units = assetUnitDAO.selectUnitsForUpdate(new TreeSet<>(assetNos));
		if (units.size() != assetNos.size()) {
			Set<String> missing = new TreeSet<>(assetNos);
			units.forEach(u -> missing.remove(u.getAssetNo()));
			throw new AssetItemException("없는 자산번호입니다: " + String.join(", ", missing));
		}
		for (AssetUnitVO unit : units) {
			if (!itemCode.equals(unit.getItemCode())) {
				throw new AssetItemException("[" + unit.getAssetNo() + "] " + item.getItemNm() + " 품목의 자산이 아닙니다.");
			}
			if (!STORED.equals(unit.getSttus())) {
				throw new AssetItemException("[" + unit.getAssetNo() + "] " + nm(unit.getSttus()) + " 상태라 지급할 수 없습니다. 보관 중인 자산만 지급합니다.");
			}
		}
		for (AssetUnitVO unit : units) {
			assetUnitDAO.updateSttus(unit.getAssetNo(), IN_USE, applicantId, actorId);
			insertHist(unit.getAssetNo(), STORED, IN_USE, "지급", reqNo, actorId);
		}
	}

	private void insertHist(String assetNo, String before, String after, String resn, String reqNo, String actorId) {
		AssetUnitHistVO hist = new AssetUnitHistVO();
		try {
			hist.setUnitHistId(assetUnitHistIdGnrService.getNextStringId());
		} catch (FdlException e) {
			throw new IllegalStateException("자산 이력 번호를 만들 수 없습니다.", e);
		}
		hist.setAssetNo(assetNo);
		hist.setBeforeSttus(before);
		hist.setAfterSttus(after);
		hist.setResn(resn);
		hist.setReqNo(reqNo);
		hist.setProcessId(actorId);
		assetUnitDAO.insertHist(hist);
	}

	private static String nm(String sttus) {
		return sttus == null ? "(없음)" : STTUS_NM.getOrDefault(sttus, sttus);
	}

	private static AssetItemException notFound(String assetNo) {
		return new AssetItemException("자산이 없습니다: " + assetNo);
	}

	private static AssetItemException assetNoTaken(String assetNo) {
		return new AssetItemException("이미 사용된 자산번호입니다(폐기된 자산 포함): " + assetNo);
	}
}
