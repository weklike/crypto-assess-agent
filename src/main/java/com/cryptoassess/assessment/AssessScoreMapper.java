package com.cryptoassess.assessment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssessScoreMapper {

	List<AssessScore> findByProject(@Param("projectId") long projectId);

	int deleteByProject(@Param("projectId") long projectId);

	int insert(@Param("s") AssessScore score);

}
