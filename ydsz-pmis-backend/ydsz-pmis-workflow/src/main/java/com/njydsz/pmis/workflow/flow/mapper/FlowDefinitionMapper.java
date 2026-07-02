package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程定义 Mapper
 *
 * <p>对应 pmis_flow_definition 表，提供按 flowCode/version 查询及发布状态维护。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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

    /**
     * P2-27: 失效同 flowCode 的其他已发布版本（is_publish 置 9）
     *
     * @param flowCode 流程编码
     * @param exceptId 排除的 definitionId（目标版本）
     * @param tenantId 租户 ID
     * @return 受影响行数
     */
    int deactivateByFlowCode(@Param("flowCode") String flowCode,
                             @Param("exceptId") Long exceptId,
                             @Param("tenantId") Long tenantId);

    /**
     * P2-28: 更新流程定义激活状态（0 挂起 / 1 激活）
     *
     * @param id             流程定义 ID
     * @param activityStatus 激活状态
     * @return 受影响行数
     */
    int updateActivityStatus(@Param("id") Long id,
                             @Param("activityStatus") Integer activityStatus);
}
