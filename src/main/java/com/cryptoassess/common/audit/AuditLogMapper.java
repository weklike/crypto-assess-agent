package com.cryptoassess.common.audit;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {

	int insert(@Param("actor") String actor, @Param("channel") String channel, @Param("action") String action,
			@Param("target") String target, @Param("argsSha256") String argsSha256, @Param("result") String result,
			@Param("createdAt") Instant createdAt);

}
