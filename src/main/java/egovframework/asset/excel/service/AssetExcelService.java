package egovframework.asset.excel.service;

import java.io.InputStream;
import java.util.List;

import egovframework.asset.item.service.AssetItemException;

/**
 * 엑셀 일괄등록·내려받기(docs/business-rules.md '엑셀 일괄등록'·'내려받기', 자산담당자 전용 화면에서만 부른다).
 *
 * <p>일괄등록은 전부 반영하거나 전부 반영하지 않는다: 모든 줄을 먼저 검사해 틀린 줄(엑셀 행 번호)과 이유를 전부 모으고,
 * 하나라도 있으면 아무것도 저장하지 않고 결과만 돌려준다. 틀린 줄이 없으면 한 트랜잭션에서 모두 저장하며, 저장 중
 * 규칙 위반(동시에 같은 이름이 등록된 경우 등)은 {@link AssetItemException}으로 전체를 롤백한다.
 * 1행은 머리글이고, 모든 칸이 빈 줄은 무시한다. .xlsx와 .xls를 받는다. 엑셀 파일이 아니거나 등록할 줄이 없으면 {@link AssetItemException}.
 */
public interface AssetExcelService {

	/** 품목 일괄등록 머리글 */
	List<String> ITEM_HEADERS = List.of("품목명", "유형", "분류코드", "단위코드", "최소 수량");

	/** 자산 일괄등록 머리글 */
	List<String> UNIT_HEADERS = List.of("품목코드", "자산번호", "비고");

	/**
	 * 품목 일괄등록(열: 품목명, 유형(수량/개별), 분류코드(AST001), 단위코드(AST002), 최소 수량).
	 * 필수는 품목명·유형(품목 등록 화면과 같다). 코드는 비워 둘 수 있고, 적으면 사용 중인 공통코드여야 한다. 최소 수량은 비우면 0.
	 * 품목코드는 생성기가 만들고 재고는 0으로 시작한다({@code AssetItemService.insertItem}).
	 */
	AssetExcelResult uploadItems(InputStream in, String actorId);

	/**
	 * 자산 일괄등록(열: 품목코드, 자산번호, 비고). 한 줄이 입고 한 건이다({@code AssetUnitService.registerUnit}: 보관 상태, 자산 이력, IN +1).
	 * 품목은 사용 중인 개별 자산 품목이어야 하고, 자산번호는 폐기 자산을 포함한 전체와 파일 안에서 유일해야 한다.
	 */
	AssetExcelResult uploadUnits(InputStream in, String actorId);

	/** 재고 현황: 품목마다 한 행(사용 중지 품목 포함). */
	AssetExcelSheet stockSheet();

	/** 자산 대장: 자산마다 한 행(폐기 포함). */
	AssetExcelSheet unitSheet();

	/** 신청 내역: 신청일이 fromDe~toDe(yyyy-MM-dd, 양끝 포함)인 신청의 품목 줄마다 한 행. 날짜가 틀리면 {@link AssetItemException}. */
	AssetExcelSheet reqSheet(String fromDe, String toDe) throws Exception;

	/** 빈 품목 일괄등록 양식(머리글만). */
	AssetExcelSheet itemTemplate();

	/** 빈 자산 일괄등록 양식(머리글만). */
	AssetExcelSheet unitTemplate();
}
