package egovframework.asset.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.egovframe.rte.fdl.idgnr.EgovIdGnrService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.init.service.AssetInitService;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.config.EgovLoginConfig;
import egovframework.com.uat.uia.service.EgovLoginService;
import egovframework.com.utl.sim.service.EgovFileScrty;
import jakarta.annotation.Resource;

/**
 * 1단계 초기화: 스키마·시드, 데모 암호 1회 적용, 기본 계정 비활성, 권한 URL, 트랜잭션 적용.
 */
class AssetInitTest extends AssetIntegrationSupport {

	private static final List<String> SEED_USERS = List.of("admin1", "admin2", "keeper", "head1", "head2", "user1",
			"user2", "user3", "user4");

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "assetInitService")
	private AssetInitService assetInitService;

	@Resource(name = "loginService")
	private EgovLoginService loginService;

	@Resource(name = "egovLoginConfig")
	private EgovLoginConfig egovLoginConfig;

	@Resource(name = "assetItemIdGnrService")
	private EgovIdGnrService assetItemIdGnrService;

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	@Test
	void seedIsLoaded() {
		JdbcTemplate jdbc = jdbc();
		assertEquals(3, jdbc.queryForObject(
				"SELECT COUNT(*) FROM COMTNORGNZTINFO WHERE ORGNZT_ID IN ('ORGNZT_0000000000001','ORGNZT_0000000000002','ORGNZT_0000000000003')",
				Integer.class));
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ASSET_DEPT_HEAD", Integer.class));
		assertEquals(2, jdbc.queryForObject(
				"SELECT COUNT(*) FROM COMTNEMPLYRSCRTYESTBS WHERE AUTHOR_CODE = 'ROLE_ADMIN'", Integer.class));
		assertEquals(List.of("ROLE_USER"), jdbc.queryForList(
				"SELECT PARNTS_ROLE FROM COMTNROLES_HIERARCHY WHERE CHLDRN_ROLE = 'ROLE_ASSET'", String.class));
		assertEquals(0, jdbc.queryForObject(
				"SELECT COUNT(*) FROM COMTNROLES_HIERARCHY WHERE PARNTS_ROLE = 'ROLE_ASSET'", Integer.class));
		assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM ASSET_ITEM", Integer.class) >= 8);
		// 초기화 SQL의 한글이 이중 인코딩되지 않는다(기반 DML과 업무 DML 모두)
		assertEquals("비품 신청", jdbc.queryForObject("SELECT MENU_NM FROM COMTNMENUINFO WHERE MENU_NO = 29100000", String.class));
		assertEquals("통합게시판", jdbc.queryForObject(
				"SELECT CODE_NM FROM COMTCCMMNDETAILCODE WHERE CODE_ID = 'COM101' AND CODE = 'BBST01'", String.class));
	}

	@Test
	void demoPasswordIsAppliedToSeedAccounts() throws Exception {
		String demo = System.getenv("DEMO_PASSWORD");
		for (String id : SEED_USERS) {
			String stored = jdbc().queryForObject("SELECT PASSWORD FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?", String.class, id);
			assertEquals(EgovFileScrty.encryptPassword(demo, id), stored, id);
		}
		assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_SETUP WHERE SETUP_KEY = 'DEMO_PASSWORD'", Integer.class));
	}

	@Test
	void demoPasswordIsNotAppliedTwice() throws Exception {
		String before = jdbc().queryForObject("SELECT PASSWORD FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = 'user1'", String.class);
		assertEquals(0, assetInitService.applyDemoPasswordOnce("another-value"));
		String after = jdbc().queryForObject("SELECT PASSWORD FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = 'user1'", String.class);
		assertEquals(before, after);
	}

	@Test
	void missingDemoPasswordStopsStartup() {
		assertThrows(IllegalStateException.class, () -> assetInitService.applyDemoPasswordOnce(""));
		assertThrows(IllegalStateException.class, () -> assetInitService.applyDemoPasswordOnce(null));
	}

	@Test
	void defaultAccountsCannotLogIn() throws Exception {
		Map<String, String> accounts = Map.of("TEST1", "USR", "webmaster", "USR", "USER", "GNR", "ENTERPRISE", "ENT");
		for (Map.Entry<String, String> account : accounts.entrySet()) {
			LoginVO vo = new LoginVO();
			vo.setId(account.getKey());
			vo.setUserSe(account.getValue());
			vo.setPassword("anything");
			Map<?, ?> lockInfo = loginService.selectLoginIncorrect(vo);
			assertEquals("L", loginService.processLoginIncorrect(vo, lockInfo), account.getKey());
		}
		// 권한 행도 없다(스프링 시큐리티가 사용자를 찾지 못한다)
		assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM COMTNEMPLYRSCRTYESTBS WHERE SCRTY_DTRMN_TRGET_ID IN "
				+ "('USRCNFRM_00000000000','USRCNFRM_00000000001','USRCNFRM_00000000002','USRCNFRM_99999999999')", Integer.class));
		assertTrue(egovLoginConfig.isLock());
		assertEquals(5, egovLoginConfig.getLockCount());
	}

	@Test
	void assetUrlPatternsComeBeforeCatchAllAndFollowRoles() {
		// 기반 security 설정의 sqlRolesAndUrl과 같은 순서
		List<Map<String, Object>> rows = jdbc().queryForList("SELECT a.ROLE_CODE, a.ROLE_PTTRN url, b.AUTHOR_CODE authority "
				+ "FROM COMTNROLEINFO a, COMTNAUTHORROLERELATE b WHERE a.ROLE_CODE = b.ROLE_CODE AND a.ROLE_TY = 'url' ORDER BY a.ROLE_SORT");
		int firstCatchAll = -1;
		int lastAsset = -1;
		for (int i = 0; i < rows.size(); i++) {
			String code = (String) rows.get(i).get("ROLE_CODE");
			if ("web-000003".equals(code) && firstCatchAll < 0) {
				firstCatchAll = i;
			}
			if (code.startsWith("asset-")) {
				lastAsset = i;
			}
		}
		assertTrue(lastAsset >= 0 && lastAsset < firstCatchAll);

		assertEquals(List.of("ROLE_ASSET"), authorities("asset-000003"));
		assertEquals(List.of("ROLE_ADMIN"), authorities("asset-000004"));
		assertTrue(matches("asset-000003", "/asset/item/list.do"));
		assertTrue(matches("asset-000003", "/asset/shortage/result.do"));
		assertFalse(matches("asset-000003", "/asset/req/form.do"));
		assertTrue(matches("asset-000004", "/asset/depthead/list.do"));
		assertTrue(matches("asset-000006", "/cop/bbs/insertArticle.do"));
		assertFalse(matches("asset-000006", "/cop/bbs/selectArticleList.do"));
		assertTrue(matches("asset-000006", "/cmm/fms/deleteFileInfs.do"));
		assertFalse(matches("asset-000006", "/cmm/fms/FileDown.do"));
		assertTrue(matches("asset-000008", "/uss/umt/EgovEmplyrManage.do"));
		assertFalse(matches("asset-000008", "/sec/ram/accessDenied.do"));
		assertTrue(matches("asset-000008", "/sym/mnu/mpm/EgovMenuManageSelect.do"));
		// 역할별 메뉴가 나오는 포털 메인화면은 모든 로그인 사용자가 본다
		assertFalse(matches("asset-000008", "/sym/mnu/mpm/EgovMainMenuHome.do"));
		// 공통컴포넌트 관리 영역은 관리자만(신청자가 부서권한관리로 스스로 권한을 올리는 경로 차단)
		assertEquals(List.of("ROLE_ADMIN"), authorities("asset-000009"));
		for (String url : List.of("/sec/drm/EgovDeptAuthorInsert.do", "/sec/rmt/EgovRoleList.do", "/sym/ccm/cca/EgovCcmCmmnCodeList.do",
				"/sym/prm/EgovProgramListManageSelect.do", "/sym/log/lgm/SelectSysLogList.do", "/uat/uap/selectLoginPolicyList.do",
				"/sts/ust/selectUserStats.do", "/utl/sys/srm/getServerResrceMntrngList.do", "/uss/ion/pwm/registPopup.do",
				"/uss/ion/msi/addMainImage.do", "/uss/ion/lsi/removeLoginScrinImage.do", "/uss/sam/stp/StplatCnUpdt.do")) {
			assertTrue(matches("asset-000009", url), url);
		}
		for (String url : List.of("/sym/mnu/mpm/EgovMainMenuHome.do", "/sym/mnu/mpm/EgovMainMenuLeft.do", "/sec/ram/accessDenied.do",
				"/sec/rnc/EgovRlnmCnfirm.do", "/sym/cal/EgovNormalCalPopup.do", "/sym/ccm/zip/EgovCcmZipSearchPopup.do",
				"/sym/bat/getBatchResultList.do", "/asset/req/form.do", "/uss/ion/pwm/openPopupManage.do", "/uss/ion/pwm/listMainPopup.do",
				"/uss/ion/msi/getMainImage.do", "/uss/ion/lsi/getLoginScrinImage.do", "/uss/umt/EgovStplatCnfirmMber.do")) {
			assertFalse(matches("asset-000009", url), url);
		}
		// 배치 화면 허용 패턴('0')이 관리 영역 차단 패턴('05')보다 먼저 평가된다
		assertTrue(sortOf("asset-000005").compareTo(sortOf("asset-000009")) < 0);
		assertTrue(sortOf("asset-000009").compareTo(sortOf("web-000003")) < 0);
	}

	private List<String> authorities(String roleCode) {
		return jdbc().queryForList("SELECT AUTHOR_CODE FROM COMTNAUTHORROLERELATE WHERE ROLE_CODE = ? ORDER BY AUTHOR_CODE",
				String.class, roleCode);
	}

	private String sortOf(String roleCode) {
		return jdbc().queryForObject("SELECT ROLE_SORT FROM COMTNROLEINFO WHERE ROLE_CODE = ?", String.class, roleCode);
	}

	private boolean matches(String roleCode, String url) {
		String pattern = jdbc().queryForObject("SELECT ROLE_PTTRN FROM COMTNROLEINFO WHERE ROLE_CODE = ?", String.class, roleCode);
		return Pattern.compile(pattern).matcher(url).matches();
	}

	@Test
	void assetServicesAreTransactional() {
		assertTrue(AopUtils.isAopProxy(assetInitService));
		boolean tx = false;
		for (Advisor advisor : ((Advised) assetInitService).getAdvisors()) {
			tx |= advisor.getAdvice() instanceof TransactionInterceptor;
		}
		assertTrue(tx);
	}

	@Test
	void businessKeysComeFromIdGenerator() throws Exception {
		String id = assetItemIdGnrService.getNextStringId();
		assertTrue(id.matches("ITEM_\\d{15}"), id);
		assertTrue(Long.parseLong(id.substring(5)) >= 101);
	}
}
