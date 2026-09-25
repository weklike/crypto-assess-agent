package com.cryptoassess.common.http;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 调用本机依赖（Ollama、TEI）的 RestClient：连接和读取都设超时，不做重试。
 */
public final class RestClients {

	private RestClients() {
	}

	public static RestClient create(String baseUrl, Duration timeout) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(timeout)
			.version(HttpClient.Version.HTTP_1_1)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

}
