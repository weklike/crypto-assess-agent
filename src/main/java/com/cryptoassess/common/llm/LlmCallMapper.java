package com.cryptoassess.common.llm;

import com.cryptoassess.common.db.GeneratedKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LlmCallMapper {

	int insert(@Param("call") LlmCall call, @Param("key") GeneratedKey key);

	/** 当前最大 id，评测用它圈定一次运行产生的调用。 */
	long maxId();

	/** 指定调用的汇总（calls、inputTokens、outputTokens、latencyMs、costCny）。 */
	java.util.Map<String, Object> summarizeIds(@Param("ids") java.util.List<Long> ids);

	/** 给定调用里出现过的模型和提示词版本（报告附录用）。 */
	java.util.List<java.util.Map<String, Object>> modelsAndPrompts(@Param("ids") java.util.List<Long> ids);

	/** id 大于 afterId 的调用汇总：calls、inputTokens、outputTokens、latencyMs（可按用途过滤）。 */
	java.util.Map<String, Object> summarizeSince(@Param("afterId") long afterId, @Param("purpose") String purpose);

}
