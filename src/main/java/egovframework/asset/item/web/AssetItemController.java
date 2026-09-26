package egovframework.asset.item.web;

import java.util.List;

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
import egovframework.asset.stock.service.AssetStockService;
import egovframework.com.cmm.ComDefaultCodeVO;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.service.CmmnDetailCode;
import egovframework.com.cmm.service.EgovCmmUseService;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 품목 관리 화면(자산담당자): 목록·등록·수정·사용 중지/재사용·삭제·입고·조정.
 */
@Controller
public class AssetItemController {

	private static final String FORM_VIEW = "egovframework/asset/item/AssetItemForm";

	@Resource(name = "assetItemService")
	private AssetItemService assetItemService;

	@Resource(name = "assetStockService")
	private AssetStockService assetStockService;

	@Resource(name = "EgovCmmUseService")
	private EgovCmmUseService cmmUseService;

	@Resource(name = "propertiesService")
	private EgovPropertyService propertiesService;

	@RequestMapping("/asset/item/list.do")
	public String list(@ModelAttribute("searchVO") AssetItemVO searchVO, ModelMap model) {
		searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
		searchVO.setPageSize(propertiesService.getInt("pageSize"));
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
		paginationInfo.setPageSize(searchVO.getPageSize());
		searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

		model.addAttribute("resultList", assetItemService.selectItemList(searchVO));
		paginationInfo.setTotalRecordCount(assetItemService.selectItemListTotCnt(searchVO));
		model.addAttribute("paginationInfo", paginationInfo);
		return "egovframework/asset/item/AssetItemList";
	}

	/** 등록(itemCode 없음) 또는 상세·수정 화면 */
	@GetMapping("/asset/item/form.do")
	public String form(@RequestParam(value = "itemCode", required = false) String itemCode, ModelMap model) {
		AssetItemVO item;
		if (itemCode == null || itemCode.isBlank()) {
			item = new AssetItemVO();
			item.setItemTy(AssetItemVO.TY_QTY);
			item.setMinQty(0);
		} else {
			item = assetItemService.selectItem(itemCode);
		}
		return formView(item, model);
	}

	@PostMapping("/asset/item/save.do")
	public String save(@ModelAttribute("item") AssetItemVO item, ModelMap model, RedirectAttributes redirect) {
		boolean isNew = item.getItemCode() == null || item.getItemCode().isBlank();
		try {
			if (isNew) {
				item.setItemCode(null);
				String code = assetItemService.insertItem(item, currentUserId());
				return done(redirect, code, "품목을 등록했습니다.");
			}
			assetItemService.updateItem(item, currentUserId());
			return done(redirect, item.getItemCode(), "품목을 수정했습니다.");
		} catch (AssetItemException e) {
			model.addAttribute("message", e.getMessage());
			if (isNew) {
				item.setItemCode(null);
			} else {
				// 화면의 재고·가용 표시는 저장된 값을 보여 준다
				AssetItemVO saved = assetItemService.selectItem(item.getItemCode());
				item.setStockQty(saved.getStockQty());
				item.setReservedQty(saved.getReservedQty());
				item.setStoredQty(saved.getStoredQty());
				item.setAvailQty(saved.getAvailQty());
				item.setUseAt(saved.getUseAt());
				item.setHasHist(saved.isHasHist());
			}
			return formView(item, model);
		}
	}

	@PostMapping("/asset/item/deactivate.do")
	public String deactivate(@RequestParam("itemCode") String itemCode, RedirectAttributes redirect) {
		try {
			assetItemService.deactivateItem(itemCode, currentUserId());
			return done(redirect, itemCode, "사용 중지했습니다. 새 신청과 일괄등록에서 빠집니다.");
		} catch (AssetItemException e) {
			return done(redirect, itemCode, e.getMessage());
		}
	}

	@PostMapping("/asset/item/activate.do")
	public String activate(@RequestParam("itemCode") String itemCode, RedirectAttributes redirect) {
		try {
			assetItemService.activateItem(itemCode, currentUserId());
			return done(redirect, itemCode, "다시 사용으로 바꿨습니다.");
		} catch (AssetItemException e) {
			return done(redirect, itemCode, e.getMessage());
		}
	}

	@PostMapping("/asset/item/delete.do")
	public String delete(@RequestParam("itemCode") String itemCode, RedirectAttributes redirect) {
		try {
			assetItemService.deleteItem(itemCode);
			redirect.addFlashAttribute("message", "품목을 삭제했습니다.");
			return "redirect:/asset/item/list.do";
		} catch (AssetItemException e) {
			return done(redirect, itemCode, e.getMessage());
		}
	}

	@PostMapping("/asset/item/receive.do")
	public String receive(@RequestParam("itemCode") String itemCode, @RequestParam("qty") int qty,
			@RequestParam(value = "reason", required = false) String reason, RedirectAttributes redirect) {
		try {
			assetStockService.receive(itemCode, qty, reason, currentUserId());
			return done(redirect, itemCode, qty + "개 입고했습니다.");
		} catch (AssetItemException e) {
			return done(redirect, itemCode, e.getMessage());
		}
	}

	@PostMapping("/asset/item/adjust.do")
	public String adjust(@RequestParam("itemCode") String itemCode, @RequestParam("delta") int delta,
			@RequestParam(value = "reason", required = false) String reason, RedirectAttributes redirect) {
		try {
			assetStockService.adjust(itemCode, delta, reason, currentUserId());
			return done(redirect, itemCode, "재고를 " + (delta > 0 ? "+" : "") + delta + " 조정했습니다.");
		} catch (AssetItemException e) {
			return done(redirect, itemCode, e.getMessage());
		}
	}

	private String formView(AssetItemVO item, ModelMap model) {
		model.addAttribute("item", item);
		model.addAttribute("clCodeList", codes("AST001"));
		model.addAttribute("unitCodeList", codes("AST002"));
		return FORM_VIEW;
	}

	private List<CmmnDetailCode> codes(String codeId) {
		ComDefaultCodeVO vo = new ComDefaultCodeVO();
		vo.setCodeId(codeId);
		return cmmUseService.selectCmmCodeDetail(vo);
	}

	private static String done(RedirectAttributes redirect, String itemCode, String message) {
		redirect.addFlashAttribute("message", message);
		redirect.addAttribute("itemCode", itemCode);
		return "redirect:/asset/item/form.do";
	}

	private static String currentUserId() {
		return ((LoginVO) EgovUserDetailsHelper.getAuthenticatedUser()).getId();
	}
}
