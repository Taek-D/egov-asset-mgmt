package egovframework.asset.item.service;

import java.util.List;

/**
 * 품목 관리(자산담당자). 재고 수량은 여기서 바꾸지 않는다 — 입고·조정·확보는 {@code AssetStockService}.
 * 규칙 위반은 {@link AssetItemException}.
 */
public interface AssetItemService {

	/** 목록(검색: searchKeyword=품목명 일부, searchItemTy, searchUseYn=Y/N). 페이징은 firstIndex/recordCountPerPage. */
	List<AssetItemVO> selectItemList(AssetItemVO searchVO);

	int selectItemListTotCnt(AssetItemVO searchVO);

	/** 품목 한 건(재고·가용·이력 여부 포함). 없으면 {@link AssetItemException}. */
	AssetItemVO selectItem(String itemCode);

	/**
	 * 품목 등록. 품목코드는 생성기가 만들고, 재고·확보 0, 사용 중으로 시작한다.
	 * 사용 중 품목끼리 이름이 같으면 거부.
	 *
	 * @return 새 품목코드
	 */
	String insertItem(AssetItemVO vo, String actorId);

	/** 품목명·유형·분류·단위·최소 수량 수정. 이력이 생긴 품목의 유형 변경은 거부. */
	void updateItem(AssetItemVO vo, String actorId);

	/** 사용 중지. 새 신청·일괄등록에서 빠지고, 이미 들어온 신청은 그대로 처리된다. */
	void deactivateItem(String itemCode, String actorId);

	/** 다시 사용. 같은 이름의 사용 중 품목이 있으면 거부. */
	void activateItem(String itemCode, String actorId);

	/** 삭제. 재고 변동·신청·개별 자산 이력이 하나라도 있으면 거부(사용 중지를 안내). */
	void deleteItem(String itemCode);
}
