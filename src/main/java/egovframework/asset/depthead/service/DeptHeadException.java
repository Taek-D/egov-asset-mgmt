package egovframework.asset.depthead.service;

/**
 * 부서장 지정 규칙 위반. 메시지는 화면에 그대로 보여 줄 수 있는 문장이다.
 */
public class DeptHeadException extends Exception {

	private static final long serialVersionUID = 1L;

	public DeptHeadException(String message) {
		super(message);
	}
}
