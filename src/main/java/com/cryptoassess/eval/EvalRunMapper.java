package com.cryptoassess.eval;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvalRunMapper {

	int insert(@Param("suite") String suite, @Param("datasetVersion") String datasetVersion,
			@Param("status") String status, @Param("configJson") String configJson,
			@Param("metricsJson") String metricsJson, @Param("outputDir") String outputDir,
			@Param("createdAt") Instant createdAt);

}
