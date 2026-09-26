package egovframework.asset.depthead.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import egovframework.asset.depthead.service.DeptHeadException;
import egovframework.asset.depthead.service.DeptHeadService;
import egovframework.asset.depthead.service.DeptHeadVO;
import egovframework.com.cmm.ComDefaultVO;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 부서장 지정 화면(관리자 전용, URL 권한 /asset/depthead/**).
 */
@Controller
public class DeptHeadController {

	private static final String LIST_REDIRECT = "redirect:/asset/depthead/list.do";

	@Resource(name = "deptHeadService")
	private DeptHeadService deptHeadService;

	@Resource(name = "propertiesService")
	private EgovPropertyService propertiesService;

	/** 전체 부서와 부서장, 무효 표시, 부서별 지정 후보. 포털 메뉴가 POST로 이동하기도 하므로 GET·POST 모두 받는다 */
	@RequestMapping("/asset/depthead/list.do")
	public String list(@ModelAttribute("searchVO") ComDefaultVO searchVO, ModelMap model) throws Exception {
		searchVO.setPageUnit(propertiesService.getInt("pageUnit"));
		searchVO.setPageSize(propertiesService.getInt("pageSize"));

		PaginationInfo paginationInfo = new PaginationInfo();
		paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
		paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
		paginationInfo.setPageSize(searchVO.getPageSize());
		searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
		searchVO.setLastIndex(paginationInfo.getLastRecordIndex());
		searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

		List<DeptHeadVO> resultList = deptHeadService.selectDeptHeadList(searchVO);
		Map<String, List<DeptHeadVO>> candidates = new HashMap<>();
		for (DeptHeadVO dept : resultList) {
			candidates.put(dept.getOrgnztId(), deptHeadService.selectHeadCandidateList(dept.getOrgnztId()));
		}
		paginationInfo.setTotalRecordCount(deptHeadService.selectDeptHeadListCnt(searchVO));

		model.addAttribute("resultList", resultList);
		model.addAttribute("candidates", candidates);
		model.addAttribute("paginationInfo", paginationInfo);
		return "egovframework/asset/depthead/DeptHeadList";
	}

	@PostMapping("/asset/depthead/assign.do")
	public String assign(@RequestParam("orgnztId") String orgnztId,
			@RequestParam(value = "emplyrId", required = false) String emplyrId,
			@RequestParam(value = "pageIndex", defaultValue = "1") int pageIndex, RedirectAttributes redirect) throws Exception {
		LoginVO user = (LoginVO) EgovUserDetailsHelper.getAuthenticatedUser();
		try {
			deptHeadService.assignDeptHead(orgnztId, emplyrId, user.getId());
			redirect.addFlashAttribute("message", "부서장을 지정했습니다.");
		} catch (DeptHeadException e) {
			redirect.addFlashAttribute("message", e.getMessage());
		}
		redirect.addAttribute("pageIndex", pageIndex);
		return LIST_REDIRECT;
	}

	@PostMapping("/asset/depthead/unassign.do")
	public String unassign(@RequestParam("orgnztId") String orgnztId,
			@RequestParam(value = "pageIndex", defaultValue = "1") int pageIndex, RedirectAttributes redirect) throws Exception {
		deptHeadService.unassignDeptHead(orgnztId);
		redirect.addFlashAttribute("message", "부서장 지정을 해제했습니다.");
		redirect.addAttribute("pageIndex", pageIndex);
		return LIST_REDIRECT;
	}
}
