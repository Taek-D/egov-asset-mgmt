package egovframework.asset.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import egovframework.asset.AssetIntegrationSupport;
import egovframework.asset.item.service.AssetItemException;
import egovframework.asset.item.service.AssetItemService;
import egovframework.asset.item.service.AssetItemVO;
import egovframework.asset.stock.service.AssetStockService;
import jakarta.annotation.Resource;

/**
 * 품목 관리: 등록·이름 유일성·사용 중지/재사용·유형 변경·삭제 규칙.
 */
class AssetItemTest extends AssetIntegrationSupport {

	@Resource(name = "egov.dataSource")
	private DataSource dataSource;

	@Resource(name = "assetItemService")
	private AssetItemService itemService;

	@Resource(name = "assetStockService")
	private AssetStockService stockService;

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private static String uniqueName(String prefix) {
		return prefix + "-" + System.nanoTime();
	}

	private static AssetItemVO item(String name, String ty) {
		AssetItemVO vo = new AssetItemVO();
		vo.setItemNm(name);
		vo.setItemTy(ty);
		vo.setClCode("OFFICE");
		vo.setUnitCode("EA");
		vo.setMinQty(3);
		return vo;
	}

	private static String reqNo() {
		return "REQ_T2" + String.format("%014d", System.nanoTime() % 100_000_000_000_000L);
	}

	@Test
	void insertCreatesItemWithGeneratedCodeAndZeroStock() {
		String name = uniqueName("등록");
		String code = itemService.insertItem(item(name, "QTY"), "keeper");
		assertTrue(code.matches("ITEM_\\d{15}"), code);

		AssetItemVO saved = itemService.selectItem(code);
		assertEquals(name, saved.getItemNm());
		assertEquals("QTY", saved.getItemTy());
		assertEquals("Y", saved.getUseAt());
		assertEquals(0, saved.getStockQty());
		assertEquals(0, saved.getAvailQty());
		assertEquals(3, saved.getMinQty());
		assertEquals("사무용품", saved.getClCodeNm());
		assertFalse(saved.isHasHist());
	}

	@Test
	void insertValidatesInput() {
		assertThrows(AssetItemException.class, () -> itemService.insertItem(item(" ", "QTY"), "keeper"));
		assertThrows(AssetItemException.class, () -> itemService.insertItem(item(uniqueName("유형"), "BAD"), "keeper"));
		AssetItemVO negative = item(uniqueName("최소"), "QTY");
		negative.setMinQty(-1);
		assertThrows(AssetItemException.class, () -> itemService.insertItem(negative, "keeper"));
	}

	@Test
	void activeNamesMustBeUnique() {
		String name = uniqueName("중복");
		String first = itemService.insertItem(item(name, "QTY"), "keeper");
		AssetItemException ex = assertThrows(AssetItemException.class, () -> itemService.insertItem(item(name, "QTY"), "keeper"));
		assertTrue(ex.getMessage().contains(name), ex.getMessage());

		// 사용 중지된 품목의 이름은 새 품목이 쓸 수 있다
		itemService.deactivateItem(first, "keeper");
		assertEquals("N", itemService.selectItem(first).getUseAt());
		String second = itemService.insertItem(item(name, "QTY"), "keeper");

		// 이름이 이미 다른 사용 중 품목에 쓰이면 다시 사용으로 되돌릴 수 없다
		assertThrows(AssetItemException.class, () -> itemService.activateItem(first, "keeper"));
		assertEquals("N", itemService.selectItem(first).getUseAt());

		// 새 품목을 중지하면 되돌릴 수 있다
		itemService.deactivateItem(second, "keeper");
		itemService.activateItem(first, "keeper");
		assertEquals("Y", itemService.selectItem(first).getUseAt());
	}

	@Test
	void renameToActiveNameIsRefused() {
		String taken = uniqueName("사용중");
		itemService.insertItem(item(taken, "QTY"), "keeper");
		String code = itemService.insertItem(item(uniqueName("변경"), "QTY"), "keeper");
		AssetItemVO vo = itemService.selectItem(code);
		vo.setItemNm(taken);
		assertThrows(AssetItemException.class, () -> itemService.updateItem(vo, "keeper"));

		// 자기 이름 그대로 저장은 된다
		AssetItemVO same = itemService.selectItem(code);
		same.setMinQty(7);
		itemService.updateItem(same, "keeper");
		assertEquals(7, itemService.selectItem(code).getMinQty());
	}

	@Test
	void typeCanChangeOnlyBeforeHistory() {
		String code = itemService.insertItem(item(uniqueName("유형변경"), "QTY"), "keeper");
		AssetItemVO vo = itemService.selectItem(code);
		vo.setItemTy("UNIT");
		itemService.updateItem(vo, "keeper");
		assertEquals("UNIT", itemService.selectItem(code).getItemTy());

		AssetItemVO back = itemService.selectItem(code);
		back.setItemTy("QTY");
		itemService.updateItem(back, "keeper");

		stockService.receive(code, 5, null, "keeper");
		AssetItemVO withHist = itemService.selectItem(code);
		assertTrue(withHist.isHasHist());
		withHist.setItemTy("UNIT");
		assertThrows(AssetItemException.class, () -> itemService.updateItem(withHist, "keeper"));
		assertEquals("QTY", itemService.selectItem(code).getItemTy());
	}

	@Test
	void typeChangeAndDeleteRefusedWhenUnitAssetsExist() {
		String code = itemService.insertItem(item(uniqueName("자산있음"), "UNIT"), "keeper");
		jdbc().update("INSERT INTO ASSET_UNIT (ASSET_NO, ITEM_CODE, STTUS, FRST_REGISTER_ID, FRST_REGIST_PNTTM) VALUES (?, ?, 'STORED', 'keeper', NOW())",
				"T2-" + System.nanoTime(), code);
		AssetItemVO vo = itemService.selectItem(code);
		vo.setItemTy("QTY");
		assertThrows(AssetItemException.class, () -> itemService.updateItem(vo, "keeper"));
		assertThrows(AssetItemException.class, () -> itemService.deleteItem(code));
	}

	@Test
	void typeChangeAndDeleteRefusedWhenRequestLineExists() {
		String code = itemService.insertItem(item(uniqueName("신청있음"), "QTY"), "keeper");
		String reqNo = reqNo();
		jdbc().update("INSERT INTO ASSET_REQ (REQ_NO, APPLCNT_ID, ORGNZT_ID, STTUS, REQ_PNTTM) VALUES (?, 'user1', 'ORGNZT_0000000000001', 'PENDING', NOW())", reqNo);
		jdbc().update("INSERT INTO ASSET_REQ_LINE (REQ_NO, ITEM_CODE, REQ_QTY) VALUES (?, ?, 1)", reqNo, code);
		AssetItemVO vo = itemService.selectItem(code);
		assertTrue(vo.isHasHist());
		vo.setItemTy("UNIT");
		assertThrows(AssetItemException.class, () -> itemService.updateItem(vo, "keeper"));
		assertThrows(AssetItemException.class, () -> itemService.deleteItem(code));
	}

	@Test
	void deleteAllowedOnlyWithoutHistory() {
		String clean = itemService.insertItem(item(uniqueName("삭제"), "QTY"), "keeper");
		itemService.deleteItem(clean);
		assertEquals(0, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, clean));

		String used = itemService.insertItem(item(uniqueName("삭제불가"), "QTY"), "keeper");
		stockService.receive(used, 1, null, "keeper");
		AssetItemException ex = assertThrows(AssetItemException.class, () -> itemService.deleteItem(used));
		assertTrue(ex.getMessage().contains("사용 중지"), ex.getMessage());
		assertEquals(1, jdbc().queryForObject("SELECT COUNT(*) FROM ASSET_ITEM WHERE ITEM_CODE = ?", Integer.class, used));
	}

	@Test
	void listSearchesByNameAndPages() {
		String tag = "목록" + System.nanoTime();
		for (int i = 0; i < 3; i++) {
			itemService.insertItem(item(tag + "-" + i, i == 2 ? "UNIT" : "QTY"), "keeper");
		}
		AssetItemVO search = new AssetItemVO();
		search.setSearchKeyword(tag);
		search.setFirstIndex(0);
		search.setRecordCountPerPage(2);
		assertEquals(3, itemService.selectItemListTotCnt(search));
		assertEquals(2, itemService.selectItemList(search).size());

		search.setSearchItemTy("UNIT");
		assertEquals(1, itemService.selectItemListTotCnt(search));
	}
}
