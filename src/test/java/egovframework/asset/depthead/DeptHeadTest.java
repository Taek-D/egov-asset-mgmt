package egovframework.asset.depthead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.depthead.service.DeptHeadException;
import egovframework.asset.depthead.service.DeptHeadService;
import egovframework.asset.depthead.service.DeptHeadVO;
import egovframework.com.cmm.ComDefaultVO;
import jakarta.annotation.Resource;

/**
 * 부서장 지정과 승인자 결정(명세 §3, §7).
 * 픽스처 부서·사용자·권한 행은 테스트마다 만들고 지운다. 시드 관리자(admin1, admin2)는 로그인 가능하다고 가정한다.
 */
class DeptHeadTest extends AssetIntegrationSupport {

	private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000_000L);
	private static final List<String> SEED_ADMINS = List.of("admin1", "admin2");

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "deptHeadService")
	private DeptHeadService deptHeadService;

	private JdbcTemplate jdbc;
	private final List<String> depts = new ArrayList<>();
	private final List<String> users = new ArrayList<>();
	private final List<String> esntls = new ArrayList<>();

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@AfterEach
	void cleanUp() {
		for (String d : depts) {
			jdbc.update("DELETE FROM ASSET_DEPT_HEAD WHERE ORGNZT_ID = ?", d);
		}
		for (String e : esntls) {
			jdbc.update("DELETE FROM COMTNEMPLYRSCRTYESTBS WHERE SCRTY_DTRMN_TRGET_ID = ?", e);
		}
		for (String u : users) {
			jdbc.update("DELETE FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?", u);
		}
		for (String d : depts) {
			jdbc.update("DELETE FROM COMTNORGNZTINFO WHERE ORGNZT_ID = ?", d);
		}
	}

	// ---------- 승인자 결정 ----------

	@Test
	void validHeadIsTheOnlyApprover() throws Exception {
		String d = dept();
		String head = user(d, "ROLE_USER");
		String applicant = user(d, "ROLE_USER");
		head(d, head);

		assertEquals(List.of(head), deptHeadService.selectApproverIds(d, applicant));
		assertEquals(head, deptHeadService.selectValidHeadId(d));
		assertTrue(deptHeadService.hasApprover(d, applicant));
		assertTrue(deptHeadService.canApprove(head, d, applicant));
		assertFalse(deptHeadService.canApprove("admin1", d, applicant));
		assertFalse(deptHeadService.canApprove(applicant, d, applicant));
	}

	@Test
	void noHeadGoesToLoginableAdminsExceptApplicant() throws Exception {
		String d = dept();
		String adminApplicant = user(d, "ROLE_ADMIN");
		String otherAdmin = user(d, "ROLE_ADMIN");
		String lockedAdmin = user(d, "ROLE_ADMIN");
		lock(lockedAdmin);
		String disabledAdmin = user(d, "ROLE_ADMIN");
		disable(disabledAdmin);
		String plainUser = user(d, "ROLE_ASSET");

		assertNull(deptHeadService.selectValidHeadId(d));
		assertEquals(admins(otherAdmin), deptHeadService.selectApproverIds(d, adminApplicant));
		assertEquals(admins(adminApplicant, otherAdmin), deptHeadService.selectApproverIds(d, plainUser));
		assertFalse(deptHeadService.canApprove(adminApplicant, d, adminApplicant));
		assertTrue(deptHeadService.canApprove(otherAdmin, d, adminApplicant));
		assertTrue(deptHeadService.canApprove("admin1", d, adminApplicant));
		assertFalse(deptHeadService.canApprove(lockedAdmin, d, plainUser));
		assertFalse(deptHeadService.canApprove(disabledAdmin, d, plainUser));
		assertFalse(deptHeadService.canApprove(plainUser, d, adminApplicant));
		assertTrue(deptHeadService.isActiveAdmin(otherAdmin));
		assertFalse(deptHeadService.isActiveAdmin(lockedAdmin));
		assertFalse(deptHeadService.isActiveAdmin(disabledAdmin));
		assertFalse(deptHeadService.isActiveAdmin(plainUser));
	}

	@Test
	void headWhoIsTheApplicantGoesToAdmins() throws Exception {
		String d = dept();
		String head = user(d, "ROLE_USER");
		head(d, head);

		assertEquals(head, deptHeadService.selectValidHeadId(d));
		assertEquals(admins(), deptHeadService.selectApproverIds(d, head));
		assertFalse(deptHeadService.canApprove(head, d, head));
	}

	@Test
	void headMovedToAnotherDeptGoesToAdmins() throws Exception {
		String d = dept();
		String other = dept();
		String head = user(d, "ROLE_USER");
		String applicant = user(d, "ROLE_USER");
		head(d, head);
		jdbc.update("UPDATE COMTNEMPLYRINFO SET ORGNZT_ID = ? WHERE EMPLYR_ID = ?", other, head);

		assertNull(deptHeadService.selectValidHeadId(d));
		assertEquals(admins(), deptHeadService.selectApproverIds(d, applicant));
		assertFalse(deptHeadService.canApprove(head, d, applicant));
		assertEquals(DeptHeadVO.STATUS_MOVED, listed(d).getHeadStatus());
		// 지정 기록은 자동으로 지우지 않는다
		assertEquals(head, headRow(d));
	}

	@Test
	void lockedHeadGoesToAdmins() throws Exception {
		String d = dept();
		String head = user(d, "ROLE_USER");
		String applicant = user(d, "ROLE_USER");
		head(d, head);
		lock(head);

		assertNull(deptHeadService.selectValidHeadId(d));
		assertEquals(admins(), deptHeadService.selectApproverIds(d, applicant));
		assertEquals(DeptHeadVO.STATUS_LOCKED, listed(d).getHeadStatus());
	}

	@Test
	void disabledHeadGoesToAdmins() throws Exception {
		String d = dept();
		String head = user(d, "ROLE_USER");
		String applicant = user(d, "ROLE_USER");
		head(d, head);
		disable(head);

		assertNull(deptHeadService.selectValidHeadId(d));
		assertEquals(admins(), deptHeadService.selectApproverIds(d, applicant));
		assertEquals(DeptHeadVO.STATUS_DISABLED, listed(d).getHeadStatus());
	}

	@Test
	void noApproverWhenTheOnlyLoginableAdminIsTheApplicant() throws Exception {
		String d = dept();
		String adminApplicant = user(d, "ROLE_ADMIN");
		Map<String, String> savedLocks = new java.util.HashMap<>();
		for (String a : SEED_ADMINS) {
			savedLocks.put(a, jdbc.queryForObject("SELECT LOCK_AT FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?", String.class, a));
		}
		try {
			for (String a : SEED_ADMINS) {
				lock(a);
			}
			assertEquals(List.of(), deptHeadService.selectApproverIds(d, adminApplicant));
			assertFalse(deptHeadService.hasApprover(d, adminApplicant));
			assertFalse(deptHeadService.canApprove(adminApplicant, d, adminApplicant));
		} finally {
			for (String a : SEED_ADMINS) {
				jdbc.update("UPDATE COMTNEMPLYRINFO SET LOCK_AT = ? WHERE EMPLYR_ID = ?", savedLocks.get(a), a);
			}
		}
	}

	@Test
	void validHeadDeptOfUser() throws Exception {
		String d = dept();
		String head = user(d, "ROLE_USER");
		head(d, head);
		assertEquals(d, deptHeadService.selectValidHeadOrgnztId(head));
		lock(head);
		assertNull(deptHeadService.selectValidHeadOrgnztId(head));
	}

	// ---------- 지정·해제 ----------

	@Test
	void assignRefusesUserOfAnotherDeptOrNotLoginable() throws Exception {
		String d = dept();
		String other = dept();
		String outsider = user(other, "ROLE_USER");
		String locked = user(d, "ROLE_USER");
		lock(locked);
		String disabled = user(d, "ROLE_USER");
		disable(disabled);
		String ok = user(d, "ROLE_USER");

		assertThrows(DeptHeadException.class, () -> deptHeadService.assignDeptHead(d, outsider, "admin1"));
		assertThrows(DeptHeadException.class, () -> deptHeadService.assignDeptHead(d, locked, "admin1"));
		assertThrows(DeptHeadException.class, () -> deptHeadService.assignDeptHead(d, disabled, "admin1"));
		assertThrows(DeptHeadException.class, () -> deptHeadService.assignDeptHead(d, "no-such-user", "admin1"));
		assertThrows(DeptHeadException.class, () -> deptHeadService.assignDeptHead("ORGT4_NO_SUCH_DEPT00", ok, "admin1"));
		assertNull(headRow(d));
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ASSET_DEPT_HEAD WHERE ORGNZT_ID = 'ORGT4_NO_SUCH_DEPT00'", Integer.class));
	}

	@Test
	void assignReplacesExistingHeadAndUnassignRemovesIt() throws Exception {
		String d = dept();
		String first = user(d, "ROLE_USER");
		String second = user(d, "ROLE_ADMIN");

		deptHeadService.assignDeptHead(d, first, "admin1");
		assertEquals(first, headRow(d));
		deptHeadService.assignDeptHead(d, second, "admin2");
		assertEquals(second, headRow(d));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ASSET_DEPT_HEAD WHERE ORGNZT_ID = ?", Integer.class, d));
		assertEquals("admin2", jdbc.queryForObject("SELECT ASSIGN_ID FROM ASSET_DEPT_HEAD WHERE ORGNZT_ID = ?", String.class, d));
		assertEquals(second, deptHeadService.selectValidHeadId(d));

		deptHeadService.unassignDeptHead(d);
		assertNull(headRow(d));
		assertNull(deptHeadService.selectValidHeadId(d));
	}

	@Test
	void candidatesAreLoginableMembersOfTheDept() throws Exception {
		String d = dept();
		String other = dept();
		String a = user(d, "ROLE_USER");
		String b = user(d, "ROLE_ADMIN");
		String locked = user(d, "ROLE_USER");
		lock(locked);
		String disabled = user(d, "ROLE_USER");
		disable(disabled);
		user(other, "ROLE_USER");

		List<String> ids = deptHeadService.selectHeadCandidateList(d).stream().map(DeptHeadVO::getEmplyrId)
				.collect(Collectors.toList());
		assertEquals(List.of(a, b).stream().sorted().collect(Collectors.toList()), ids);
	}

	@Test
	void listShowsEveryDeptWithHeadStatus() throws Exception {
		String valid = dept();
		String none = dept();
		String moved = dept();
		String locked = dept();
		String h1 = user(valid, "ROLE_USER");
		head(valid, h1);
		String h2 = user(moved, "ROLE_USER");
		head(moved, h2);
		jdbc.update("UPDATE COMTNEMPLYRINFO SET ORGNZT_ID = ? WHERE EMPLYR_ID = ?", valid, h2);
		String h3 = user(locked, "ROLE_USER");
		head(locked, h3);
		lock(h3);

		ComDefaultVO all = new ComDefaultVO();
		all.setFirstIndex(0);
		all.setRecordCountPerPage(100000);
		List<DeptHeadVO> list = deptHeadService.selectDeptHeadList(all);
		assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM COMTNORGNZTINFO", Integer.class).intValue(), list.size());
		assertEquals(list.size(), deptHeadService.selectDeptHeadListCnt(all));

		DeptHeadVO v = listed(valid);
		assertEquals(DeptHeadVO.STATUS_VALID, v.getHeadStatus());
		assertEquals(h1, v.getEmplyrId());
		assertEquals("T4사용자", v.getUserNm());
		assertTrue(v.isValid());
		DeptHeadVO n = listed(none);
		assertNull(n.getEmplyrId());
		assertNull(n.getHeadStatus());
		assertFalse(n.isValid());
		assertEquals(DeptHeadVO.STATUS_MOVED, listed(moved).getHeadStatus());
		assertFalse(listed(moved).isValid());
		assertEquals(DeptHeadVO.STATUS_LOCKED, listed(locked).getHeadStatus());

		// 페이징: 한 쪽에 2건
		ComDefaultVO page = new ComDefaultVO();
		page.setFirstIndex(0);
		page.setRecordCountPerPage(2);
		assertEquals(2, deptHeadService.selectDeptHeadList(page).size());
	}

	@Test
	void seedHeadsAreValid() throws Exception {
		assertEquals("head1", deptHeadService.selectValidHeadId("ORGNZT_0000000000001"));
		assertEquals("head2", deptHeadService.selectValidHeadId("ORGNZT_0000000000002"));
		assertNull(deptHeadService.selectValidHeadId("ORGNZT_0000000000003"));
		assertEquals(List.of("head1"), deptHeadService.selectApproverIds("ORGNZT_0000000000001", "user1"));
		assertEquals(SEED_ADMINS, deptHeadService.selectApproverIds("ORGNZT_0000000000003", "user3"));
	}

	// ---------- 픽스처 ----------

	private long next() {
		return SEQ.incrementAndGet();
	}

	private String dept() {
		long n = next();
		String id = String.format("ORGT4%015d", n);
		jdbc.update("INSERT INTO COMTNORGNZTINFO (ORGNZT_ID, ORGNZT_NM, ORGNZT_DC) VALUES (?, ?, 'T4 테스트')", id,
				"T4부서" + (n % 1_000_000));
		depts.add(id);
		return id;
	}

	private String user(String orgnztId, String authorCode) {
		long n = next();
		String id = String.format("t4u%012d", n);
		String esntl = String.format("ESNT4%015d", n);
		jdbc.update("INSERT INTO COMTNEMPLYRINFO (EMPLYR_ID, ORGNZT_ID, USER_NM, PASSWORD, HOUSE_ADRES, PASSWORD_HINT, PASSWORD_CNSR, "
				+ "HOUSE_END_TELNO, AREA_NO, HOUSE_MIDDLE_TELNO, ZIP, EMPLYR_STTUS_CODE, ESNTL_ID, SBSCRB_DE) "
				+ "VALUES (?, ?, 'T4사용자', '!test', '가상로', 'P01', '테스트', '0000', '02', '0000', '000000', 'P', ?, SYSDATE())",
				id, orgnztId, esntl);
		users.add(id);
		esntls.add(esntl);
		jdbc.update("INSERT INTO COMTNEMPLYRSCRTYESTBS (SCRTY_DTRMN_TRGET_ID, MBER_TY_CODE, AUTHOR_CODE) VALUES (?, 'USR03', ?)",
				esntl, authorCode);
		return id;
	}

	private void head(String orgnztId, String emplyrId) {
		jdbc.update("INSERT INTO ASSET_DEPT_HEAD (ORGNZT_ID, EMPLYR_ID, ASSIGN_ID, ASSIGN_PNTTM) VALUES (?, ?, 'admin1', SYSDATE())",
				orgnztId, emplyrId);
	}

	private void lock(String emplyrId) {
		jdbc.update("UPDATE COMTNEMPLYRINFO SET LOCK_AT = 'Y' WHERE EMPLYR_ID = ?", emplyrId);
	}

	/** 비활성 = 잠금 표시 + 권한 행 제거 */
	private void disable(String emplyrId) {
		lock(emplyrId);
		jdbc.update("DELETE FROM COMTNEMPLYRSCRTYESTBS WHERE SCRTY_DTRMN_TRGET_ID = "
				+ "(SELECT ESNTL_ID FROM COMTNEMPLYRINFO WHERE EMPLYR_ID = ?)", emplyrId);
	}

	@Test
	void headWhoseAccountIsNotApprovedGoesToAdmins() throws Exception {
		String d = dept();
		String head = user(d, "ROLE_USER");
		String applicant = user(d, "ROLE_USER");
		head(d, head);
		// 공통컴포넌트 로그인은 업무사용자 상태가 'P'(승인)일 때만 된다
		jdbc.update("UPDATE COMTNEMPLYRINFO SET EMPLYR_STTUS_CODE = 'D' WHERE EMPLYR_ID = ?", head);

		assertNull(deptHeadService.selectValidHeadId(d));
		assertEquals(admins(), deptHeadService.selectApproverIds(d, applicant));
		assertEquals(DeptHeadVO.STATUS_DISABLED, listed(d).getHeadStatus());
	}

	private String headRow(String orgnztId) {
		List<String> r = jdbc.queryForList("SELECT EMPLYR_ID FROM ASSET_DEPT_HEAD WHERE ORGNZT_ID = ?", String.class, orgnztId);
		return r.isEmpty() ? null : r.get(0);
	}

	/** 시드 관리자 + 주어진 관리자, 정렬 */
	private List<String> admins(String... extra) {
		List<String> all = new ArrayList<>(SEED_ADMINS);
		all.addAll(List.of(extra));
		return all.stream().sorted().collect(Collectors.toList());
	}

	private DeptHeadVO listed(String orgnztId) throws Exception {
		ComDefaultVO all = new ComDefaultVO();
		all.setFirstIndex(0);
		all.setRecordCountPerPage(100000);
		return deptHeadService.selectDeptHeadList(all).stream().filter(v -> orgnztId.equals(v.getOrgnztId())).findFirst()
				.orElseThrow();
	}
}
