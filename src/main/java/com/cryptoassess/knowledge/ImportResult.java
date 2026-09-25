package com.cryptoassess.knowledge;

/**
 * @param created true 表示本次新写入；false 表示相同内容已导入过，直接返回
 * @param normalizedSha256 本次导入内容的哈希（幂等键）
 */
public record ImportResult(long documentId, String docCode, int clauseCount, boolean created, String normalizedSha256) {

}
