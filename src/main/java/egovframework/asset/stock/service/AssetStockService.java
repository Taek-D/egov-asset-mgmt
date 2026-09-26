package egovframework.asset.stock.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemVO;

/**
 * 재고·가용 계산과 잠금 규약(스펙 §4·§5·§8). 다른 업무(승인·지급·개별 자산·재고 부족 집계)는 이 인터페이스로만 재고를 바꾼다.
 *
 * <p>가용: 수량 비품(QTY) = 재고 수량. 개별 자산(UNIT) = 보관(STORED) 자산 수 − 확보 수량. 0 미만이 되는 처리는 모두 거부한다.
 *
 * <p>트랜잭션: 구현은 {@code *ServiceImpl}이라 REQUIRED로 호출자의 트랜잭션에 합류한다. 변경 메서드는 스스로 품목 행을 잠그고
 * 다시 확인한 뒤 바꾸므로 단독 호출도 안전하다. 여러 품목을 다루는 호출자는 규약대로
 * ① 신청서 행을 잠그고 ② {@link #lockItems}로 관련 품목 전체를 한 번에 잠근 뒤 ③ 변경 메서드를 부른다.
 *
 * <p>거부는 {@link AssetItemException}(메시지는 화면 표시용). 합류한 트랜잭션 안에서 던져지면 바깥 트랜잭션도 롤백 전용으로
 * 표시되므로, 호출자는 잡아서 계속 진행하면 안 된다(커밋 시 UnexpectedRollbackException). 미리 판단하려면
 * {@link #lockItems}의 availQty를 보고 부른다.
 */
public interface AssetStockService {

	/** 이력 유형 */
	String HIST_IN = "IN";
	String HIST_ADJUST = "ADJUST";
	String HIST_RESERVE = "RESERVE";
	String HIST_RELEASE = "RELEASE";
	String HIST_DELIVER = "DELIVER";

	/** 한 품목의 가용 수량(잠그지 않는 조회). 품목이 없으면 {@link AssetItemException}. */
	int getAvailableQty(String itemCode);

	/** 여러 품목의 가용 수량(잠그지 않는 조회). 없는 품목코드는 결과에 없다. */
	Map<String, Integer> getAvailableQtys(Collection<String> itemCodes);

	/**
	 * 품목 행들을 ITEM_CODE 오름차순으로 {@code FOR UPDATE} 잠그고 현재 상태를 돌려준다
	 * (itemTy, useAt, stockQty, reservedQty, storedQty, availQty, minQty, itemNm).
	 * 보관 자산 수는 잠금 읽기(FOR SHARE)로 세므로 최신 커밋 값이다. 신청서 행은 호출자가 먼저 잠근다.
	 *
	 * @return 품목코드 오름차순 맵. 없는 품목코드가 있으면 {@link AssetItemException}
	 */
	Map<String, AssetItemVO> lockItems(Collection<String> itemCodes);

	/**
	 * 승인 확보(RESERVE). QTY: 재고 −qty(이력 −qty, 변동 후 = 재고). UNIT: 확보 +qty(이력 −qty, 변동 후 = 가용).
	 * 가용 &lt; qty면 부족 수량을 담은 {@link AssetItemException}으로 거부하고 아무것도 바꾸지 않는다. 사용 중지 품목도 처리한다.
	 */
	void reserve(String itemCode, int qty, String reqNo, String actorId);

	/**
	 * 확보 복구(RELEASE). QTY: 재고 +qty. UNIT: 확보 −qty(확보 &lt; qty면 거부). 이력 +qty.
	 *
	 * @param reason 사유(선택)
	 */
	void release(String itemCode, int qty, String reqNo, String actorId, String reason);

	/**
	 * 개별 자산 지급으로 확보 해제(DELIVER, UNIT 전용). 확보 −count, 이력 증감 0 / 변동 후 = 가용.
	 * <b>호출 순서</b>(한 트랜잭션): ① 신청서 행 잠금 ② {@link #lockItems}(관련 품목 전체) ③ 지급할 자산 count대를
	 * STORED→IN_USE로 변경 ④ deliverUnits. 변동 후 값은 ③ 이후의 보관 수로 계산된다(보관 −n과 확보 −n이 상쇄).
	 * 확보 &lt; count이거나 결과 가용 &lt; 0이면 거부.
	 */
	void deliverUnits(String itemCode, int count, String reqNo, String actorId);

	/**
	 * 개별 자산 등록 입고(IN +1, UNIT 전용). 변동 후 = 가용. 사유에 자산번호를 남긴다.
	 * <b>호출 순서</b>(한 트랜잭션): ① {@link #lockItems}([품목]) ② ASSET_UNIT 행(STORED) 삽입 ③ recordUnitIn.
	 * 품목 잠금보다 자산 행 변경을 먼저 하면 동시 확보와 교착될 수 있다.
	 */
	void recordUnitIn(String itemCode, String assetNo, String actorId);

	/** 입고(IN, QTY 전용). qty ≥ 1, 재고 +qty. reason은 선택. */
	void receive(String itemCode, int qty, String reason, String actorId);

	/** 조정(ADJUST, QTY 전용). delta ≠ 0, 사유 필수, 조정 후 재고 &lt; 0이면 거부. */
	void adjust(String itemCode, int delta, String reason, String actorId);

	/** 재고 변동 이력 목록(검색: itemCode). 최신순. */
	List<AssetStockHistVO> selectHistList(AssetStockHistVO searchVO);

	int selectHistListTotCnt(AssetStockHistVO searchVO);
}
