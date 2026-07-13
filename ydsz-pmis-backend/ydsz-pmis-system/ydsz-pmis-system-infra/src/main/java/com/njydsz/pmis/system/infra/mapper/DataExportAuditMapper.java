package com.njydsz.pmis.system.infra.mapper.audit;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.system.domain.entity.audit.DataExportAuditDO;

/**
 * 数据导出审计 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DataExportAuditMapper extends BaseMapper<DataExportAuditDO> {

    /**
     * 插入数据导出审计记录
     *
     * @param e 数据导出审计实体
     * @return 影响行数
     */
    int insertExport(DataExportAuditDO e);

    /**
     * 按用户查询导出历史
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 导出审计列表
     */
    List<DataExportAuditDO> selectByUser(@Param("userId") String userId,
                                                   @Param("limit") int limit);
}
