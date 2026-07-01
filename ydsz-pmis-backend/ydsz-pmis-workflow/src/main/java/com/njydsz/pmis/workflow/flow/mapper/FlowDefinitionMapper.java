package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程定义 Mapper
 */
@Mapper
public interface FlowDefinitionMapper extends BaseMapper<FlowDefinitionDO> {

    /**
     * 根据 flowCode + version 查最新已发布版本
     */
    FlowDefinitionDO selectPublished(@Param("flowCode") String flowCode,
                                     @Param("version") String version,
                                     @Param("tenantId") Long tenantId);

    /**
     * 根据 flowCode 查最新版本（不区分发布状态）
     */
    FlowDefinitionDO selectLatestByCode(@Param("flowCode") String flowCode,
                                        @Param("tenantId") Long tenantId);

    /**
     * 发布（更新 is_publish）
     */
    int publish(@Param("id") Long id, @Param("isPublish") Integer isPublish);
}
