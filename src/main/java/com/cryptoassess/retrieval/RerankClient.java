package com.cryptoassess.retrieval;

import java.util.List;

/**
 * 交叉编码器重排。返回与 texts 一一对应的分数（越大越相关）。服务不可用时抛出 503 类异常，不降级。
 */
public interface RerankClient {

	double[] rerank(String query, List<String> texts);

}
