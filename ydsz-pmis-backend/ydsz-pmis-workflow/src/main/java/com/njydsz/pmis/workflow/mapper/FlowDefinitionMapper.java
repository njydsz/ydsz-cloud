package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
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
                                     @Param("tenantId") String tenantId);

    /**
     * 根据 flowCode 查最新版本（不区分发布状态）
     */
    FlowDefinitionDO selectLatestByCode(@Param("flowCode") String flowCode,
                                        @Param("tenantId") String tenantId);

    /**
     * 发布（更新 is_publish）
     */
    int publish(@Param("id") String id, @Param("isPublish") Integer isPublish);

    /**
     * P2-27: 失效同 flowCode 的其他已发布版本（is_publish 置 9）
     *
     * @param flowCode 流程编码
     * @param exceptId 排除的 definitionId（目标版本）
     * @param tenantId 租户 ID
     * @return 受影响行数
     */
    int deactivateByFlowCode(@Param("flowCode") String flowCode,
                             @Param("exceptId") String exceptId,
                             @Param("tenantId") String tenantId);

    /**
     * P2-28: 更新流程定义激活状态（0 挂起 / 1 激活）
     *
     * @param id             流程定义 ID
     * @param activityStatus 激活状态
     * @return 受影响行数
     */
    int updateActivityStatus(@Param("id") String id,
                             @Param("activityStatus") Integer activityStatus);

    /**
     * P3-1: 查询同 flowCode + tenant 下处于灰度中（CANARYING）的所有定义，按 version 倒序
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID
     * @return 灰度中定义列表（按 version desc）
     */
    java.util.List<FlowDefinitionDO> selectCanaryingByCode(@Param("flowCode") String flowCode,
                                                           @Param("tenantId") String tenantId);

    /**
     * P3-1: 查询同 flowCode + tenant 下的所有定义（含历史版本），按 version 倒序
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID
     * @return 所有定义列表
     */
    java.util.List<FlowDefinitionDO> selectByFlowCode(@Param("flowCode") String flowCode,
                                                      @Param("tenantId") String tenantId);
}
