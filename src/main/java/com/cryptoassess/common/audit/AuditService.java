package com.cryptoassess.common.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志：谁、从哪个通道、做了什么、结果如何。参数只存 SHA-256，不存原文。
 */
@Service
public class AuditService {

	public static final String OK = "OK";

	public static final String ERROR = "ERROR";

	public static final String DENIED = "DENIED";

	private final AuditLogMapper mapper;

	public AuditService(AuditLogMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String actor, String channel, String action, String target, String argsSha256, String result) {
		this.mapper.insert(truncate(actor, 64), channel, truncate(action, 64), truncate(target, 255), argsSha256,
				result, Instant.now());
	}

	public static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static String sha256(String text) {
		return sha256(text.getBytes(StandardCharsets.UTF_8));
	}

	private static String truncate(String value, int max) {
		return (value == null || value.length() <= max) ? value : value.substring(0, max);
	}

}
