package egovframework.asset.excel.web;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import egovframework.asset.excel.service.AssetExcelResult;
import egovframework.asset.excel.service.AssetExcelService;
import egovframework.asset.excel.service.AssetExcelSheet;
import egovframework.asset.item.service.AssetItemException;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 엑셀 일괄등록·내려받기 화면(자산담당자 전용, URL 권한 /asset/excel/**).
 */
@Controller
public class AssetExcelController {

	private static final String MAIN_VIEW = "egovframework/asset/excel/AssetExcelMain";

	@Resource(name = "assetExcelService")
	private AssetExcelService assetExcelService;

	/** 메뉴 진입(헤더 메뉴가 POST로 부르므로 GET·POST 모두) */
	@RequestMapping("/asset/excel/main.do")
	public String main(ModelMap model) {
		return mainView(model, null, null);
	}

	@PostMapping("/asset/excel/uploadItems.do")
	public String uploadItems(@RequestParam(value = "file", required = false) MultipartFile file, ModelMap model) throws IOException {
		return upload(file, true, model);
	}

	@PostMapping("/asset/excel/uploadUnits.do")
	public String uploadUnits(@RequestParam(value = "file", required = false) MultipartFile file, ModelMap model) throws IOException {
		return upload(file, false, model);
	}

	@GetMapping("/asset/excel/stock.do")
	public ModelAndView stock() {
		return download(assetExcelService.stockSheet());
	}

	@GetMapping("/asset/excel/units.do")
	public ModelAndView units() {
		return download(assetExcelService.unitSheet());
	}

	@GetMapping("/asset/excel/requests.do")
	public ModelAndView requests(@RequestParam(value = "fromDe", required = false) String fromDe,
			@RequestParam(value = "toDe", required = false) String toDe, ModelMap model) throws Exception {
		try {
			return download(assetExcelService.reqSheet(fromDe, toDe));
		} catch (AssetItemException e) {
			model.addAttribute("message", e.getMessage());
			return new ModelAndView(mainView(model, fromDe, toDe), model);
		}
	}

	@GetMapping("/asset/excel/template.do")
	public ModelAndView template(@RequestParam("type") String type) {
		return download("unit".equals(type) ? assetExcelService.unitTemplate() : assetExcelService.itemTemplate());
	}

	private String upload(MultipartFile file, boolean items, ModelMap model) throws IOException {
		String kind = items ? "품목" : "자산";
		model.addAttribute("uploadKind", kind);
		if (file == null || file.isEmpty()) {
			model.addAttribute("message", kind + " 일괄등록 파일을 선택하세요.");
			return mainView(model, null, null);
		}
		String actorId = ((LoginVO) EgovUserDetailsHelper.getAuthenticatedUser()).getId();
		try (InputStream in = file.getInputStream()) {
			AssetExcelResult result = items ? assetExcelService.uploadItems(in, actorId) : assetExcelService.uploadUnits(in, actorId);
			model.addAttribute("result", result);
			model.addAttribute("message", result.isOk() ? kind + " " + result.getSavedCnt() + "건을 등록했습니다."
					: "틀린 줄이 " + result.getErrors().size() + "건 있어 아무것도 저장하지 않았습니다. 고친 뒤 파일 전체를 다시 올리세요.");
		} catch (AssetItemException e) {
			model.addAttribute("message", "저장하지 않았습니다: " + e.getMessage());
		}
		return mainView(model, null, null);
	}

	private String mainView(ModelMap model, String fromDe, String toDe) {
		LocalDate today = LocalDate.now();
		model.addAttribute("fromDe", fromDe == null ? today.withDayOfMonth(1).toString() : fromDe);
		model.addAttribute("toDe", toDe == null ? today.toString() : toDe);
		return MAIN_VIEW;
	}

	private static ModelAndView download(AssetExcelSheet sheet) {
		return new ModelAndView(new AssetExcelView(), Map.of("sheet", sheet, "filename", sheet.getFileName()));
	}
}
