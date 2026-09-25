package com.cryptoassess.common.llm;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * 模型调用异常分类。SDK 的 HTTP 超时会被包成 IO 异常（如 OpenAIIoException），
 * 需要沿着 cause 链识别，才能记为 TIMEOUT 而不是笼统的调用失败。
 */
public final class LlmErrors {

	private LlmErrors() {
	}

	public static boolean isTimeout(Throwable error) {
		for (Throwable t = error; t != null; t = (t.getCause() == t) ? null : t.getCause()) {
			if (t instanceof TimeoutException || t instanceof HttpTimeoutException
					|| t instanceof InterruptedIOException) {
				return true;
			}
		}
		return false;
	}

}
