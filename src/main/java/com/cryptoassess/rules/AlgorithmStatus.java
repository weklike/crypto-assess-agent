package com.cryptoassess.rules;

/**
 * 算法合规状态。取值来自规则表 YAML，代码不对任何具体算法做判断。
 */
public enum AlgorithmStatus {

	/** 认可的商用密码算法 */
	APPROVED,
	/** 非认可算法：在密评中其保护通常不被认可 */
	NOT_APPROVED,
	/** 已知不安全 */
	INSECURE,
	/** 规则表中没有，不做判断，交给后续步骤 */
	UNKNOWN;

	/** 规则层面是否判为不合规：判为不合规的项，模型不能改判为“符合”。 */
	public boolean violates() {
		return this == NOT_APPROVED || this == INSECURE;
	}

}
