package egovframework.asset.init.service.impl;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;

import egovframework.asset.init.service.AssetInitService;
import egovframework.com.utl.sim.service.EgovFileScrty;
import jakarta.annotation.Resource;

/**
 * 기동 시 초기화 서비스 구현.
 */
@Service("assetInitService")
public class AssetInitServiceImpl extends EgovAbstractServiceImpl implements AssetInitService {

	@Resource(name = "assetInitDAO")
	private AssetInitDAO assetInitDAO;

	@Override
	public int applyDemoPasswordOnce(String demoPassword) throws Exception {
		if (demoPassword == null || demoPassword.isBlank()) {
			throw new IllegalStateException("DEMO_PASSWORD가 비어 있습니다. .env에 시드 계정의 데모 비밀번호를 넣고 다시 기동하세요.");
		}
		// 적용 기록 행을 잠가 여러 인스턴스가 동시에 떠도 한 번만 적용한다
		if (assetInitDAO.selectSetupApplied(DEMO_PASSWORD_KEY) > 0) {
			return 0;
		}
		int count = 0;
		for (String emplyrId : assetInitDAO.selectPendingDemoUserIds()) {
			// 공통컴포넌트 로그인과 같은 저장 방식
			count += assetInitDAO.updateDemoPassword(emplyrId, EgovFileScrty.encryptPassword(demoPassword, emplyrId));
		}
		assetInitDAO.insertSetup(DEMO_PASSWORD_KEY);
		egovLogger.info("데모 암호를 시드 계정 {}개에 적용했습니다.", count);
		return count;
	}
}
