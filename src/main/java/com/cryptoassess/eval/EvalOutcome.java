package com.cryptoassess.eval;

import java.util.Map;

/**
 * @param config 写进 eval_run.config_json 与报告头部的运行配置
 * @param metrics 汇总指标，写进 eval_run.metrics_json
 */
public record EvalOutcome(Map<String, Object> config, Map<String, Object> metrics) {

}
