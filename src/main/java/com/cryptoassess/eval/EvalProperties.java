package com.cryptoassess.eval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 评测运行参数，命令行传入，如 --eval.suite=retrieval --eval.dataset=v1。
 *
 * @param suite 要运行的评测：retrieval / qa / gap / scoring / summary
 * @param tool 要运行的工具：annotate / validate（与 suite 二选一）
 * @param dataset 数据集版本
 * @param datasetPath 直接指定数据集文件，覆盖按版本推导的路径（如用 fixtures 演示）
 * @param resultsDir 结果根目录，每次运行在其下新建 &lt;UTC 时间戳&gt;-&lt;suite&gt;/
 * @param datasetsDir 数据集根目录
 */
@ConfigurationProperties("eval")
public record EvalProperties(String suite, String tool, @DefaultValue("v1") String dataset, String datasetPath,
		@DefaultValue("eval/results") String resultsDir, @DefaultValue("eval/datasets") String datasetsDir) {

}
