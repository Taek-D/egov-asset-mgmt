package egovframework.asset.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
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
import egovframework.com.cmm.util.EgovUserDetailsHelper;
import jakarta.annotation.Resource;

/**
 * 신청 작성 화면의 화면별 1회용 제출 토큰(assetReqToken). 화면을 열 때마다 새 토큰을 받고, 신청서가 실제로 만들어질 때만
 * 소모된다. 토큰은 웹 계층에 있으므로 컨트롤러를 직접 만들어 MockMvc(standalone)로 부르고, JSP는 그려지지 않으므로
 * 모델 속성으로 토큰을 읽는다. 건수는 테스트 신청자 본인의 신청서 수로 센다.
 */
class AssetReqWebTest extends AssetIntegrationSupport {

	private static final String TOKEN = "assetReqToken";
	private static final String REJECT = "이미 제출했거나 만료된 화면입니다. 내 신청에서 제출 여부를 확인하세요.";

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
	private MockMvc mvc;
	private String applicant;
	private String item;

	@BeforeEach
	void setUp() {
		f = new AssetReqFixture(new JdbcTemplate(dataSource), itemService, stockService, unitService);
		originalUserService = new EgovUserDetailsHelper().getEgovUserDetailsService();
		String d = f.dept();
		String head = f.user(d, "ROLE_USER");
		applicant = f.user(d, "ROLE_USER");
		f.head(d, head);
		item = f.qtyItem(10);
		loginAs(applicant);
		mvc = mvc();
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

	/** 메뉴 진입으로 작성 화면을 새로 열고, 화면에 심긴 토큰을 돌려준다 */
	private String openForm(MockHttpSession session) throws Exception {
		String token = token(mvc.perform(get("/asset/req/form.do").session(session)).andExpect(status().isOk()).andReturn());
		assertNotNull(token);
		return token;
	}

	private static String token(MvcResult result) {
		return (String) result.getModelAndView().getModel().get(TOKEN);
	}

	private static MockHttpServletRequestBuilder submit(MockHttpSession session, String token, String itemCode, String qty) {
		MockHttpServletRequestBuilder b = post("/asset/req/submit.do").session(session).param("reqResn", "토큰 시험")
				.param("lines[0].itemCode", itemCode).param("lines[0].reqQty", qty);
		return token == null ? b : b.param(TOKEN, token);
	}

	private MockHttpServletRequestBuilder submit(MockHttpSession session, String token) {
		return submit(session, token, item, "1");
	}

	/** 거부 화면: 기존 문구, 신청서 없음 */
	private MvcResult expectRejected(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request).andExpect(status().isOk()).andExpect(model().attribute("message", REJECT)).andReturn();
	}

	@Test
	void twoTabsEachSubmitOnce() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String tab1 = openForm(session);
		String tab2 = openForm(session);
		assertNotEquals(tab1, tab2);

		mvc.perform(submit(session, tab1)).andExpect(status().is3xxRedirection());
		mvc.perform(submit(session, tab2)).andExpect(status().is3xxRedirection());
		assertEquals(2, f.reqCnt(applicant));
	}

	@Test
	void failedSubmitKeepsTokenAndRetryIsAcceptedOnce() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String t = openForm(session);

		// 줄 추가로 다시 그린 화면도 같은 토큰
		MvcResult added = mvc.perform(post("/asset/req/form.do").session(session).param("addRows", "Y").param(TOKEN, t))
				.andExpect(status().isOk()).andReturn();
		assertEquals(t, token(added));

		// 입력 형식 오류: 토큰 유지, 같은 토큰으로 다시 그림
		MvcResult badFormat = mvc.perform(submit(session, t, item, "abc")).andExpect(status().isOk()).andReturn();
		assertEquals(t, token(badFormat));
		// 규칙 위반(서비스 거부: 수량만 있고 품목 없음): 토큰 유지
		MvcResult ruleFail = mvc.perform(submit(session, t, "", "1")).andExpect(status().isOk()).andReturn();
		assertEquals("수량을 적은 줄의 품목을 선택하세요.", ruleFail.getModelAndView().getModel().get("message"));
		assertEquals(t, token(ruleFail));
		assertEquals(0, f.reqCnt(applicant));

		// 고쳐서 같은 토큰으로 다시 내면 한 번은 받아들여진다
		mvc.perform(submit(session, t)).andExpect(status().is3xxRedirection());
		assertEquals(1, f.reqCnt(applicant));

		// 성공한 토큰을 다시 내면(새로고침 재전송) 거부, 건수 그대로. 다시 그린 화면은 새 토큰
		MvcResult again = expectRejected(submit(session, t));
		assertNotEquals(t, token(again));
		assertEquals(1, f.reqCnt(applicant));
	}

	@Test
	void sameTokenConcurrentlyCreatesOne() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String t = openForm(session);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return mvc.perform(submit(session, t)).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			int redirects = 0;
			for (Future<Integer> fu : futures) {
				if (fu.get(60, TimeUnit.SECONDS) / 100 == 3) {
					redirects++;
				}
			}
			assertEquals(1, redirects);
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, f.reqCnt(applicant));
	}

	@Test
	void unknownOrMissingTokenIsRejected() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String t = openForm(session);
		expectRejected(submit(session, null));
		expectRejected(submit(session, "not-a-token"));
		// 다른 세션(세션이 끊긴 뒤)에서 온 토큰
		expectRejected(submit(new MockHttpSession(), t));
		assertEquals(0, f.reqCnt(applicant));
	}

	@Test
	void oldestTokenIsDiscardedBeyondCap() throws Exception {
		MockHttpSession session = new MockHttpSession();
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i <= AssetReqController.MAX_FORM_TOKENS; i++) {
			tokens.add(openForm(session));
		}
		// 두 번째 것은 아직 유효(거부 화면은 새 토큰을 발급해 다음 것을 밀어내므로 성공 확인을 먼저 한다)
		mvc.perform(submit(session, tokens.get(1))).andExpect(status().is3xxRedirection());
		assertEquals(1, f.reqCnt(applicant));
		expectRejected(submit(session, tokens.get(0)));
		assertEquals(1, f.reqCnt(applicant));
	}
}
