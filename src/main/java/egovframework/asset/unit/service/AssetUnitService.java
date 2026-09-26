package egovframework.asset.unit.service;

import java.util.List;

import egovframework.asset.item.service.AssetItemException;

/**
 * 개별 자산(스펙 §6): 등록(입고), 상태 전이(반납·수리·수리 완료·폐기), 지급 시 자산 배정, 조회.
 *
 * <p>상태 전이 표(이 밖의 전이는 모두 거부):
 * <pre>
 * 보관 → 사용   지급 처리 안에서만({@link #issueUnits}). 현재 사용자 = 신청자
 * 사용 → 보관   반납. 현재 사용자를 비운다
 * 보관 → 수리   수리 후 보관 수 − 1 ≥ 확보 수량일 때만
 * 수리 → 보관   수리 완료. 항상
 * 보관 → 폐기   사유 필수. 폐기 후 보관 수 − 1 ≥ 확보 수량일 때만
 * 수리 → 폐기   사유 필수. 항상
 * </pre>
 * 모든 변경은 자산 이력(ASSET_UNIT_HIST) 한 행을 남긴다. 반납·수리·폐기는 재고 변동 이력을 남기지 않는다(자산 이력이 기록이다).
 *
 * <p>잠금 규약(§8): 품목 행 잠금({@code AssetStockService.lockItems}) → 자산 행 {@code FOR UPDATE} → 상태·확보 재확인 → 변경.
 * 구현은 {@code *ServiceImpl}이라 REQUIRED로 호출자 트랜잭션에 합류한다. 거부는 {@link AssetItemException}(메시지는 화면 표시용)이며
 * 합류한 트랜잭션도 롤백 전용이 되므로 잡아서 계속 진행하지 않는다.
 */
public interface AssetUnitService {

	/** 목록(검색: searchItemCode, searchSttus, searchAssetNo=일부). 페이징은 firstIndex/recordCountPerPage. 자산번호순. */
	List<AssetUnitVO> selectUnitList(AssetUnitVO searchVO);

	int selectUnitListTotCnt(AssetUnitVO searchVO);

	/** 자산 한 건(품목명·사용자명 포함). 없으면 {@link AssetItemException}. */
	AssetUnitVO selectUnit(String assetNo);

	/** 자산 이력 전체(처리 순). */
	List<AssetUnitHistVO> selectUnitHistList(String assetNo);

	/**
	 * 품목의 보관(STORED) 자산 목록(자산번호순). 지급 화면의 자산 선택용 — 잠그지 않는 조회이므로
	 * 실제 배정은 {@link #issueUnits}가 잠금 아래에서 다시 확인한다.
	 */
	List<AssetUnitVO> selectStoredUnits(String itemCode);

	/**
	 * 등록 = 입고. 자산번호(1~50자, 폐기 자산 포함 전체에서 유일), 품목(사용 중인 개별 자산 품목만), 비고(선택, 500자 이내).
	 * 보관 상태로 넣고 자산 이력 첫 행(이전 상태 없음 → 보관)과 재고 변동 이력 IN +1을 남긴다.
	 * 순서: 품목 잠금 → 자산 행 삽입 → 자산 이력 → {@code AssetStockService.recordUnitIn}.
	 */
	void registerUnit(AssetUnitVO vo, String actorId);

	/**
	 * 자산담당자의 상태 변경: 반납(사용→보관), 수리(보관→수리), 수리 완료(수리→보관), 폐기(보관·수리→폐기, 사유 필수).
	 * 보관→사용은 여기서 할 수 없다({@link #issueUnits}만). 표에 없는 전이·확보 침범은 거부하고 아무것도 바꾸지 않는다.
	 *
	 * @param toSttus 바꿀 상태(STORED / REPAIR / DISPOSED)
	 * @param reason  사유(폐기는 필수, 나머지는 선택)
	 */
	void changeStatus(String assetNo, String toSttus, String reason, String actorId);

	/**
	 * 지급(신청 흐름 T5 전용): 신청서 reqNo의 품목 itemCode에 대해 고른 자산들을 보관 → 사용으로 바꾸고 현재 사용자 = 신청자,
	 * 자산 이력마다 신청번호를 남긴다. 하나라도 없거나, 다른 품목이거나, 보관 상태가 아니거나, 목록이 비었거나 중복이면
	 * {@link AssetItemException}으로 거부한다(호출자 트랜잭션 전체 롤백).
	 *
	 * <p><b>호출 순서</b>(T5의 한 트랜잭션): ① 신청서 행 잠금 ② {@code AssetStockService.lockItems}(관련 품목 전체)
	 * ③ 품목마다 {@code issueUnits} ④ 같은 품목에 {@code AssetStockService.deliverUnits(itemCode, assetNos.size(), reqNo, actorId)}.
	 * 이 메서드는 품목을 다시 잠그지만(이미 잠겼으면 무해) 잠금 순서를 바꾸지 않고, 확보 수량은 건드리지 않는다
	 * — 확보 해제와 DELIVER 이력은 ④가 한다.
	 *
	 * @param assetNos    지급할 자산번호(1개 이상, 중복 없음)
	 * @param applicantId 신청자(현재 사용자로 기록)
	 * @param actorId     처리자(자산담당자)
	 */
	void issueUnits(String reqNo, String itemCode, List<String> assetNos, String applicantId, String actorId);
}
