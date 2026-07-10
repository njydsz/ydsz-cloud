package com.njydsz.pmis.workflow.mapper.integration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.integration.FlowWebhookSubscriptionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * P1-6: 工作流 Webhook 事件订阅 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Mapper
public interface FlowWebhookSubscriptionMapper extends BaseMapper<FlowWebhookSubscriptionDO> {

    /**
     * 查启用的订阅（按租户 + 事件类型匹配）。
     *
     * <p>匹配规则：eventTypes 为空表示订阅全部事件，否则要求 eventTypes 包含给定 eventType。
     *
     * @param tenantId  租户 ID
     * @param eventType 事件类型
     * @return 匹配的订阅列表
     */
    @Select("SELECT * FROM pmis_flow_webhook_subscription " +
            "WHERE tenant_id = #{tenantId} AND enabled = 1 AND deleted = 0 " +
            "AND (event_types IS NULL OR event_types = '' " +
            "     OR ',' || event_types || ',' LIKE '%,' || #{eventType} || ',%')")
    List<FlowWebhookSubscriptionDO> selectEnabledByEvent(@Param("tenantId") String tenantId,
                                                          @Param("eventType") String eventType);

    /**
     * 查全部启用的订阅（管理后台用）
     */
    @Select("SELECT * FROM pmis_flow_webhook_subscription " +
            "WHERE deleted = 0 ORDER BY created_at DESC")
    List<FlowWebhookSubscriptionDO> selectAll();
}
