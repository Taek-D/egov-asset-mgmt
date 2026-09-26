package egovframework.asset.init;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.egovframe.rte.fdl.crypto.EgovPasswordEncoder;
import org.egovframe.rte.fdl.crypto.impl.EgovARIACryptoServiceImpl;

/**
 * 컨테이너 기동 전에 .env 값으로 공통컴포넌트 설정 파일을 만드는 독립 실행 도구.
 * <ul>
 * <li>egov-crypto-config.properties: algorithmKey와 짝 해시(algorithmKeyHash)</li>
 * <li>globals.properties: DB 계정과, 새 키로 암호화한 DB 암호</li>
 * </ul>
 * 원본(클래스패스의 공통컴포넌트 설정)을 읽어 해당 줄만 바꾸고 출력 디렉터리에 쓴다.
 * 빌드된 WAR와 저장소에는 비밀 값이 남지 않는다.
 *
 * 사용: java -cp classes:lib/* egovframework.asset.init.ConfigGenerator &lt;출력 classes 디렉터리&gt;
 */
public final class ConfigGenerator {

	static final String GLOBALS = "egovframework/egovProps/globals.properties";
	static final String CRYPTO = "egovframework/egovProps/conf/egov-crypto-config.properties";
	/** 공통컴포넌트 crypto 설정의 해시 알고리즘(egov-crypto-config.properties의 algorithm) */
	static final String KEY_HASH_ALGORITHM = "SHA-256";
	static final int BLOCK_SIZE = 1024;

	static final List<String> REQUIRED = List.of("MYSQL_USER", "MYSQL_PASSWORD", "CRYPTO_KEY", "DEMO_PASSWORD");

	private ConfigGenerator() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("사용법: ConfigGenerator <출력 classes 디렉터리>");
			System.exit(2);
		}
		try {
			generate(System.getenv(), Paths.get(args[0]));
		} catch (IllegalStateException e) {
			System.err.println("[설정 생성 실패] " + e.getMessage());
			System.exit(1);
		}
		System.out.println("[설정 생성] " + args[0] + " 에 crypto·DB 설정을 만들었습니다.");
	}

	/** 필수 값을 확인하고 두 설정 파일을 outDir 아래에 쓴다. */
	public static void generate(Map<String, String> env, Path outDir) throws IOException {
		List<String> missing = new ArrayList<>();
		for (String key : REQUIRED) {
			if (env.get(key) == null || env.get(key).isBlank()) {
				missing.add(key);
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException(".env에 다음 값을 채우세요: " + String.join(", ", missing));
		}
		String key = env.get("CRYPTO_KEY").trim();
		if ("egovframe".equals(key)) {
			throw new IllegalStateException("CRYPTO_KEY에 공개된 기본 키(egovframe)를 쓸 수 없습니다.");
		}
		String keyHash = keyHash(key);

		String crypto = replaceValues(readTemplate(CRYPTO), Map.of("algorithmKey", key, "algorithmKeyHash", keyHash));
		String globals = replaceValues(readTemplate(GLOBALS), Map.of(
				"Globals.mysql.UserName", env.get("MYSQL_USER").trim(),
				"Globals.mysql.Password", encryptDbPassword(env.get("MYSQL_PASSWORD"), key, keyHash),
				"Globals.File.algorithmKey", key));

		write(outDir.resolve(CRYPTO), crypto);
		write(outDir.resolve(GLOBALS), globals);
	}

	/** 공통컴포넌트가 키를 검증하는 방식과 같은 해시(EgovPasswordEncoder, SHA-256 Base64). */
	static String keyHash(String key) {
		EgovPasswordEncoder encoder = new EgovPasswordEncoder();
		encoder.setAlgorithm(KEY_HASH_ALGORITHM);
		return encoder.encryptPassword(key);
	}

	/** EgovEnvCryptoServiceImpl.encrypt와 같은 방식: ARIA 암호화 → Base64 → URL 인코딩. */
	static String encryptDbPassword(String plain, String key, String keyHash) {
		byte[] encrypted = ariaService(keyHash).encrypt(plain.getBytes(StandardCharsets.UTF_8), key);
		return URLEncoder.encode(new String(Base64.encodeBase64(encrypted, false, false), StandardCharsets.UTF_8),
				StandardCharsets.UTF_8);
	}

	static EgovARIACryptoServiceImpl ariaService(String keyHash) {
		EgovPasswordEncoder encoder = new EgovPasswordEncoder();
		encoder.setAlgorithm(KEY_HASH_ALGORITHM);
		encoder.setHashedPassword(keyHash);
		EgovARIACryptoServiceImpl aria = new EgovARIACryptoServiceImpl();
		aria.setPasswordEncoder(encoder);
		aria.setBlockSize(BLOCK_SIZE);
		return aria;
	}

	/** "키 = 값" 줄에서 지정한 키의 값만 바꾼다. 키가 원본에 없으면 실패한다. */
	static String replaceValues(String template, Map<String, String> values) {
		StringBuilder out = new StringBuilder();
		List<String> seen = new ArrayList<>();
		for (String line : template.split("\n", -1)) {
			int eq = line.indexOf('=');
			String name = eq > 0 ? line.substring(0, eq).trim() : null;
			if (name != null && !line.trim().startsWith("#") && values.containsKey(name)) {
				line = name + " = " + values.get(name);
				seen.add(name);
			}
			out.append(line).append('\n');
		}
		for (String name : values.keySet()) {
			if (!seen.contains(name)) {
				throw new IllegalStateException("설정 원본에 " + name + " 항목이 없습니다.");
			}
		}
		return out.substring(0, out.length() - 1);
	}

	private static String readTemplate(String resource) throws IOException {
		try (InputStream in = ConfigGenerator.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("클래스패스에서 " + resource + " 를 찾지 못했습니다.");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}
}
