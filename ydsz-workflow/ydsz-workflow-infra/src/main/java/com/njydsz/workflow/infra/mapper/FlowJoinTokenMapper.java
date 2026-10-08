package com.njydsz.workflow.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.infra.entity.FlowJoinTokenEntity;

/**
 * 并行网关 join token Mapper
 *
 * <p>对应数据表 {@code ydsz_flow_join_token}，提供原子递增和条件更新操作，
 * 用于持久化并行网关的分支到达状态。
 *
 * <p><b>并发安全：</b>所有写操作通过 SQL 原子语句（UPDATE ... SET arrived_count = arrived_count + 1）
 * 保证计数准确性，不依赖应用层读-改-写。
 *
 * <p><b>索引：</b>
 *
 * <ul>
 *   <li>{@code idx_instance_node} — (instance_id, join_node_code, is_deleted)：精确查找</li>
 *   <li>{@code idx_status} — (join_status, is_deleted)：状态筛选</li>
 *   <li>{@code idx_tenant_deleted} — (tenant_id, is_deleted)：租户过滤</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.08
 */
@Mapper
public interface FlowJoinTokenMapper extends BaseMapper<FlowJoinTokenEntity> {

  /**
   * 原子递增到达计数并返回更新后的值。
   *
   * <p>使用 SQL {@code arrived_count = arrived_count + 1} 原子操作，返回受影响行数。
   *
   * @param id join token 主键 ID
   * @return 受影响行数（0=记录不存在或已删除，1=递增成功）
   */
  int incrementArrivedCount(@Param("id") String id);

  /**
   * 条件递增到达计数：仅当 join_status='PENDING' 时执行。
   *
   * <p>防止 CANCELLED/COMPLETED 状态的 token 被重复递增。
   *
   * @param id join token 主键 ID
   * @return 受影响行数（0=非 PENDING 状态或记录不存在，1=递增成功）
   */
  int incrementArrivedCountIfPending(@Param("id") String id);

  /**
   * 按实例 ID 和 join 节点编码精确查找有效的 join token。
   *
   * <p>仅返回未删除的记录。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return join token 实体；不存在返回 null
   */
  FlowJoinTokenEntity selectByInstanceAndNode(
      @Param("instanceId") String instanceId,
      @Param("joinNodeCode") String joinNodeCode);

  /**
   * 按实例 ID 批量查询所有有效的 join token。
   *
   * <p>用于实例终止时批量清理。
   *
   * @param instanceId 流程实例 ID
   * @return join token 实体列表
   */
  List<FlowJoinTokenEntity> selectByInstanceId(@Param("instanceId") String instanceId);

  /**
   * 标记 join token 状态（原子 CAS 更新）。
   *
   * <p>仅当当前状态等于 expectedStatus 时更新为目标状态，防止并发覆盖。
   *
   * @param id join token 主键 ID
   * @param expectedStatus 当前预期状态
   * @param targetStatus 目标状态
   * @return 受影响行数（0=状态已变更或记录不存在，1=更新成功）
   */
  int updateStatus(
      @Param("id") String id,
      @Param("expectedStatus") String expectedStatus,
      @Param("targetStatus") String targetStatus);

  /**
   * 按实例 ID 批量取消所有 PENDING 状态的 join token。
   *
   * @param instanceId 流程实例 ID
   * @return 受影响行数
   */
  int cancelPendingByInstanceId(@Param("instanceId") String instanceId);

  /**
   * 清理已完成/已取消的 join token（逻辑删除）。
   *
   * <p>用于定期清理任务：删除已达到终态超过 retentionDays 天的记录。
   *
   * @param statusList 目标状态列表
   * @param updatedAtThreshold 更新时间阈值（早于此时间的记录被删除）
   * @param limit 单次清理上限
   * @return 受影响行数
   */
  int deleteCompletedBefore(
      @Param("statusList") List<String> statusList,
      @Param("updatedAtThreshold") java.time.LocalDateTime updatedAtThreshold,
      @Param("limit") int limit);
}
