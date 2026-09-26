package egovframework.asset.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.egovframe.rte.fdl.property.EgovPropertyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.req.service.AssetReqService;
import egovframework.asset.req.web.AssetReqController;
import egovframework.asset.stock.service.AssetStockService;
import egovframework.asset.unit.service.AssetUnitService;
import egovframework.com.cmm.LoginVO;
import egovframework.com.cmm.service.EgovUserDetailsService;
import egovframework.com.cmm.util.EgovDoubleSubmitHelper;
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 신청 작성 화면의 1회용 제출 토큰(§8, §14 #12): 같은 토큰으로 두 번(연속·동시) 보내도 신청서는 한 건.
 * 토큰은 웹 계층에 있으므로 컨트롤러를 직접 만들어 MockMvc(standalone)로 부른다. 세션 토큰은 JSP 태그
 * {@code <double-submit:preventer tokenKey="assetReqForm"/>}가 만드는 것과 같게 넣는다.
 */
class AssetReqWebTest extends AssetIntegrationSupport {

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "assetReqService")
	private AssetReqService reqService;

	@Resource(name = "assetItemService")
	private AssetItemService itemService;

	@Resource(name = "assetStockService")
	private AssetStockService stockService;

	@Resource(name = "assetUnitService")
	private AssetUnitService unitService;

	@Resource(name = "propertiesService")
	private EgovPropertyService propertiesService;

	private AssetReqFixture f;
	private EgovUserDetailsService originalUserService;

	@BeforeEach
	void setUp() {
		f = new AssetReqFixture(new JdbcTemplate(dataSource), itemService, stockService, unitService);
		originalUserService = new EgovUserDetailsHelper().getEgovUserDetailsService();
	}

	@AfterEach
	void cleanUp() {
		new EgovUserDetailsHelper().setEgovUserDetailsService(originalUserService);
		f.cleanUp();
	}

	private MockMvc mvc() {
		AssetReqController controller = new AssetReqController();
		ReflectionTestUtils.setField(controller, "assetReqService", reqService);
		ReflectionTestUtils.setField(controller, "assetItemService", itemService);
		ReflectionTestUtils.setField(controller, "assetUnitService", unitService);
		ReflectionTestUtils.setField(controller, "propertiesService", propertiesService);
		return MockMvcBuilders.standaloneSetup(controller).build();
	}

	private static void loginAs(String emplyrId) {
		LoginVO user = new LoginVO();
		user.setId(emplyrId);
		new EgovUserDetailsHelper().setEgovUserDetailsService(new EgovUserDetailsService() {
			@Override
			public Object getAuthenticatedUser() {
				return user;
			}

			@Override
			public List<String> getAuthorities() {
				return List.of("ROLE_USER");
			}

			@Override
			public Boolean isAuthenticated() {
				return true;
			}
		});
	}

	/** JSP 태그가 폼을 그릴 때 하는 일: 세션에 토큰 맵을 두고 토큰을 만든다 */
	private static String issueToken(MockHttpSession session) {
		@SuppressWarnings("unchecked")
		Map<String, String> map = (Map<String, String>) session.getAttribute(EgovDoubleSubmitHelper.SESSION_TOKEN_KEY);
		if (map == null) {
			map = new HashMap<>();
			session.setAttribute(EgovDoubleSubmitHelper.SESSION_TOKEN_KEY, map);
		}
		String token = EgovDoubleSubmitHelper.getNewUUID();
		map.put(AssetReqController.TOKEN_KEY, token);
		return token;
	}

	private static MockHttpServletRequestBuilder submit(MockHttpSession session, String token, String itemCode) {
		MockHttpServletRequestBuilder b = post("/asset/req/submit.do").session(session).param("reqResn", "토큰 시험")
				.param("lines[0].itemCode", itemCode).param("lines[0].reqQty", "1");
		return token == null ? b : b.param(EgovDoubleSubmitHelper.PARAMETER_NAME, token);
	}

	@Test
	void sameTokenCreatesExactlyOneRequest() throws Exception {
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		String applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		String q = f.qtyItem(10);
		loginAs(applicant);
		MockMvc mvc = mvc();
		MockHttpSession session = new MockHttpSession();

		// 토큰 없이(폼을 거치지 않은 요청) → 거부
		issueToken(session);
		mvc.perform(submit(session, null, q)).andExpect(status().isOk()).andExpect(model().attributeExists("message"));
		assertEquals(0, f.reqCnt(applicant));

		// 연속 두 번(새로고침 재전송): 첫 번째만 신청서가 된다
		String token = issueToken(session);
		mvc.perform(submit(session, token, q)).andExpect(status().is3xxRedirection());
		mvc.perform(submit(session, token, q)).andExpect(status().isOk()).andExpect(model().attributeExists("message"));
		assertEquals(1, f.reqCnt(applicant));

		// 동시에 두 번(더블클릭)
		String token2 = issueToken(session);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> futures = List.of(0, 1).stream().map(i -> pool.submit(() -> {
				start.await();
				return mvc.perform(submit(session, token2, q)).andReturn().getResponse().getStatus();
			})).toList();
			start.countDown();
			int redirects = 0;
			for (Future<Integer> fu : futures) {
				int st = fu.get(60, TimeUnit.SECONDS);
				if (st / 100 == 3) {
					redirects++;
				}
			}
			assertEquals(1, redirects);
		} finally {
			pool.shutdownNow();
		}
		assertEquals(2, f.reqCnt(applicant));

		// 세션이 끊겨 토큰 맵이 없어도 오류 화면이 아니라 거부 메시지
		mvc.perform(submit(new MockHttpSession(), token2, q)).andExpect(status().isOk()).andExpect(model().attributeExists("message"));
		assertEquals(2, f.reqCnt(applicant));
	}
}
