package com.cryptoassess.assessment;

import java.util.List;

import com.cryptoassess.common.db.GeneratedKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssessObjectMapper {

	int insert(@Param("o") AssessObject object, @Param("key") GeneratedKey key);

	List<AssessObject> findByProject(@Param("projectId") long projectId);

	AssessObject findById(@Param("id") long id);

}
