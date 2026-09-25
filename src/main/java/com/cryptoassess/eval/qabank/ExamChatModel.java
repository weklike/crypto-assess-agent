package com.cryptoassess.eval.qabank;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 题库问答评测使用的模型。题库及其派生数据只能发往本地模型，不走 spring.ai.openai 配置的外部接口。
 */
public interface ExamChatModel {

	ChatModel chatModel();

	String modelName();

	/** 模型服务地址，写进评测报告以便核对数据没有离开本机。 */
	String endpoint();

}
