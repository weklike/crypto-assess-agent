package com.cryptoassess.assessment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssessStepMapper {

	List<AssessStep> findByProject(@Param("projectId") long projectId);

	AssessStep find(@Param("projectId") long projectId, @Param("objectId") long objectId, @Param("step") String step,
			@Param("stepKey") String stepKey);

	/** 开始一步：不存在则插入 RUNNING，存在则改回 RUNNING 并累加 attempts。 */
	int start(@Param("projectId") long projectId, @Param("objectId") long objectId, @Param("step") String step,
			@Param("stepKey") String stepKey);

	int succeed(@Param("projectId") long projectId, @Param("objectId") long objectId, @Param("step") String step,
			@Param("stepKey") String stepKey, @Param("outputJson") String outputJson);

	int fail(@Param("projectId") long projectId, @Param("objectId") long objectId, @Param("step") String step,
			@Param("stepKey") String stepKey, @Param("error") String error);

	int deleteByObject(@Param("objectId") long objectId);

	/** 进程重启后，把遗留的 RUNNING 步骤标记为失败（它们的结果没有落库）。 */
	int failRunning(@Param("projectId") long projectId, @Param("error") String error);

}
