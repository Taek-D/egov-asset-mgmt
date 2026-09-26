package egovframework.asset.shortage.web;

import java.util.List;

import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.shortage.service.AssetShortageRunVO;
import egovframework.asset.shortage.service.AssetShortageService;
import egovframework.asset.shortage.service.AssetShortageVO;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 재고 부족 집계 화면(자산담당자): 최근 실행 결과와 즉시 실행.
 */
@Controller
public class AssetShortageController {

	@Resource(name = "assetShortageService")
	private AssetShortageService assetShortageService;

	@Resource(name = "propertiesService")
	private EgovPropertyService propertiesService;

	/** 메뉴 진입(포털 메뉴는 POST로 이동하므로 GET·POST 모두 받는다) */
	@RequestMapping("/asset/shortage/result.do")
	public String result(@ModelAttribute("searchVO") AssetShortageVO searchVO, ModelMap model) {
		searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
		searchVO.setPageSize(propertiesService.getInt("pageSize"));
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
		paginationInfo.setPageSize(searchVO.getPageSize());
		searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

		AssetShortageRunVO latestRun = assetShortageService.selectLatestRun();
		List<AssetShortageVO> resultList = List.of();
		int totCnt = 0;
		if (latestRun != null) {
			searchVO.setRunId(latestRun.getRunId());
			resultList = assetShortageService.selectShortageList(searchVO);
			totCnt = assetShortageService.selectShortageListTotCnt(searchVO);
		}
		paginationInfo.setTotalRecordCount(totCnt);
		model.addAttribute("latestRun", latestRun);
		model.addAttribute("resultList", resultList);
		model.addAttribute("paginationInfo", paginationInfo);
		return "egovframework/asset/shortage/AssetShortageResult";
	}

	@PostMapping("/asset/shortage/run.do")
	public String run(RedirectAttributes redirect) {
		String message;
		try {
			AssetShortageRunVO run = assetShortageService.run(AssetShortageService.RUN_MANUAL, currentUserId());
			message = run.isSuccess()
					? "재고 부족 집계를 실행했습니다. 부족 품목 " + run.getItemCnt() + "건."
					: "재고 부족 집계에 실패했습니다. 배치 결과 화면에서 내용을 확인하세요(배치결과 ID " + run.getBatchResultId() + ").";
		} catch (AssetItemException e) {
			message = e.getMessage();
		}
		redirect.addFlashAttribute("message", message);
		return "redirect:/asset/shortage/result.do";
	}

	private static String currentUserId() {
		return ((LoginVO) EgovUserDetailsHelper.getAuthenticatedUser()).getId();
	}
}
