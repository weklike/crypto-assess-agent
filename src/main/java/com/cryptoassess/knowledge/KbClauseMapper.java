package com.cryptoassess.knowledge;

import java.util.List;

import com.cryptoassess.knowledge.format.ClauseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbClauseMapper {

	int insertBatch(@Param("documentId") long documentId, @Param("clauses") List<ClauseRecord> clauses);

	int deleteByDocCode(@Param("docCode") String docCode);

	KbClause findByRef(@Param("clauseRef") String clauseRef);

	List<KbClause> findByRefs(@Param("clauseRefs") List<String> clauseRefs);

	/** 全部 ACTIVE 文档的条款，按文档和顺序排列（索引重建用） */
	List<KbClause> findActive();

	int countActive();

	/** ACTIVE 文档的条款分页，可按层面、等级过滤；layer、level 为 null 时不过滤。 */
	List<KbClause> findPage(@Param("layer") String layer, @Param("level") Integer level, @Param("offset") int offset,
			@Param("limit") int limit);

	int countPage(@Param("layer") String layer, @Param("level") Integer level);

	/** 某安全层面、某等级适用的“要求”类条款（测评指标），按文档和顺序排列。 */
	List<KbClause> findRequirements(@Param("layer") String layer, @Param("level") int level);

}
