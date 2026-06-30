package com.njydsz.pmis.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.audit.entity.DataExportAuditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataExportAuditMapper extends BaseMapper<DataExportAuditDO> {

    int insertExport(DataExportAuditDO e);

    java.util.List<DataExportAuditDO> selectByUser(@Param("userId") Long userId,
                                                   @Param("limit") int limit);
}
