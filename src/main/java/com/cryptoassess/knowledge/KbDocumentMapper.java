package com.cryptoassess.knowledge;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbDocumentMapper {

	KbDocument findByCodeAndSha(@Param("docCode") String docCode, @Param("normalizedSha256") String normalizedSha256);

	List<KbDocument> findAll();

	int insert(KbDocument document);

	int supersedeActive(@Param("docCode") String docCode);

}
