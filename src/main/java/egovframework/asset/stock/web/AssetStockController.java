package egovframework.asset.stock.web;

import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.stock.service.AssetStockHistVO;
import egovframework.asset.stock.service.AssetStockService;
import jakarta.annotation.Resource;

/**
 * 재고 변동 이력 화면(자산담당자).
 */
@Controller
public class AssetStockController {

	@Resource(name = "assetStockService")
	private AssetStockService assetStockService;

	@Resource(name = "assetItemService")
	private AssetItemService assetItemService;

	@Resource(name = "propertiesService")
	private EgovPropertyService propertiesService;

	@RequestMapping("/asset/stock/history.do")
	public String history(@ModelAttribute("searchVO") AssetStockHistVO searchVO, ModelMap model) {
		searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
		searchVO.setPageSize(propertiesService.getInt("pageSize"));
		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
		paginationInfo.setPageSize(searchVO.getPageSize());
		searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

		model.addAttribute("resultList", assetStockService.selectHistList(searchVO));
		paginationInfo.setTotalRecordCount(assetStockService.selectHistListTotCnt(searchVO));
		model.addAttribute("paginationInfo", paginationInfo);

		// 품목 선택 상자(사용 중지 품목 포함 전체)
		AssetItemVO all = new AssetItemVO();
		all.setFirstIndex(0);
		all.setRecordCountPerPage(Integer.MAX_VALUE);
		model.addAttribute("itemList", assetItemService.selectItemList(all));
		return "egovframework/asset/stock/AssetStockHistory";
	}
}
