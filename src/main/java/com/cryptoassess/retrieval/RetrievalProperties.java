package com.cryptoassess.retrieval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param indexAlias 检索读取的索引别名
 * @param candidates BM25 与向量各自召回的条数
 * @param numCandidates kNN 的 num_candidates
 * @param rrfK RRF 常数 k
 * @param refuseThreshold 问答拒答阈值（最高重排分低于它就拒答），待 T09 用评测数据校准
 */
@Validated
@ConfigurationProperties("app.retrieval")
public record RetrievalProperties(@NotBlank String indexAlias, @Positive int candidates, @Positive int numCandidates,
		@Positive int rrfK, double refuseThreshold) {

}
