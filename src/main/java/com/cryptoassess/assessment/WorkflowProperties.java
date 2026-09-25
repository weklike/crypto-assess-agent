package com.cryptoassess.assessment;

import com.cryptoassess.retrieval.SearchMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param supplementK 每个测评对象用检索补充的相关条款数（作为判定时的参考上下文），0 表示不补充
 * @param supplementMode 补充检索的模式
 * @param resumeOnStartup 启动时是否自动继续被中断（停在 ANALYZING）的分析
 */
@Validated
@ConfigurationProperties("app.workflow")
public record WorkflowProperties(@PositiveOrZero int supplementK, @NotNull SearchMode supplementMode,
		boolean resumeOnStartup) {

}
