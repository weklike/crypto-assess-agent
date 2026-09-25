package com.cryptoassess.common.health;

/**
 * 一个外部依赖的连通性检查。实现只做轻量探测，不调用大模型。
 */
public interface DependencyCheck {

	String name();

	CheckResult check();

}
