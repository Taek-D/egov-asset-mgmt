package egovframework.asset.req.web;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.WebUtils;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.req.service.AssetReqLineVO;
import egovframework.asset.req.service.AssetReqService;
import egovframework.asset.req.service.AssetReqVO;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.asset.unit.service.AssetUnitVO;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 신청·승인·지급 화면. 권한·범위·상태 판정은 모두 서비스가 한다(여기서는 로그인 사용자를 넘기고 결과를 보여 줄 뿐이다).
 * <ul>
 * <li>/asset/req/** 신청서 작성·내 신청·상세·신청취소 (로그인 사용자)</li>
 * <li>/asset/approve/** 승인 대기함·승인·부서 신청 조회 (로그인 사용자, 승인자 여부는 서비스)</li>
 * <li>/asset/deliver/** 지급 목록·지급·지급취소 (자산담당자)</li>
 * <li>/asset/admin/reqList.do 전체 신청 조회 (관리자)</li>
 * </ul>
 */
@Controller
public class AssetReqController {

	/** 작성 화면마다 발급하는 1회용 제출 토큰(폼 hidden 필드와 모델 속성 이름) */
	public static final String TOKEN_PARAM = "assetReqToken";
	/** 세션당 보관하는 미사용 토큰 수. 넘치면 가장 오래된 것부터 버린다 */
	public static final int MAX_FORM_TOKENS = 20;
	private static final String TOKEN_SESSION_KEY = AssetReqController.class.getName() + ".formTokens";
	private static final String TOKEN_REJECT = "이미 제출했거나 만료된 화면입니다. 내 신청에서 제출 여부를 확인하세요.";

	private static final String FORM_VIEW = "egovframework/asset/req/AssetReqForm";
	private static final String LIST_VIEW = "egovframework/asset/req/AssetReqList";
	private static final String APPROVE_VIEW = "egovframework/asset/req/AssetReqApprove";
	private static final String DELIVER_VIEW = "egovframework/asset/req/AssetReqDeliver";
	private static final int FORM_ROWS = 5;
	private static final String PICK_PREFIX = "pick_";

	@Resource(name = "assetReqService")
	private AssetReqService assetReqService;

	@Resource(name = "assetItemService")
	private AssetItemService assetItemService;

	@Resource(name = "assetUnitService")
	private AssetUnitService assetUnitService;

	@Resource(name = "propertiesService")
	private EgovPropertyService propertiesService;

	// ---------- 신청서 작성 ----------

	/** 메뉴 진입(GET·POST). addRows가 있으면 품목 줄을 더 늘린다(입력한 줄·토큰은 유지) */
	@RequestMapping("/asset/req/form.do")
	public String form(@ModelAttribute("req") AssetReqVO req, @RequestParam(value = "addRows", required = false) String addRows,
			HttpServletRequest request, ModelMap model) {
		return formView(req, model, addRows != null, request);
	}

	/**
	 * 토큰 확인부터 신청서 생성까지 세션 단위로 직렬화한다(더블클릭으로 같은 토큰이 동시에 오면 차례로 처리).
	 * 토큰은 신청서가 실제로 만들어졌을 때만 소모한다. 입력 형식 오류·규칙 위반이면 같은 토큰으로 화면을 다시 그린다.
	 */
	@PostMapping("/asset/req/submit.do")
	public String submit(@ModelAttribute("req") AssetReqVO req, BindingResult bindingResult, HttpServletRequest request, ModelMap model,
			RedirectAttributes redirect) throws Exception {
		HttpSession session = request.getSession();
		synchronized (WebUtils.getSessionMutex(session)) {
			if (bindingResult.hasErrors()) {
				model.addAttribute("message", "신청 수량은 1 이상의 숫자로 입력하세요.");
				return formView(req, model, false, request);
			}
			String token = request.getParameter(TOKEN_PARAM);
			Set<String> tokens = formTokens(session);
			if (token == null || !tokens.contains(token)) {
				model.addAttribute("message", TOKEN_REJECT);
				return formView(req, model, false, request);
			}
			try {
				String reqNo = assetReqService.submit(req, currentUserId());
				tokens.remove(token);
				redirect.addFlashAttribute("message", "신청서를 제출했습니다(대기). 대기 중에는 수정할 수 없으며, 바꾸려면 취소 후 다시 신청합니다.");
				redirect.addAttribute("reqNo", reqNo);
				return "redirect:/asset/req/detail.do";
			} catch (AssetItemException e) {
				model.addAttribute("message", e.getMessage());
				return formView(req, model, false, request);
			}
		}
	}

	/** 세션의 미사용 토큰(발급 순서). 세션 뮤텍스 안에서만 부른다 */
	@SuppressWarnings("unchecked")
	private static Set<String> formTokens(HttpSession session) {
		Set<String> tokens = (Set<String>) session.getAttribute(TOKEN_SESSION_KEY);
		if (tokens == null) {
			tokens = new LinkedHashSet<>();
			session.setAttribute(TOKEN_SESSION_KEY, tokens);
		}
		return tokens;
	}

	/** 요청의 토큰이 아직 쓰지 않은 유효 토큰이면 그대로, 아니면 새로 발급한다 */
	private static String formToken(HttpServletRequest request) {
		HttpSession session = request.getSession();
		synchronized (WebUtils.getSessionMutex(session)) {
			Set<String> tokens = formTokens(session);
			String token = request.getParameter(TOKEN_PARAM);
			if (token != null && tokens.contains(token)) {
				return token;
			}
			token = UUID.randomUUID().toString();
			tokens.add(token);
			Iterator<String> oldest = tokens.iterator();
			while (tokens.size() > MAX_FORM_TOKENS) {
				oldest.next();
				oldest.remove();
			}
			return token;
		}
	}

	private String formView(AssetReqVO req, ModelMap model, boolean addRows, HttpServletRequest request) {
		model.addAttribute(TOKEN_PARAM, formToken(request));
		List<AssetReqLineVO> lines = new ArrayList<>(req.getLines());
		int rows = Math.max(FORM_ROWS, lines.size()) + (addRows ? FORM_ROWS : 0);
		while (lines.size() < rows) {
			lines.add(new AssetReqLineVO());
		}
		req.setLines(lines);
		model.addAttribute("req", req);
		// 사용 중인 품목만(가용 수량 포함)
		AssetItemVO search = new AssetItemVO();
		search.setSearchUseYn("Y");
		search.setFirstIndex(0);
		search.setRecordCountPerPage(Integer.MAX_VALUE);
		model.addAttribute("itemList", assetItemService.selectItemList(search));
		return FORM_VIEW;
	}

	// ---------- 목록 ----------

	@RequestMapping("/asset/req/myList.do")
	public String myList(@ModelAttribute("searchVO") AssetReqVO searchVO, ModelMap model) throws Exception {
		return list(AssetReqService.SCOPE_MY, searchVO, model, "내 신청", "/asset/req/myList.do", "detail");
	}

	@RequestMapping("/asset/approve/inbox.do")
	public String inbox(@ModelAttribute("searchVO") AssetReqVO searchVO, ModelMap model) throws Exception {
		return list(AssetReqService.SCOPE_INBOX, searchVO, model, "승인 대기함", "/asset/approve/inbox.do", "approve");
	}

	@RequestMapping("/asset/approve/deptList.do")
	public String deptList(@ModelAttribute("searchVO") AssetReqVO searchVO, ModelMap model) throws Exception {
		return list(AssetReqService.SCOPE_DEPT, searchVO, model, "부서 신청 조회", "/asset/approve/deptList.do", "detail");
	}

	/** 지급 관리: 기본은 지급 대기(승인) 신청, 상태 '전체'를 고르면 모든 신청 */
	@RequestMapping("/asset/deliver/list.do")
	public String deliverList(@ModelAttribute("searchVO") AssetReqVO searchVO, ModelMap model) throws Exception {
		if (searchVO.getSearchSttus() == null) {
			searchVO.setSearchSttus(AssetReqVO.APPROVED);
		}
		return list(AssetReqService.SCOPE_ALL, searchVO, model, "지급 관리", "/asset/deliver/list.do", "deliver");
	}

	@RequestMapping("/asset/admin/reqList.do")
	public String adminList(@ModelAttribute("searchVO") AssetReqVO searchVO, ModelMap model) throws Exception {
		return list(AssetReqService.SCOPE_ALL, searchVO, model, "전체 신청 조회", "/asset/admin/reqList.do", "detail");
	}

	private String list(String scope, AssetReqVO searchVO, ModelMap model, String title, String action, String rowLink) throws Exception {
		searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
		searchVO.setPageSize(propertiesService.getInt("pageSize"));
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
		paginationInfo.setPageSize(searchVO.getPageSize());
		searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

		model.addAttribute("listTitle", title);
		model.addAttribute("listAction", action);
		model.addAttribute("rowLink", rowLink);
		model.addAttribute("scope", scope);
		model.addAttribute("sttusNm", AssetReqVO.STTUS_NM);
		model.addAttribute("sttusList", List.of(AssetReqVO.PENDING, AssetReqVO.APPROVED, AssetReqVO.REJECTED, AssetReqVO.DELIVERED,
				AssetReqVO.CANCELED, AssetReqVO.DELIVERY_CANCELED));
		String userId = currentUserId();
		try {
			model.addAttribute("resultList", assetReqService.selectReqList(scope, searchVO, userId));
			paginationInfo.setTotalRecordCount(assetReqService.selectReqListTotCnt(scope, searchVO, userId));
		} catch (AssetItemException e) {
			model.addAttribute("resultList", List.of());
			model.addAttribute("listError", e.getMessage());
		}
		model.addAttribute("paginationInfo", paginationInfo);
		return LIST_VIEW;
	}

	// ---------- 상세·신청취소 ----------

	@GetMapping("/asset/req/detail.do")
	public String detail(@RequestParam("reqNo") String reqNo, ModelMap model, RedirectAttributes redirect) throws Exception {
		try {
			model.addAttribute("req", assetReqService.selectReqDetail(reqNo, currentUserId()));
		} catch (AssetItemException e) {
			redirect.addFlashAttribute("message", e.getMessage());
			return "redirect:/asset/req/myList.do";
		}
		addNames(model);
		return "egovframework/asset/req/AssetReqDetail";
	}

	@PostMapping("/asset/req/cancel.do")
	public String cancel(@RequestParam("reqNo") String reqNo, RedirectAttributes redirect) throws Exception {
		try {
			assetReqService.cancel(reqNo, currentUserId());
			return toDetail(redirect, reqNo, "신청을 취소했습니다.");
		} catch (AssetItemException e) {
			return toDetail(redirect, reqNo, e.getMessage());
		}
	}

	// ---------- 승인 ----------

	@GetMapping("/asset/approve/form.do")
	public String approveForm(@RequestParam("reqNo") String reqNo, ModelMap model, RedirectAttributes redirect) throws Exception {
		return approveView(reqNo, null, model, redirect);
	}

	/** 품목마다 lines[i].lineSttus(APPROVED/REJECTED), lines[i].aprvQty 또는 lines[i].rejectResn을 한 번에 제출 */
	@PostMapping("/asset/approve/submit.do")
	public String approve(@ModelAttribute("input") AssetReqVO input, BindingResult bindingResult, ModelMap model,
			RedirectAttributes redirect) throws Exception {
		if (bindingResult.hasErrors()) {
			model.addAttribute("message", "승인 수량은 숫자로 입력하세요.");
			return approveView(input.getReqNo(), input, model, redirect);
		}
		try {
			assetReqService.approve(input.getReqNo(), input.getLines(), currentUserId());
			redirect.addFlashAttribute("message", "승인 처리를 마쳤습니다: " + input.getReqNo());
			return "redirect:/asset/approve/inbox.do";
		} catch (AssetItemException e) {
			model.addAttribute("message", e.getMessage());
			return approveView(input.getReqNo(), input, model, redirect);
		}
	}

	/** 승인 화면. 입력이 있으면(거부 후 다시 보여 줄 때) 품목마다 입력값을 되살린다 */
	private String approveView(String reqNo, AssetReqVO input, ModelMap model, RedirectAttributes redirect) throws Exception {
		AssetReqVO req;
		try {
			req = assetReqService.selectReqDetail(reqNo, currentUserId());
		} catch (AssetItemException e) {
			redirect.addFlashAttribute("message", e.getMessage());
			return "redirect:/asset/approve/inbox.do";
		}
		if (!req.isApprovable()) {
			return toDetail(redirect, reqNo, model.containsAttribute("message") ? (String) model.get("message")
					: "지금 승인할 수 있는 신청이 아닙니다(이미 처리되었거나 승인자가 아닙니다).");
		}
		if (input != null) {
			Map<String, AssetReqLineVO> entered = new LinkedHashMap<>();
			input.getLines().forEach(l -> entered.put(l.getItemCode(), l));
			for (AssetReqLineVO line : req.getLines()) {
				AssetReqLineVO in = entered.get(line.getItemCode());
				if (in != null) {
					line.setLineSttus(in.getLineSttus());
					line.setAprvQty(in.getAprvQty());
					line.setRejectResn(in.getRejectResn());
				}
			}
		}
		model.addAttribute("req", req);
		addNames(model);
		return APPROVE_VIEW;
	}

	// ---------- 지급·지급취소 ----------

	@GetMapping("/asset/deliver/form.do")
	public String deliverForm(@RequestParam("reqNo") String reqNo, ModelMap model, RedirectAttributes redirect) throws Exception {
		AssetReqVO req;
		try {
			req = assetReqService.selectReqDetail(reqNo, currentUserId());
		} catch (AssetItemException e) {
			redirect.addFlashAttribute("message", e.getMessage());
			return "redirect:/asset/deliver/list.do";
		}
		if (!req.isDeliverable()) {
			return toDetail(redirect, reqNo, "지급할 수 있는 신청이 아닙니다(승인 상태가 아니거나 자산담당자가 아닙니다).");
		}
		Map<String, List<AssetUnitVO>> stored = new LinkedHashMap<>();
		for (AssetReqLineVO line : req.getLines()) {
			if (line.isUnit() && AssetReqLineVO.APPROVED.equals(line.getLineSttus())) {
				stored.put(line.getItemCode(), assetUnitService.selectStoredUnits(line.getItemCode()));
			}
		}
		req.setStoredUnits(stored);
		model.addAttribute("req", req);
		addNames(model);
		return DELIVER_VIEW;
	}

	/** 개별 자산 품목마다 pick_{품목코드} 체크박스로 고른 자산번호를 받는다 */
	@PostMapping("/asset/deliver/submit.do")
	public String deliver(@RequestParam("reqNo") String reqNo, @RequestParam MultiValueMap<String, String> params,
			RedirectAttributes redirect) throws Exception {
		Map<String, List<String>> picks = new LinkedHashMap<>();
		params.forEach((name, values) -> {
			if (name.startsWith(PICK_PREFIX)) {
				picks.put(name.substring(PICK_PREFIX.length()), values);
			}
		});
		try {
			assetReqService.deliver(reqNo, picks, currentUserId());
			return toDetail(redirect, reqNo, "지급을 마쳤습니다(지급완료).");
		} catch (AssetItemException e) {
			redirect.addFlashAttribute("message", e.getMessage());
			redirect.addAttribute("reqNo", reqNo);
			return "redirect:/asset/deliver/form.do";
		}
	}

	@PostMapping("/asset/deliver/cancel.do")
	public String cancelDelivery(@RequestParam("reqNo") String reqNo, @RequestParam(value = "reason", required = false) String reason,
			RedirectAttributes redirect) throws Exception {
		try {
			assetReqService.cancelDelivery(reqNo, reason, currentUserId());
			return toDetail(redirect, reqNo, "지급을 취소하고 확보 수량을 복구했습니다.");
		} catch (AssetItemException e) {
			return toDetail(redirect, reqNo, e.getMessage());
		}
	}

	// ---------- 공통 ----------

	private static void addNames(ModelMap model) {
		model.addAttribute("sttusNm", AssetReqVO.STTUS_NM);
		model.addAttribute("lineSttusNm", AssetReqLineVO.STTUS_NM);
	}

	private static String toDetail(RedirectAttributes redirect, String reqNo, String message) {
		redirect.addFlashAttribute("message", message);
		redirect.addAttribute("reqNo", reqNo);
		return "redirect:/asset/req/detail.do";
	}

	private static String currentUserId() {
		LoginVO user = (LoginVO) EgovUserDetailsHelper.getAuthenticatedUser();
		return user == null ? null : user.getId();
	}
}
