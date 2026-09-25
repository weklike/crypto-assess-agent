package com.cryptoassess.eval;

/**
 * eval profile 下的辅助工具（标注、数据集校验），返回进程退出码。
 */
public interface EvalTool {

	String name();

	int run() throws Exception;

}
