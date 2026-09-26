package egovframework.asset.item.service;

/**
 * 품목·재고 업무 규칙 위반. 메시지는 화면에 그대로 보여 줄 수 있는 문장이다.
 * 서비스 트랜잭션 안에서 던져지면 그 트랜잭션(호출자의 바깥 트랜잭션 포함)은 롤백된다.
 */
public class AssetItemException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AssetItemException(String message) {
		super(message);
	}
}
