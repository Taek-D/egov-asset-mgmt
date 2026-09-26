package egovframework.asset.init.service;

/**
 * 기동 시 초기화 서비스.
 */
public interface AssetInitService {

	/** 데모 암호 적용 여부를 기록하는 키 */
	String DEMO_PASSWORD_KEY = "DEMO_PASSWORD";

	/**
	 * 시드 계정(비밀번호 자리표시 값을 가진 업무 사용자)에 데모 암호를 한 번만 적용한다.
	 * 이미 적용됐으면 아무것도 바꾸지 않는다.
	 *
	 * @param demoPassword .env의 데모 암호
	 * @return 이번에 비밀번호를 설정한 계정 수(이미 적용됐으면 0)
	 * @throws IllegalStateException 데모 암호가 비어 있을 때
	 */
	int applyDemoPasswordOnce(String demoPassword) throws Exception;
}
