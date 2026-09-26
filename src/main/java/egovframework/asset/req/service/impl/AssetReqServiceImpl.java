package egovframework.asset.req.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.springframework.stereotype.Service;

import egovframework.asset.depthead.service.DeptHeadService;
import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.req.service.AssetReqHistVO;
import egovframework.asset.req.service.AssetReqLineVO;
import egovframework.asset.req.service.AssetReqService;
import egovframework.asset.req.service.AssetReqVO;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.asset.unit.service.AssetUnitService;
import jakarta.annotation.Resource;

/**
 * 신청·승인·지급·취소 구현. 상태를 바꾸는 처리는 모두 신청서 행 잠금 → 품목 줄 잠금 → 품목 행 잠금(코드 오름차순) → 재확인 → 변경 → 이력.
 */
@Service("assetReqService")
public class AssetReqServiceImpl extends EgovAbstractServiceImpl implements AssetReqService {

	private static final String ALREADY = "이미 처리된 신청입니다.";
	private static final String ROLE_ASSET = "ROLE_ASSET";

	@Resource(name = "assetReqDAO")
	private AssetReqDAO assetReqDAO;

	@Resource(name = "assetStockService")
	private AssetStockService assetStockService;

	@Resource(name = "assetUnitService")
	private AssetUnitService assetUnitService;

	@Resource(name = "deptHeadService")
	private DeptHeadService deptHeadService;

	@Resource(name = "assetReqIdGnrService")
	private EgovIdGnrService assetReqIdGnrService;

	@Resource(name = "assetReqHistIdGnrService")
	private EgovIdGnrService assetReqHistIdGnrService;

	// ---------- 제출 ----------

	@Override
	public String submit(AssetReqVO vo, String applicantId) throws Exception {
		AssetReqVO applicant = applicantId == null ? null : assetReqDAO.selectApplicant(applicantId);
		if (applicant == null) {
			throw new AssetItemException("업무사용자(직원) 계정만 신청할 수 있습니다.");
		}
		if (applicant.getOrgnztId() == null) {
			throw new AssetItemException("부서에 소속된 직원만 신청할 수 있습니다. 관리자에게 부서 지정을 요청하세요.");
		}
		String resn = trimToNull(vo.getReqResn());
		if (resn != null && resn.length() > 1000) {
			throw new AssetItemException("신청 사유는 1000자 이내로 입력하세요.");
		}

		// 같은 품목은 한 줄로 합친다(수량 합산). 품목·수량이 모두 빈 줄은 무시
		Map<String, Integer> merged = new TreeMap<>();
		for (AssetReqLineVO line : vo.getLines()) {
			String code = trimToNull(line.getItemCode());
			if (code == null && line.getReqQty() == null) {
				continue;
			}
			if (code == null) {
				throw new AssetItemException("수량을 적은 줄의 품목을 선택하세요.");
			}
			if (line.getReqQty() == null || line.getReqQty() < 1) {
				throw new AssetItemException("신청 수량은 1 이상이어야 합니다.");
			}
			merged.merge(code, line.getReqQty(), Integer::sum);
		}
		if (merged.isEmpty()) {
			throw new AssetItemException("신청 품목을 1개 이상 입력하세요.");
		}
		if (merged.values().stream().anyMatch(q -> q < 1)) {
			throw new AssetItemException("신청 수량이 너무 큽니다.");
		}
		if (!deptHeadService.hasApprover(applicant.getOrgnztId(), applicantId)) {
			throw new AssetItemException("지금 이 신청을 승인할 사람이 없어 제출할 수 없습니다. 관리자에게 부서장 지정을 요청하세요.");
		}
		// 사용 중지와 겹치지 않도록 품목을 잠그고 확인한다(가용은 확인하지 않는다 — 재고 확인은 승인 때)
		for (AssetItemVO item : assetStockService.lockItems(merged.keySet()).values()) {
			if (!"Y".equals(item.getUseAt())) {
				throw new AssetItemException("[" + item.getItemNm() + "] 사용 중지된 품목은 신청할 수 없습니다.");
			}
		}

		String reqNo = assetReqIdGnrService.getNextStringId();
		AssetReqVO header = new AssetReqVO();
		header.setReqNo(reqNo);
		header.setApplcntId(applicantId);
		header.setOrgnztId(applicant.getOrgnztId());
		header.setReqResn(resn);
		assetReqDAO.insertReq(header);
		for (Map.Entry<String, Integer> e : merged.entrySet()) {
			AssetReqLineVO line = new AssetReqLineVO();
			line.setReqNo(reqNo);
			line.setItemCode(e.getKey());
			line.setReqQty(e.getValue());
			assetReqDAO.insertLine(line);
		}
		insertHist(reqNo, null, AssetReqVO.PENDING, null, applicantId);
		return reqNo;
	}

	// ---------- 승인·반려 ----------

	@Override
	public void approve(String reqNo, List<AssetReqLineVO> decisions, String approverId) throws Exception {
		AssetReqVO req = lockReq(reqNo);
		if (!AssetReqVO.PENDING.equals(req.getSttus())) {
			throw new AssetItemException(ALREADY);
		}
		if (req.getApplcntId().equals(approverId)) {
			throw new AssetItemException("자기 신청은 승인·반려할 수 없습니다.");
		}
		if (!deptHeadService.canApprove(approverId, req.getOrgnztId(), req.getApplcntId())) {
			throw new AssetItemException("이 신청을 승인할 권한이 없습니다. 지금 승인자만 처리할 수 있습니다.");
		}
		List<AssetReqLineVO> lines = assetReqDAO.selectLinesForUpdate(reqNo);

		Map<String, AssetReqLineVO> byItem = new HashMap<>();
		for (AssetReqLineVO d : decisions == null ? List.<AssetReqLineVO>of() : decisions) {
			if (d.getItemCode() != null) {
				byItem.put(d.getItemCode().trim(), d);
			}
		}
		List<String> undecided = new ArrayList<>();
		Map<String, Integer> approvedQty = new LinkedHashMap<>();
		for (AssetReqLineVO line : lines) {
			AssetReqLineVO d = byItem.get(line.getItemCode().trim());
			String result = d == null ? null : d.getLineSttus();
			if (AssetReqLineVO.APPROVED.equals(result)) {
				if (d.getAprvQty() == null || d.getAprvQty() < 1 || d.getAprvQty() > line.getReqQty()) {
					throw new AssetItemException(String.format("[%s] 승인 수량은 1 이상 신청 수량(%d) 이하로 입력하세요.", line.getItemNm(), line.getReqQty()));
				}
				line.setLineSttus(AssetReqLineVO.APPROVED);
				line.setAprvQty(d.getAprvQty());
				line.setRejectResn(null);
				approvedQty.put(line.getItemCode(), d.getAprvQty());
			} else if (AssetReqLineVO.REJECTED.equals(result)) {
				String resn = trimToNull(d.getRejectResn());
				if (resn == null || resn.length() > 500) {
					throw new AssetItemException("[" + line.getItemNm() + "] 반려 사유를 500자 이내로 입력하세요.");
				}
				line.setLineSttus(AssetReqLineVO.REJECTED);
				line.setAprvQty(null);
				line.setRejectResn(resn);
			} else {
				undecided.add(line.getItemNm());
			}
		}
		if (!undecided.isEmpty()) {
			throw new AssetItemException("모든 품목의 처리(승인 또는 반려)를 정하세요. 미결 품목: " + String.join(", ", undecided));
		}

		if (!approvedQty.isEmpty()) {
			// 승인 품목 전부를 잠그고 가용을 확인한다. 하나라도 모자라면 아무것도 바꾸지 않고 모자란 품목을 모두 알린다
			Map<String, AssetItemVO> items = assetStockService.lockItems(approvedQty.keySet());
			List<String> shortages = new ArrayList<>();
			for (AssetItemVO item : items.values()) {
				int qty = approvedQty.get(item.getItemCode());
				if (item.getAvailQty() < qty) {
					shortages.add(String.format("%s: 가용 %d, 승인 %d → %d개 부족", item.getItemNm(), item.getAvailQty(), qty,
							qty - item.getAvailQty()));
				}
			}
			if (!shortages.isEmpty()) {
				throw new AssetItemException("가용 수량이 모자라 승인할 수 없습니다. " + String.join(" / ", shortages));
			}
			for (Map.Entry<String, Integer> e : approvedQty.entrySet()) {
				assetStockService.reserve(e.getKey(), e.getValue(), reqNo, approverId);
			}
		}
		for (AssetReqLineVO line : lines) {
			assetReqDAO.updateLine(line);
		}
		String after = approvedQty.isEmpty() ? AssetReqVO.REJECTED : AssetReqVO.APPROVED;
		assetReqDAO.updateSttus(reqNo, after, approverId, true);
		insertHist(reqNo, AssetReqVO.PENDING, after, null, approverId);
	}

	// ---------- 신청취소 ----------

	@Override
	public void cancel(String reqNo, String applicantId) throws Exception {
		AssetReqVO req = lockReq(reqNo);
		if (!AssetReqVO.PENDING.equals(req.getSttus())) {
			throw new AssetItemException(ALREADY);
		}
		if (!req.getApplcntId().equals(applicantId)) {
			throw new AssetItemException("신청자 본인만 취소할 수 있습니다.");
		}
		// 규약대로 품목 행까지 잠근다(재고는 바꾸지 않는다)
		assetStockService.lockItems(itemCodes(assetReqDAO.selectLinesForUpdate(reqNo)));
		assetReqDAO.updateSttus(reqNo, AssetReqVO.CANCELED, applicantId, false);
		insertHist(reqNo, AssetReqVO.PENDING, AssetReqVO.CANCELED, null, applicantId);
	}

	// ---------- 지급 ----------

	@Override
	public void deliver(String reqNo, Map<String, List<String>> picks, String keeperId) throws Exception {
		requireKeeper(keeperId);
		AssetReqVO req = lockApproved(reqNo, "지급");
		List<AssetReqLineVO> approved = approvedLines(assetReqDAO.selectLinesForUpdate(reqNo));
		Map<String, AssetItemVO> items = assetStockService.lockItems(itemCodes(approved));

		Map<String, List<String>> chosen = new HashMap<>();
		(picks == null ? Map.<String, List<String>>of() : picks).forEach((code, nos) -> {
			if (nos != null && !nos.isEmpty()) {
				chosen.put(code, nos);
			}
		});
		// 개수를 모두 확인한 뒤에 바꾼다(메시지에 틀린 품목을 전부 담는다)
		List<String> wrong = new ArrayList<>();
		List<AssetReqLineVO> unitLines = new ArrayList<>();
		for (AssetReqLineVO line : approved) {
			AssetItemVO item = items.get(line.getItemCode());
			List<String> nos = chosen.remove(line.getItemCode());
			if (!item.isUnit()) {
				if (nos != null) {
					wrong.add("[" + item.getItemNm() + "] 수량 비품은 자산을 고르지 않습니다.");
				}
				continue;
			}
			int count = nos == null ? 0 : nos.size();
			if (count != line.getAprvQty()) {
				wrong.add(String.format("[%s] 승인 수량 %d대와 고른 자산 %d대가 다릅니다.", item.getItemNm(), line.getAprvQty(), count));
			}
			unitLines.add(line);
		}
		if (!chosen.isEmpty()) {
			wrong.add("지급 대상이 아닌 품목의 자산을 골랐습니다: " + String.join(", ", chosen.keySet()));
		}
		if (!wrong.isEmpty()) {
			throw new AssetItemException(String.join(" ", wrong));
		}
		for (AssetReqLineVO line : unitLines) {
			List<String> nos = picks.get(line.getItemCode());
			assetUnitService.issueUnits(reqNo, line.getItemCode(), nos, req.getApplcntId(), keeperId);
			assetStockService.deliverUnits(line.getItemCode(), nos.size(), reqNo, keeperId);
		}
		assetReqDAO.updateSttus(reqNo, AssetReqVO.DELIVERED, keeperId, false);
		insertHist(reqNo, AssetReqVO.APPROVED, AssetReqVO.DELIVERED, null, keeperId);
	}

	// ---------- 지급취소 ----------

	@Override
	public void cancelDelivery(String reqNo, String reason, String keeperId) throws Exception {
		requireKeeper(keeperId);
		String resn = trimToNull(reason);
		if (resn == null || resn.length() > 500) {
			throw new AssetItemException("지급취소 사유를 500자 이내로 입력하세요.");
		}
		lockApproved(reqNo, "지급취소");
		List<AssetReqLineVO> approved = approvedLines(assetReqDAO.selectLinesForUpdate(reqNo));
		assetStockService.lockItems(itemCodes(approved));
		for (AssetReqLineVO line : approved) {
			assetStockService.release(line.getItemCode(), line.getAprvQty(), reqNo, keeperId, resn);
		}
		assetReqDAO.updateSttus(reqNo, AssetReqVO.DELIVERY_CANCELED, keeperId, false);
		insertHist(reqNo, AssetReqVO.APPROVED, AssetReqVO.DELIVERY_CANCELED, resn, keeperId);
	}

	// ---------- 조회 ----------

	@Override
	public AssetReqVO selectReqDetail(String reqNo, String viewerId) throws Exception {
		AssetReqVO req = reqNo == null ? null : assetReqDAO.selectReq(reqNo);
		if (req == null) {
			throw new AssetItemException("신청서가 없습니다.");
		}
		boolean pending = AssetReqVO.PENDING.equals(req.getSttus());
		boolean keeper = isKeeper(viewerId);
		List<String> approvers = pending ? deptHeadService.selectApproverIds(req.getOrgnztId(), req.getApplcntId()) : List.of();
		boolean allowed = req.getApplcntId().equals(viewerId) || keeper || deptHeadService.isActiveAdmin(viewerId)
				|| approvers.contains(viewerId) || req.getOrgnztId().equals(deptHeadService.selectValidHeadOrgnztId(viewerId));
		if (!allowed) {
			throw new AssetItemException("이 신청을 조회할 권한이 없습니다.");
		}
		List<AssetReqLineVO> lines = assetReqDAO.selectLines(reqNo);
		Map<String, Integer> avail = assetStockService.getAvailableQtys(itemCodes(lines));
		lines.forEach(l -> l.setAvailQty(avail.get(l.getItemCode())));
		req.setLines(lines);
		req.setHistList(assetReqDAO.selectHistList(reqNo));
		req.setApproverIds(approvers);
		req.setNoApprover(pending && approvers.isEmpty());
		req.setApprovable(pending && approvers.contains(viewerId));
		req.setCancelable(pending && req.getApplcntId().equals(viewerId));
		req.setDeliverable(AssetReqVO.APPROVED.equals(req.getSttus()) && keeper);
		return req;
	}

	@Override
	public List<AssetReqVO> selectReqList(String scope, AssetReqVO searchVO, String userId) throws Exception {
		applyScope(scope, searchVO, userId);
		return assetReqDAO.selectReqList(searchVO);
	}

	@Override
	public int selectReqListTotCnt(String scope, AssetReqVO searchVO, String userId) throws Exception {
		applyScope(scope, searchVO, userId);
		return assetReqDAO.selectReqListTotCnt(searchVO);
	}

	/** 조회 범위를 서비스가 정한다(화면에서 넘어온 범위 값은 모두 덮어쓴다) */
	private void applyScope(String scope, AssetReqVO vo, String userId) throws Exception {
		vo.setScopeApplcntId(null);
		vo.setScopeOrgnztId(null);
		vo.setScopeInboxUserId(null);
		vo.setScopeInboxAdmin(false);
		if (userId == null) {
			throw new AssetItemException("로그인한 사용자만 조회할 수 있습니다.");
		}
		switch (scope == null ? "" : scope) {
		case SCOPE_MY:
			vo.setScopeApplcntId(userId);
			break;
		case SCOPE_DEPT:
			String orgnztId = deptHeadService.selectValidHeadOrgnztId(userId);
			if (orgnztId == null) {
				throw new AssetItemException("부서장이 아닙니다. 부서 신청 조회는 부서장만 할 수 있습니다.");
			}
			vo.setScopeOrgnztId(orgnztId);
			break;
		case SCOPE_INBOX:
			vo.setScopeInboxUserId(userId);
			vo.setScopeInboxAdmin(deptHeadService.isActiveAdmin(userId));
			break;
		case SCOPE_ALL:
			if (!deptHeadService.isActiveAdmin(userId) && !isKeeper(userId)) {
				throw new AssetItemException("전체 신청 조회 권한이 없습니다.");
			}
			break;
		default:
			throw new IllegalArgumentException("알 수 없는 조회 범위: " + scope);
		}
	}

	@Override
	public boolean isKeeper(String emplyrId) throws Exception {
		return emplyrId != null && assetReqDAO.hasAuthor(emplyrId, ROLE_ASSET);
	}

	@Override
	public List<AssetReqLineVO> selectReqLineReport(String fromDe, String toDe) throws Exception {
		return assetReqDAO.selectReqLineReport(fromDe, toDe);
	}

	// ---------- 내부 ----------

	/** 잠금 규약 ①: 신청서 행 잠금. 없으면 거부 */
	private AssetReqVO lockReq(String reqNo) {
		AssetReqVO req = reqNo == null ? null : assetReqDAO.selectReqForUpdate(reqNo);
		if (req == null) {
			throw new AssetItemException("신청서가 없습니다.");
		}
		return req;
	}

	/** 신청서를 잠그고 '승인' 상태인지 확인 */
	private AssetReqVO lockApproved(String reqNo, String what) {
		AssetReqVO req = lockReq(reqNo);
		if (AssetReqVO.PENDING.equals(req.getSttus())) {
			throw new AssetItemException("승인된 신청만 " + what + "할 수 있습니다.");
		}
		if (!AssetReqVO.APPROVED.equals(req.getSttus())) {
			throw new AssetItemException(ALREADY);
		}
		return req;
	}

	private void requireKeeper(String userId) throws Exception {
		if (!isKeeper(userId)) {
			throw new AssetItemException("지급·지급취소는 자산담당자만 할 수 있습니다.");
		}
	}

	private static List<AssetReqLineVO> approvedLines(List<AssetReqLineVO> lines) {
		return lines.stream().filter(l -> AssetReqLineVO.APPROVED.equals(l.getLineSttus())).toList();
	}

	private static List<String> itemCodes(List<AssetReqLineVO> lines) {
		return lines.stream().map(AssetReqLineVO::getItemCode).toList();
	}

	private void insertHist(String reqNo, String before, String after, String resn, String actorId) throws Exception {
		AssetReqHistVO hist = new AssetReqHistVO();
		hist.setReqHistId(assetReqHistIdGnrService.getNextStringId());
		hist.setReqNo(reqNo);
		hist.setBeforeSttus(before);
		hist.setAfterSttus(after);
		hist.setResn(resn);
		hist.setProcessId(actorId);
		assetReqDAO.insertHist(hist);
	}

	private static String trimToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
