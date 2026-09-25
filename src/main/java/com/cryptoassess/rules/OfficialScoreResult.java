package com.cryptoassess.rules;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 按量化评估规则计算的结果。
 *
 * @param total 总分（2 位）；不出总分时为 null，原因见 totalNote
 * @param groups 技术组、管理组的层面加权平均（4 位）
 * @param layers 安全层面得分（4 位）；全部不适用的层面为 null
 * @param units 测评单元得分，键为 层面|单元名
 * @param objects 测评对象的平均得分（仅供参考展示，不参与总分计算）
 * @param detail 逐步计算明细
 */
public record OfficialScoreResult(BigDecimal total, String totalNote, Map<String, BigDecimal> groups,
		Map<String, BigDecimal> layers, Map<String, BigDecimal> units, Map<String, BigDecimal> objects,
		List<String> detail, String ruleVersion, int level) {

}
