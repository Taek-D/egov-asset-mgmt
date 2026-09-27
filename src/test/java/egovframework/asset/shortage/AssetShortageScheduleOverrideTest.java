package egovframework.asset.shortage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import egovframework.asset.shortage.service.AssetShortageRunVO;
import egovframework.asset.shortage.service.AssetShortageService;
import egovframework.asset.shortage.service.AssetShortageVO;

/**
 * 야간 집계 시각을 시스템 속성(운영의 CATALINA_OPTS=-D... 방식)으로 바꾸면 트리거에 실제로 반영되는지 본다.
 * 공유 테스트 컨텍스트를 건드리지 않도록 앱과 같은 설정 파일로 작은 컨텍스트를 따로 만든다.
 */
class AssetShortageScheduleOverrideTest {

	private static final String CRON_KEY = "asset.shortage.cron";
	private static final String ZONE_KEY = "asset.shortage.zone";

	/** 집계를 실제로 돌리지 않는 대역(스케줄 계산만 본다) */
	private static final AssetShortageService NO_OP = new AssetShortageService() {
		@Override
		public AssetShortageRunVO run(String runTy, String actorId) {
			return null;
		}

		@Override
		public AssetShortageRunVO selectLatestRun() {
			return null;
		}

		@Override
		public List<AssetShortageVO> selectShortageList(AssetShortageVO searchVO) {
			return List.of();
		}

		@Override
		public int selectShortageListTotCnt(AssetShortageVO searchVO) {
			return 0;
		}
	};

	@Test
	void systemPropertiesOverrideNightlySchedule() {
		System.setProperty(CRON_KEY, "0 30 3 * * *");
		System.setProperty(ZONE_KEY, "UTC");
		try (GenericXmlApplicationContext ctx = new GenericXmlApplicationContext()) {
			ctx.load("file:src/main/resources/egovframework/spring/com/context-properties.xml",
					"file:src/main/resources/egovframework/spring/com/asset/context-asset-scheduling.xml");
			ctx.registerBean("assetShortageService", AssetShortageService.class, () -> NO_OP);
			ctx.refresh();

			CronTrigger trigger = ctx.getBean("assetShortageTrigger", CronTrigger.class);
			assertEquals("0 30 3 * * *", trigger.getExpression());

			// 기본값(02:00 서울)이라면 2026-01-01T17:00Z가 나온다
			Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
			Instant next = trigger.nextExecution(new SimpleTriggerContext(fixed, fixed, fixed));
			assertEquals(Instant.parse("2026-01-01T03:30:00Z"), next);
		} finally {
			System.clearProperty(CRON_KEY);
			System.clearProperty(ZONE_KEY);
		}
		assertNull(System.getProperty(CRON_KEY));
		assertNull(System.getProperty(ZONE_KEY));
	}
}
