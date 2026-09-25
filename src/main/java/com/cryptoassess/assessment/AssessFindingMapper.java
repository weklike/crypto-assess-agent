package com.cryptoassess.assessment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssessFindingMapper {

	List<AssessFinding> findByProject(@Param("projectId") long projectId);

	AssessFinding findById(@Param("id") long id);

	/** 按 (object_id, clause_ref) 幂等写入：已存在则覆盖判定内容并清除人工确认标记。 */
	int upsert(@Param("f") AssessFinding finding);

	int review(@Param("id") long id, @Param("judgment") String judgment, @Param("note") String note,
			@Param("reviewed") boolean reviewed, @Param("source") String source, @Param("d") Boolean d,
			@Param("a") Boolean a, @Param("k") Boolean k, @Param("ra") java.math.BigDecimal ra,
			@Param("rk") java.math.BigDecimal rk);

	int deleteByObject(@Param("objectId") long objectId);

	int countUnreviewed(@Param("projectId") long projectId);

}
