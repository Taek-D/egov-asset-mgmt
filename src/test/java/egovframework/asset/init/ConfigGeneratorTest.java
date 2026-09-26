package egovframework.asset.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 설정 생성기: 키 짝 해시, DB 암호 왕복 복호화, 필수 값 누락 시 실패.
 */
class ConfigGeneratorTest {

	@TempDir
	Path out;

	private Map<String, String> env() {
		Map<String, String> env = new HashMap<>();
		env.put("MYSQL_USER", "tester");
		env.put("MYSQL_PASSWORD", "db-Secret#1");
		env.put("CRYPTO_KEY", "unit-test-key-123");
		env.put("DEMO_PASSWORD", "demo");
		return env;
	}

	private Properties load(String resource) throws Exception {
		Properties p = new Properties();
		p.load(new StringReader(Files.readString(out.resolve(resource))));
		return p;
	}

	@Test
	void writesKeyHashAndEncryptedDbPasswordThatDecryptsBack() throws Exception {
		ConfigGenerator.generate(env(), out);

		Properties crypto = load(ConfigGenerator.CRYPTO);
		assertEquals("unit-test-key-123", crypto.getProperty("algorithmKey"));
		String expectedHash = Base64.getEncoder().encodeToString(
				MessageDigest.getInstance("SHA-256").digest("unit-test-key-123".getBytes(StandardCharsets.UTF_8)));
		assertEquals(expectedHash, crypto.getProperty("algorithmKeyHash"));
		assertEquals("SHA-256", crypto.getProperty("algorithm"));

		Properties globals = load(ConfigGenerator.GLOBALS);
		assertEquals("tester", globals.getProperty("Globals.mysql.UserName"));
		assertEquals("unit-test-key-123", globals.getProperty("Globals.File.algorithmKey"));
		assertTrue(globals.getProperty("Globals.mysql.Url").contains("127.0.0.1:3306"));

		String stored = globals.getProperty("Globals.mysql.Password");
		byte[] cipher = Base64.getDecoder().decode(URLDecoder.decode(stored, StandardCharsets.UTF_8));
		byte[] plain = ConfigGenerator.ariaService(expectedHash).decrypt(cipher, "unit-test-key-123");
		assertEquals("db-Secret#1", new String(plain, StandardCharsets.UTF_8));
	}

	@Test
	void failsWhenRequiredValuesAreMissing() {
		Map<String, String> env = env();
		env.remove("DEMO_PASSWORD");
		env.put("CRYPTO_KEY", " ");
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> ConfigGenerator.generate(env, out));
		assertTrue(e.getMessage().contains("CRYPTO_KEY"));
		assertTrue(e.getMessage().contains("DEMO_PASSWORD"));
		assertTrue(Files.notExists(out.resolve(ConfigGenerator.GLOBALS)));
	}

	@Test
	void rejectsPublicDefaultKey() {
		Map<String, String> env = env();
		env.put("CRYPTO_KEY", "egovframe");
		assertThrows(IllegalStateException.class, () -> ConfigGenerator.generate(env, out));
	}
}
