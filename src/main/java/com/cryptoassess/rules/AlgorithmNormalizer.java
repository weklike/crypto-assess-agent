package com.cryptoassess.rules;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 “SM4-CBC”“sm4_gcm”“AES-256-GCM”“RSA2048”“AES/CBC/PKCS5Padding” 这类写法归一。
 * 做法：去掉分隔符并转大写 → 用规则表里的别名做最长前缀匹配 → 剩余部分只允许是密钥位数、工作模式、填充方式。
 * 剩余部分解析不了就返回 UNKNOWN，不猜。别名全部来自 YAML。
 */
public class AlgorithmNormalizer {

	public static final String UNKNOWN = "UNKNOWN";

	private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-_/]+");

	private static final Pattern REMAINDER = Pattern
		.compile("^(\\d{2,5})?(ECB|CBC|CTR|GCM|CCM|OFB|CFB|XTS)?((?:PKCS5|PKCS7|PKCS1|NO)PADDING|OAEP|PSS)?$");

	public record Normalized(String input, String name, String mode, Integer keyBits) {

		public boolean recognized() {
			return !UNKNOWN.equals(this.name);
		}

	}

	private record Alias(String compact, String name) {
	}

	private final List<Alias> aliases;

	/**
	 * @param aliasToName 别名 → 规范名
	 */
	public AlgorithmNormalizer(Map<String, String> aliasToName) {
		this.aliases = aliasToName.entrySet()
			.stream()
			.map(e -> new Alias(compact(e.getKey()), e.getValue()))
			.sorted(Comparator.comparingInt((Alias a) -> a.compact().length()).reversed())
			.toList();
	}

	public Normalized normalize(String raw) {
		String input = (raw == null) ? "" : raw.strip();
		String compact = compact(input);
		if (compact.isEmpty()) {
			return new Normalized(input, UNKNOWN, null, null);
		}
		for (Alias alias : this.aliases) {
			if (!compact.startsWith(alias.compact())) {
				continue;
			}
			Matcher rest = REMAINDER.matcher(compact.substring(alias.compact().length()));
			if (rest.matches()) {
				Integer bits = (rest.group(1) == null) ? null : Integer.valueOf(rest.group(1));
				return new Normalized(input, alias.name(), rest.group(2), bits);
			}
		}
		return new Normalized(input, UNKNOWN, null, null);
	}

	static String compact(String text) {
		return SEPARATORS.matcher(text).replaceAll("").toUpperCase(Locale.ROOT);
	}

}
