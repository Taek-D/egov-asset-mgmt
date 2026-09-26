package egovframework.asset.init;

import org.springframework.stereotype.Component;

import egovframework.asset.init.service.AssetInitService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * 앱이 기동할 때 데모 암호를 한 번 적용한다. 값이 없으면 예외로 기동을 중단한다.
 */
@Component
public class AssetStartupInitializer {

	@Resource(name = "assetInitService")
	private AssetInitService assetInitService;

	@PostConstruct
	public void init() throws Exception {
		assetInitService.applyDemoPasswordOnce(System.getenv("DEMO_PASSWORD"));
	}
}
