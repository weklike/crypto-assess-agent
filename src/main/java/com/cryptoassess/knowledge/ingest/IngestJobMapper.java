package com.cryptoassess.knowledge.ingest;

import com.cryptoassess.common.db.GeneratedKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IngestJobMapper {

	int insert(@Param("fileName") String fileName, @Param("sha") String sha, @Param("parserVersion") String parserVersion,
			@Param("key") GeneratedKey key);

	IngestJob findById(@Param("id") long id);

	IngestJob findByContent(@Param("sha") String sha, @Param("parserVersion") String parserVersion);

	/** 失败的任务重新排队（FAILED → PENDING），返回受影响行数。 */
	int requeue(@Param("id") long id, @Param("fileName") String fileName);

	/** 开始一次尝试：未成功的任务置为 RUNNING 并累加 attempts；已成功返回 0。 */
	int claim(@Param("id") long id);

	int succeed(@Param("id") long id, @Param("documentId") long documentId);

	/** 一次尝试失败：记录错误，terminal=true 时置为 FAILED，否则回到 PENDING 等待重试。 */
	int fail(@Param("id") long id, @Param("error") String error, @Param("terminal") boolean terminal);

}
