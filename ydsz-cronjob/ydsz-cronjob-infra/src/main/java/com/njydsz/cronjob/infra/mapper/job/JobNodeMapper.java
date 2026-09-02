package com.njydsz.cronjob.infra.mapper.job;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.infra.entity.job.JobNode;

/**
 * 任务执行节点 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_node</code>。
 *
 * <p>节点注册到中心用于 Leader 选举、任务分片、健康检查，是分布式调度的核心基础设施。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_node_id — 节点 ID 唯一索引
 *   <li>idx_status — 状态过滤索引（ONLINE/OFFLINE）
 *   <li>idx_heartbeat_at — 心跳时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.cronjob.domain.entity.job.JobNode 节点实体
 * @see com.njydsz.cronjob.server.service.JobNodeService 节点 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobNodeMapper extends BaseMapper<JobNode> {

  /**
   * P0-8: 将超时仍为 ONLINE 的节点标记为 OFFLINE（僵尸节点回收）。
   *
   * <p>节点心跳超时（默认 30s 无心跳）但 status 仍为 ONLINE， 说明节点未优雅下线（如 kill -9 / 宕机），需要由 Reaper 标记为 OFFLINE。
   *
   * @param cutoff 心跳截止时间（早于此时间的 ONLINE 节点视为僵尸）
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_node SET status = 'OFFLINE' "
          + "WHERE status = 'ONLINE' AND last_heartbeat < #{cutoff}")
  int markStaleOnlineAsOffline(@Param("cutoff") LocalDateTime cutoff);

  /**
   * P1-3: 查询即将被标记为 OFFLINE 的僵尸节点 ID 列表（故障转移用）。
   *
   * <p>在 {@link #markStaleOnlineAsOffline} 执行前调用， 获取所有心跳超时但仍为 ONLINE 的节点 ID，用于对这些节点上的 RUNNING
   * 任务执行故障转移。
   *
   * @param cutoff 心跳截止时间（早于此时间的 ONLINE 节点视为僵尸）
   * @return 僵尸节点 ID 列表（nodeId）
   */
  @Select(
      "SELECT node_id FROM ydsz_job_node "
          + "WHERE status = 'ONLINE' AND last_heartbeat < #{cutoff}")
  List<String> selectStaleOnlineNodeIds(@Param("cutoff") LocalDateTime cutoff);

  /**
   * P0-8: 物理删除已离线超过指定时长的节点记录。
   *
   * <p>清理 OFFLINE/DRAINING 状态且最后心跳超过 cutoff 的节点， 避免 ydsz_job_node 表无限膨胀。
   *
   * @param cutoff 心跳截止时间（早于此时间的离线节点将被删除）
   * @return 受影响行数
   */
  @Delete(
      "DELETE FROM ydsz_job_node "
          + "WHERE status IN ('OFFLINE', 'DRAINING') AND last_heartbeat < #{cutoff}")
  int deleteStaleOfflineNodes(@Param("cutoff") LocalDateTime cutoff);

  /**
   * 按 nodeId 更新节点记录（心跳/信息更新场景）。
   *
   * @param node 节点实体（node_id 为更新条件）
   * @return 受影响行数
   */
  @Update(
      "<script>UPDATE ydsz_job_node "
          + "<set>"
          + "<if test='appName != null'>app_name = #{appName},</if>"
          + "<if test='host != null'>host = #{host},</if>"
          + "<if test='port != null'>port = #{port},</if>"
          + "<if test='lastHeartbeat != null'>last_heartbeat = #{lastHeartbeat},</if>"
          + "<if test='runningCount != null'>running_count = #{runningCount},</if>"
          + "<if test='cpuUsage != null'>cpu_usage = #{cpuUsage},</if>"
          + "<if test='memUsagePct != null'>mem_usage_pct = #{memUsagePct},</if>"
          + "<if test='status != null'>status = #{status},</if>"
          + "</set>"
          + "WHERE node_id = #{nodeId} AND deleted = 0"
          + "</script>")
  int updateByNodeId(JobNode node);

  /**
   * 更新节点心跳与运行指标。
   *
   * @param nodeId 节点 ID
   * @param lastHeartbeat 心跳时间
   * @param runningCount 运行任务数
   * @param cpuUsage CPU 使用率
   * @param memUsagePct 内存使用率
   * @param status 状态
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_node "
          + "SET last_heartbeat = #{lastHeartbeat}, running_count = #{runningCount}, "
          + "    cpu_usage = #{cpuUsage}, mem_usage_pct = #{memUsagePct}, "
          + "    status = #{status} "
          + "WHERE node_id = #{nodeId} AND deleted = 0")
  int updateHeartbeat(
      @Param("nodeId") String nodeId,
      @Param("lastHeartbeat") LocalDateTime lastHeartbeat,
      @Param("runningCount") int runningCount,
            @Param("cpuUsage") BigDecimal cpuUsage,
            @Param("memUsagePct") BigDecimal memUsagePct,
      @Param("status") String status);

  /**
   * 更新节点状态。
   *
   * @param nodeId 节点 ID
   * @param status 目标状态
   * @param lastHeartbeat 心跳时间
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_node "
          + "SET status = #{status}, last_heartbeat = #{lastHeartbeat} "
          + "WHERE node_id = #{nodeId} AND deleted = 0")
  int updateStatus(
      @Param("nodeId") String nodeId,
      @Param("status") String status,
      @Param("lastHeartbeat") LocalDateTime lastHeartbeat);

  // ===== P1-1: 节点健康检查 =====

  /**
   * 更新节点加权响应时长（EMA）。
   *
   * @param nodeId 节点 ID
   * @param responseTimeMs 加权平均响应时长（毫秒）
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_node "
          + "SET response_time_ms = #{responseTimeMs}, updated_at = NOW() "
          + "WHERE node_id = #{nodeId} AND deleted = 0")
  int updateResponseTime(
      @Param("nodeId") String nodeId, @Param("responseTimeMs") long responseTimeMs);

  /**
   * 更新节点连续失败次数。
   *
   * @param nodeId 节点 ID
   * @param consecutiveFailures 连续失败次数
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_node "
          + "SET consecutive_failures = #{consecutiveFailures}, updated_at = NOW() "
          + "WHERE node_id = #{nodeId} AND deleted = 0")
  int updateConsecutiveFailures(
      @Param("nodeId") String nodeId, @Param("consecutiveFailures") int consecutiveFailures);
}
