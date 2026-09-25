package com.cryptoassess.knowledge.format;

/**
 * 解析得到的一条条款。
 *
 * @param clauseRef 标准号#条款号
 * @param parentNo 父条款编号，顶层为 null
 * @param path 祖先章节路径，如 “5 技术要求 &gt; 5.2 网络和通信”，顶层为空串
 * @param body 正文，不含标题和元数据注释；只有子条款的章节为空串
 * @param layer 安全层面，未标注时为 null
 * @param levels 适用等级，逗号分隔，未标注时为 null
 * @param clauseType 条款类型，未标注时为 null
 * @param ordinal 文档内顺序，从 1 开始
 */
public record ClauseRecord(String clauseRef, String clauseNo, String parentNo, String path, String title, String body,
		String layer, String levels, String clauseType, int ordinal, String bodySha256) {

}
