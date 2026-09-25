package com.cryptoassess.qa;

import com.cryptoassess.common.db.GeneratedKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QaRecordMapper {

	int insert(@Param("record") QaRecord record, @Param("key") GeneratedKey key);

}
