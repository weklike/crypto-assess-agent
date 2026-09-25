package com.cryptoassess.qa;

import java.io.IOException;

/**
 * SSE 事件出口。QaService 不依赖 SseEmitter，便于测试和复用。
 */
@FunctionalInterface
public interface QaEventSink {

	void send(String event, Object data) throws IOException;

}
