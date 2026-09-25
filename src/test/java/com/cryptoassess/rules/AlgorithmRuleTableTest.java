package com.cryptoassess.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;

class AlgorithmRuleTableTest {

	private static final AlgorithmRuleTable TABLE = AlgorithmRuleTable
		.load(new ClassPathResource("rules/algorithms.test.yaml"));

	static Stream<Arguments> spellings() {
		return Stream.of(
				// 写法, 规范名, 模式, 密钥位数
				Arguments.of("SM4", "SM4", null, null), Arguments.of("sm4", "SM4", null, null),
				Arguments.of("SM4-CBC", "SM4", "CBC", null), Arguments.of("sm4_gcm", "SM4", "GCM", null),
				Arguments.of("SM4/ECB/PKCS5Padding", "SM4", "ECB", null), Arguments.of("SMS4", "SM4", null, null),
				Arguments.of(" SM4 CTR ", "SM4", "CTR", null), Arguments.of("SM4-128-CBC", "SM4", "CBC", 128),
				Arguments.of("国密SM2", "SM2", null, null), Arguments.of("SM2", "SM2", null, null),
				Arguments.of("sm2-256", "SM2", null, 256), Arguments.of("SM3", "SM3", null, null),
				Arguments.of("sm3", "SM3", null, null), Arguments.of("HMAC-SM3", "HMAC-SM3", null, null),
				Arguments.of("HmacSM3", "HMAC-SM3", null, null), Arguments.of("hmac_sm3", "HMAC-SM3", null, null),
				Arguments.of("ZUC", "ZUC", null, null), Arguments.of("zuc-128", "ZUC", null, 128),
				Arguments.of("祖冲之", "ZUC", null, null), Arguments.of("AES", "AES", null, null),
				Arguments.of("AES-256-GCM", "AES", "GCM", 256), Arguments.of("aes128", "AES", null, 128),
				Arguments.of("AES_128_CBC", "AES", "CBC", 128), Arguments.of("AES/CBC/PKCS7Padding", "AES", "CBC", null),
				Arguments.of("Rijndael", "AES", null, null), Arguments.of("3DES", "3DES", null, null),
				Arguments.of("DESede", "3DES", null, null), Arguments.of("TDEA-CBC", "3DES", "CBC", null),
				Arguments.of("DES", "DES", null, null), Arguments.of("des-ecb", "DES", "ECB", null),
				Arguments.of("RSA", "RSA", null, null), Arguments.of("RSA2048", "RSA", null, 2048),
				Arguments.of("RSA-1024", "RSA", null, 1024), Arguments.of("rsa_4096", "RSA", null, 4096),
				Arguments.of("RSA/ECB/OAEP", "RSA", "ECB", null), Arguments.of("SHA256", "SHA-256", null, null),
				Arguments.of("SHA-256", "SHA-256", null, null), Arguments.of("sha2", "SHA-256", null, null),
				Arguments.of("SHA-1", "SHA-1", null, null), Arguments.of("sha1", "SHA-1", null, null),
				Arguments.of("MD5", "MD5", null, null), Arguments.of("HMAC-SHA256", "HMAC-SHA256", null, null),
				Arguments.of("HmacSHA256", "HMAC-SHA256", null, null), Arguments.of("ECDSA", "ECDSA", null, null),
				Arguments.of("RC4", "RC4", null, null), Arguments.of("ARCFOUR", "RC4", null, null));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("spellings")
	void normalizesSpellings(String raw, String name, String mode, Integer keyBits) {
		AlgorithmNormalizer.Normalized n = TABLE.normalizer().normalize(raw);

		assertThat(n.name()).isEqualTo(name);
		assertThat(n.mode()).isEqualTo(mode);
		assertThat(n.keyBits()).isEqualTo(keyBits);
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "SM44", "SM4XYZ", "AESX", "SHA", "自研算法", "", "   ",
			"SM4-GCM-FOO", "BLOWFISH", "RSA-CBC-2048x" })
	void unrecognizedIsUnknownNotGuessed(String raw) {
		assertThat(TABLE.normalizer().normalize(raw).name()).isEqualTo(AlgorithmNormalizer.UNKNOWN);
		assertThat(TABLE.check(raw).status()).isEqualTo(AlgorithmStatus.UNKNOWN);
	}

	@Test
	void checkReturnsCategoryStatusBasisAndVersionFromYaml() {
		RuleCheck check = TABLE.check("SM2");

		assertThat(check.name()).isEqualTo("SM2");
		assertThat(check.category()).isEqualTo("public_key");
		assertThat(check.status()).isEqualTo(AlgorithmStatus.APPROVED);
		assertThat(check.basis()).isEqualTo("测试依据 A");
		assertThat(check.clauseRefs()).containsExactly("FIX/T 0001-2026#4.1");
		assertThat(check.ruleVersion()).isEqualTo("algorithms.test-1");
		assertThat(TABLE.check("MD5").status()).isEqualTo(AlgorithmStatus.INSECURE);
		assertThat(TABLE.check("AES-256-GCM").status()).isEqualTo(AlgorithmStatus.NOT_APPROVED);
		assertThat(TABLE.check("AES-256-GCM").input()).isEqualTo("AES-256-GCM");
	}

	@Test
	void securityBitsFollowKeySizeWhenListedOtherwiseTheConservativeDefault() {
		assertThat(TABLE.check("RSA2048").securityBits()).isEqualTo(112);
		assertThat(TABLE.check("RSA-1024").securityBits()).isEqualTo(80);
		assertThat(TABLE.check("RSA").securityBits()).isEqualTo(80);
		assertThat(TABLE.check("RSA-4096").securityBits()).isEqualTo(80);
		assertThat(TABLE.check("AES-256-GCM").securityBits()).isEqualTo(256);
		assertThat(TABLE.check("AES").securityBits()).isEqualTo(128);
		assertThat(TABLE.check("MD5").securityBits()).isZero();
		assertThat(TABLE.check("SM4").securityBits()).isNull();
		assertThat(TABLE.check("自研算法").securityBits()).isNull();
	}

	@Test
	void extractsAlgorithmsFromFreeText() {
		List<RuleCheck> found = TABLE.extract("传输层用国密 TLS（ECC-SM4-SM3 套件），数据库字段用 AES-256-GCM 加密，"
				+ "签名用 SM3withSM2，口令用 MD5 散列，另有自研算法 X。");

		assertThat(found).extracting(RuleCheck::name).containsExactly("SM4", "SM3", "AES", "SM2", "MD5");
	}

	@Test
	void extractionFindsChineseAliases() {
		assertThat(TABLE.extract("4G 链路采用祖冲之算法加密")).extracting(RuleCheck::name).containsExactly("ZUC");
	}

	static Stream<Arguments> invalidYaml() {
		return Stream.of(Arguments.of("缺 version", "algorithms: []", "version"),
				Arguments.of("缺 algorithms", "version: x", "algorithms"),
				Arguments.of("非法状态", """
						version: x
						algorithms:
						  - {name: A, category: hash, aliases: [A], status: MAYBE, basis: b}""", "status"),
				Arguments.of("非法类别", """
						version: x
						algorithms:
						  - {name: A, category: magic, aliases: [A], status: APPROVED, basis: b}""", "category"),
				Arguments.of("别名冲突", """
						version: x
						algorithms:
						  - {name: A, category: hash, aliases: [X], status: APPROVED, basis: b}
						  - {name: B, category: hash, aliases: [X], status: APPROVED, basis: b}""", "alias"),
				Arguments.of("缺依据", """
						version: x
						algorithms:
						  - {name: A, category: hash, aliases: [A], status: APPROVED}""", "basis"),
				Arguments.of("安全强度为负数", """
						version: x
						algorithms:
						  - {name: A, category: hash, aliases: [A], status: APPROVED, basis: b, security_bits: -1}""",
						"security_bits"),
				Arguments.of("不是 YAML", "version: [unclosed", "YAML"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidYaml")
	void invalidYamlFailsWithReason(String name, String yaml, String reason) {
		assertThatThrownBy(() -> AlgorithmRuleTable.load(new ByteArrayResource(yaml.getBytes(), "bad.yaml")))
			.isInstanceOf(RuleTableException.class)
			.hasMessageContaining(reason);
	}

	@Test
	void productionRuleTablesLoad() {
		AlgorithmRuleTable v1 = AlgorithmRuleTable.load(new ClassPathResource("rules/algorithms.v1.yaml"));
		AlgorithmRuleTable v2 = AlgorithmRuleTable.load(new ClassPathResource("rules/algorithms.v2.yaml"));

		assertThat(v1.ruleVersion()).isEqualTo("algorithms.v1");
		assertThat(v2.ruleVersion()).isEqualTo("algorithms.v2");
		assertThat(v2.check("SM4").status()).isEqualTo(v1.check("SM4").status());
		// v2 只新增安全强度，合规结论与已核对的 v1 保持一致
		for (String name : java.util.List.of("SM1", "SM2", "SM3", "SM4", "SM7", "SM9", "ZUC", "HMAC-SM3", "AES", "3DES",
				"RSA", "ECDSA", "DSA", "ED25519", "ECDH", "DH", "SHA-256", "SHA-512", "SHA-3", "HMAC-SHA256",
				"CHACHA20", "DES", "RC4", "MD5", "SHA-1", "HMAC-MD5")) {
			assertThat(v2.check(name).status()).as(name).isEqualTo(v1.check(name).status());
			assertThat(v2.check(name).basis()).as(name).isEqualTo(v1.check(name).basis());
		}
		assertThat(v2.check("RSA2048").securityBits()).isEqualTo(112);
	}

}
