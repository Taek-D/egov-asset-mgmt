package egovframework.asset.unit.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.asset.unit.service.AssetUnitVO;
import egovframework.com.cmm.ComDefaultCodeVO;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.service.CmmnDetailCode;
import egovframework.com.cmm.service.EgovCmmUseService;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 개별 자산 화면(자산담당자): 목록·상세(이력)·등록(입고)·반납/수리/수리 완료/폐기.
 */
@Controller
public class AssetUnitController {

	private static final String FORM_VIEW = "egovframework/asset/unit/AssetUnitForm";

	@Resource(name = "assetUnitService")
	private AssetUnitService assetUnitService;

	@Resource(name = "assetItemService")
	private AssetItemService assetItemService;

	@Resource(name = "EgovCmmUseService")
	private EgovCmmUseService cmmUseService;

	@Resource(name = "propertiesService")
	private EgovPropertyService propertiesService;

	/** 메뉴 진입(포털 메뉴는 POST로 들어온다): GET·POST 모두 받는다 */
	@RequestMapping("/asset/unit/list.do")
	public String list(@ModelAttribute("searchVO") AssetUnitVO searchVO, ModelMap model) {
		searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
		searchVO.setPageSize(propertiesService.getInt("pageSize"));
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
		paginationInfo.setPageSize(searchVO.getPageSize());
		searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

		model.addAttribute("resultList", assetUnitService.selectUnitList(searchVO));
		paginationInfo.setTotalRecordCount(assetUnitService.selectUnitListTotCnt(searchVO));
		model.addAttribute("paginationInfo", paginationInfo);
		// 품목 필터는 사용 중지 품목 포함(자산은 남아 있다)
		model.addAttribute("itemList", unitItems(null));
		addSttus(model);
		return "egovframework/asset/unit/AssetUnitList";
	}

	@GetMapping("/asset/unit/detail.do")
	public String detail(@RequestParam("assetNo") String assetNo, ModelMap model, RedirectAttributes redirect) {
		AssetUnitVO unit;
		try {
			unit = assetUnitService.selectUnit(assetNo);
		} catch (AssetItemException e) {
			redirect.addFlashAttribute("message", e.getMessage());
			return "redirect:/asset/unit/list.do";
		}
		model.addAttribute("unit", unit);
		// 보관 자산을 빼도 확보를 침범하지 않는지(화면 표시용. 실제 판정은 서비스가 잠금 아래에서 한다)
		AssetItemVO item = assetItemService.selectItem(unit.getItemCode());
		model.addAttribute("canTakeOut", item.getStoredQty() - 1 >= item.getReservedQty());
		model.addAttribute("item", item);
		model.addAttribute("histList", assetUnitService.selectUnitHistList(assetNo));
		addSttus(model);
		return "egovframework/asset/unit/AssetUnitDetail";
	}

	@GetMapping("/asset/unit/form.do")
	public String form(@RequestParam(value = "itemCode", required = false) String itemCode, ModelMap model) {
		AssetUnitVO unit = new AssetUnitVO();
		unit.setItemCode(itemCode);
		return formView(unit, model);
	}

	@PostMapping("/asset/unit/register.do")
	public String register(@ModelAttribute("unit") AssetUnitVO unit, ModelMap model, RedirectAttributes redirect) {
		try {
			assetUnitService.registerUnit(unit, currentUserId());
			return done(redirect, unit.getAssetNo(), "자산을 등록했습니다(보관, 입고 +1).");
		} catch (AssetItemException e) {
			model.addAttribute("message", e.getMessage());
			return formView(unit, model);
		}
	}

	/** 반납(→보관)·수리(→수리)·수리 완료(→보관)·폐기(→폐기). 허용 여부는 서비스가 판정한다 */
	@PostMapping("/asset/unit/status.do")
	public String status(@RequestParam("assetNo") String assetNo, @RequestParam("toSttus") String toSttus,
			@RequestParam(value = "reason", required = false) String reason, RedirectAttributes redirect) {
		try {
			assetUnitService.changeStatus(assetNo, toSttus, reason, currentUserId());
			return done(redirect, assetNo, "상태를 바꿨습니다.");
		} catch (AssetItemException e) {
			return done(redirect, assetNo, e.getMessage());
		}
	}

	private String formView(AssetUnitVO unit, ModelMap model) {
		model.addAttribute("unit", unit);
		// 새 등록은 사용 중인 개별 자산 품목만
		model.addAttribute("itemList", unitItems("Y"));
		return FORM_VIEW;
	}

	private List<AssetItemVO> unitItems(String useYn) {
		AssetItemVO search = new AssetItemVO();
		search.setSearchItemTy(AssetItemVO.TY_UNIT);
		search.setSearchUseYn(useYn);
		search.setFirstIndex(0);
		search.setRecordCountPerPage(Integer.MAX_VALUE);
		return assetItemService.selectItemList(search);
	}

	/** 상태 코드(AST004) 목록과 코드→이름 맵 */
	private void addSttus(ModelMap model) {
		ComDefaultCodeVO vo = new ComDefaultCodeVO();
		vo.setCodeId("AST004");
		List<CmmnDetailCode> codes = cmmUseService.selectCmmCodeDetail(vo);
		Map<String, String> names = new LinkedHashMap<>();
		codes.forEach(c -> names.put(c.getCode(), c.getCodeNm()));
		model.addAttribute("sttusList", codes);
		model.addAttribute("sttusNm", names);
	}

	private static String done(RedirectAttributes redirect, String assetNo, String message) {
		redirect.addFlashAttribute("message", message);
		redirect.addAttribute("assetNo", assetNo);
		return "redirect:/asset/unit/detail.do";
	}

	private static String currentUserId() {
		return ((LoginVO) EgovUserDetailsHelper.getAuthenticatedUser()).getId();
	}
}
