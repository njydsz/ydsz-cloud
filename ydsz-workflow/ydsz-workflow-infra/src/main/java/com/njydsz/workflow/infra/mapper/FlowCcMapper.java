package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.infra.entity.FlowCc;

/**
 * 流程抄送 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_cc</code>（P0-3），存储流程抄送关系。
 *
 * <p>抄送中心（「抄送我的」独立 Tab），被抄送人只读可见，不参与审批。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_cc_user — 被抄送人维度查询索引（抄送我的）
 *   <li>idx_instance_id — 流程实例维度查询索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.infra.entity.FlowCc 抄送实体
 * @see com.njydsz.workflow.server.service.FlowCcService 抄送 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowCcMapper extends BaseMapper<FlowCc> {

  /**
   * 查"抄送我的"（分页）
   *
   *
   * @param tenantId 租户 ID
   * @param ccUserId 被抄送人用户 ID
   * @param readStatus 阅读状态过滤（READ/UNREAD，可空）
   * @param flowCode 流程编码过滤（可空）
   * @param offset 分页偏移量
   * @param limit 每页大小
   * @return 抄送列表
   */
  List<FlowCc> selectCcByUserPage(
      @Param("tenantId") String tenantId,
      @Param("ccUserId") String ccUserId,
      @Param("readStatus") String readStatus,
      @Param("flowCode") String flowCode,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 统计"抄送我的"总数
   *
   * @param tenantId 租户 ID
   * @param ccUserId 被抄送人用户 ID
   * @param readStatus 阅读状态过滤（可空）
   * @param flowCode 流程编码过滤（可空）
   * @return 符合条件的抄送总数
   */
  long countCcByUser(
      @Param("tenantId") String tenantId,
      @Param("ccUserId") String ccUserId,
      @Param("readStatus") String readStatus,
      @Param("flowCode") String flowCode);

  /**
   * 统计"抄送我的"未读数
   *
   * @param tenantId 租户 ID
   * @param ccUserId 被抄送人用户 ID
   * @return 未读抄送数
   */
  long countCcUnreadByUser(@Param("tenantId") String tenantId, @Param("ccUserId") String ccUserId);

  /**
   * P2-3: 统计全局未读抄送数（Prometheus Gauge 监控指标）
   *
   * <p>无 tenant/ccUser 过滤，统计 ydsz_flow_cc 表所有未读记录数。
   *
   * @return 全局未读抄送数
   */
  long countUnread();

  /**
   * 标记抄送为已读
   *
   * @param id 抄送记录 ID
   * @param ccUserId 已读人用户 ID
   * @param readAt 阅读时间
   * @return 受影响行数
   */
  int markRead(
      @Param("id") String id,
      @Param("ccUserId") String ccUserId,
      @Param("readAt") LocalDateTime readAt);

  /**
   * 全部标记为已读
   *
   * @param tenantId 租户 ID
   * @param ccUserId 用户 ID
   * @param readAt 已读时间
   * @return 受影响行数
   */
  int markAllRead(
      @Param("tenantId") String tenantId,
      @Param("ccUserId") String ccUserId,
      @Param("readAt") LocalDateTime readAt);

  /**
   * 查实例的抄送列表
   *
   * @param tenantId 租户 ID
   * @param instanceId 流程实例 ID
   * @return 抄送列表
   */
  List<FlowCc> selectByInstanceId(
      @Param("tenantId") String tenantId, @Param("instanceId") String instanceId);
}
