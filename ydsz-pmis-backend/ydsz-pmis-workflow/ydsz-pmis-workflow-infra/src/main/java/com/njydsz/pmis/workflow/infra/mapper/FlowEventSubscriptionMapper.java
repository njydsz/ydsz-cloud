package com.njydsz.pmis.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowEventSubscriptionDO;

/**
 * 工作流事件订阅 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface FlowEventSubscriptionMapper extends BaseMapper<FlowEventSubscriptionDO> {

    /**
     * 按事件类型 + 引用匹配 WAITING 订阅
     *
     * @param tenantId  租户 ID
     * @param eventType 事件类型 MESSAGE / ERROR / SIGNAL
     * @param eventRef  事件引用标识
     * @return 匹配的订阅列表
     */
    List<FlowEventSubscriptionDO> selectWaitingByEvent(@Param("tenantId") String tenantId,
                                                        @Param("eventType") String eventType,
                                                        @Param("eventRef") String eventRef);

    /**
     * 按关联键匹配 WAITING 消息订阅
     */
    List<FlowEventSubscriptionDO> selectWaitingByCorrelation(@Param("tenantId") String tenantId,
                                                              @Param("correlationKey") String correlationKey);

    /**
     * 标记订阅已触发
     */
    int markTriggered(@Param("id") String id,
                      @Param("payload") String payload,
                      @Param("triggerSource") String triggerSource,
                      @Param("triggeredAt") LocalDateTime triggeredAt);

    /**
     * 取消某 userTask 关联的所有边界事件订阅
     */
    int cancelByTask(@Param("boundaryTaskId") String boundaryTaskId,
                     @Param("reason") String reason);

    /**
     * 取消某实例所有 WAITING 订阅（实例终止/驳回时使用）
     */
    int cancelByInstance(@Param("instanceId") String instanceId,
                         @Param("reason") String reason);

    /**
     * 查询实例的 WAITING 订阅数（检查流程是否被事件阻塞）
     */
    long countWaitingByInstance(@Param("instanceId") String instanceId);
}
