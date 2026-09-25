package com.cryptoassess.common.db;

/**
 * 接收 MyBatis 回填的自增主键。实体都是不可变的 record，主键通过这个单独的可变对象带回。
 */
public class GeneratedKey {

	private Long id;

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
