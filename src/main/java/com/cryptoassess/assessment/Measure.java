package com.cryptoassess.assessment;

import jakarta.validation.constraints.Size;

/**
 * 一项密码措施。四个字段至少填一个。
 *
 * @param algorithm 算法，如 SM4-GCM、RSA2048
 * @param protocol 协议，如 TLS 1.3、IPSec
 * @param product 产品，如 服务器密码机、国密 SSL 网关
 * @param evidence 证据说明，如 配置截图、检测证书编号
 */
public record Measure(@Size(max = 64) String algorithm, @Size(max = 128) String protocol,
		@Size(max = 128) String product, @Size(max = 1000) String evidence) {

	boolean isBlank() {
		return blank(this.algorithm) && blank(this.protocol) && blank(this.product) && blank(this.evidence);
	}

	Measure normalized() {
		return new Measure(trim(this.algorithm), trim(this.protocol), trim(this.product), trim(this.evidence));
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String trim(String value) {
		return blank(value) ? null : value.strip();
	}

}
