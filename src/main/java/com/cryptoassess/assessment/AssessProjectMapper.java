package com.cryptoassess.assessment;

import java.util.List;

import com.cryptoassess.common.db.GeneratedKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssessProjectMapper {

	int insert(@Param("p") AssessProject project, @Param("key") GeneratedKey key);

	AssessProject findById(@Param("id") long id);

	List<AssessProject> findAll();

	/**
	 * 乐观锁更新状态：只有当前 status 与 version 都符合预期时才更新，返回受影响行数（0 表示被并发修改）。
	 */
	int transition(@Param("id") long id, @Param("from") AssessmentStatus from, @Param("to") AssessmentStatus to,
			@Param("version") int version, @Param("lastError") String lastError);

}
