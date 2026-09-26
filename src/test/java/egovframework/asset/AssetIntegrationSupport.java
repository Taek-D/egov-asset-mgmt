package egovframework.asset;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * 업무 통합 테스트 기반. 앱과 같은 공통컴포넌트 본 Spring 설정을 읽고 실제 MySQL에 붙는다.
 * (src/test/resources의 공통컴포넌트 테스트용 context-*.xml은 섞이지 않도록 main 경로만 읽는다)
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(locations = "file:src/main/resources/egovframework/spring/com/**/context-*.xml")
@ActiveProfiles({ "mysql", "security" })
public abstract class AssetIntegrationSupport {
}
