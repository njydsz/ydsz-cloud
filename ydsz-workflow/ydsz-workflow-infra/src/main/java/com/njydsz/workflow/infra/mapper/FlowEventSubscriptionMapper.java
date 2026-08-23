package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.infra.entity.FlowEventSubscriptionDO;

/**
 * 工作流事件订阅 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_event_subscription</code>，存储流程事件的外部订阅。
 *
 * <p>事件订阅支持「流程开始/结束/节点完成」等事件推送到 IM/OA/三方系统（基于 Spring Event / Redis Stream）。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_subscription_id — 订阅 ID 唯一索引
 *   <li>idx_event_type — 事件类型过滤索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.infra.entity.FlowEventSubscriptionDO 事件订阅实体
 * @see com.njydsz.workflow.server.service.FlowEventService 事件 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowEventSubscriptionMapper extends BaseMapper<FlowEventSubscriptionDO> {

  /**
   * 按事件类型 + 引用匹配 WAITING 订阅
   *
   * @param tenantId 租户 ID
   * @param eventType 事件类型 MESSAGE / ERROR / SIGNAL
   * @param eventRef 事件引用标识
   * @return 匹配的订阅列表
   */
  List<FlowEventSubscriptionDO> selectWaitingByEvent(
      @Param("tenantId") String tenantId,
      @Param("eventType") String eventType,
      @Param("eventRef") String eventRef);

  /**
   * 按关联键匹配 WAITING 消息订阅
   *
   * @param tenantId 参数说明
   * @param correlationKey 参数说明
   * @return 返回值说明
   */
  List<FlowEventSubscriptionDO> selectWaitingByCorrelation(
      @Param("tenantId") String tenantId, @Param("correlationKey") String correlationKey);

  /**
   * 标记订阅已触发
   *
   * @param id 参数说明
   * @param payload 参数说明
   * @param triggerSource 参数说明
   * @param triggeredAt 参数说明
   * @return 返回值说明
   */
  int markTriggered(
      @Param("id") String id,
      @Param("payload") String payload,
      @Param("triggerSource") String triggerSource,
      @Param("triggeredAt") LocalDateTime triggeredAt);

  /**
   * 取消某 userTask 关联的所有边界事件订阅
   *
   * @param boundaryTaskId 参数说明
   * @param reason 参数说明
   * @return 返回值说明
   */
  int cancelByTask(@Param("boundaryTaskId") String boundaryTaskId, @Param("reason") String reason);

  /**
   * 取消某实例所有 WAITING 订阅（实例终止/驳回时使用）
   *
   * @param instanceId 参数说明
   * @param reason 参数说明
   * @return 返回值说明
   */
  int cancelByInstance(@Param("instanceId") String instanceId, @Param("reason") String reason);

  /**
   * 查询实例的 WAITING 订阅数（检查流程是否被事件阻塞）
   *
   * @param instanceId 参数说明
   * @return 返回值说明
   */
  long countWaitingByInstance(@Param("instanceId") String instanceId);
}
