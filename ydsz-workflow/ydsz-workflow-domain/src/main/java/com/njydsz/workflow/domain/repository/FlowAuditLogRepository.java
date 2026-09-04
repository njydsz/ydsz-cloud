package com.njydsz.workflow.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


/**
 * 审计日志仓储接口（domain 层契约）。
 *
 * <p>定义审计日志（ydsz_flow_audit_log）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作审计日志聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowAuditLogVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / action / operatorId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowAuditLogRepository {

  /**
   * 保存审计日志（新增）。
   *
   * @param vo 审计日志 VO
   * @return 保存后的审计日志 VO（含生成的 id 与审计字段）
   */
  FlowAuditLogVO save(FlowAuditLogVO vo);

  /**
   * 根据 ID 查询审计日志。
   *
   * @param id 审计日志 ID
   * @return 审计日志 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowAuditLogVO> findById(String id);

  /**
   * 根据实例 ID 查询审计日志列表。
   *
   * @param instanceId 实例 ID
   * @return 审计日志 VO 列表
   */
  List<FlowAuditLogVO> findByInstanceId(String instanceId);

  /**
   * 根据实例 ID + 操作动作查询审计日志列表。
   *
   * @param instanceId 实例 ID
   * @param action 操作动作
   * @return 审计日志 VO 列表
   */
  List<FlowAuditLogVO> findByInstanceIdAndAction(String instanceId, String action);

  /**
   * 根据任务 ID 查询审计日志列表。
   *
   * @param taskId 任务 ID
   * @return 审计日志 VO 列表
   */
  List<FlowAuditLogVO> findByTaskId(String taskId);

  /**
   * 根据 ID 删除审计日志。
   *
   * @param id 审计日志 ID
   */
  void deleteById(String id);

  /**
   * 按业务类型 + 操作人查询审计日志（分页）。
   *
   * <p>用于代理人操作日志查询：按 {@code businessType + operatorId} 过滤，
   * 按时间倒序排列。
   *
   * @param businessType 业务类型
   * @param operatorId 操作人 ID
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 审计日志 VO 列表
   */
  List<FlowAuditLogVO> findByBusinessTypeAndOperator(
      String businessType, String operatorId, int offset, int limit);

  /**
   * 按业务类型 + 目标人查询审计日志（分页）。
   *
   * <p>用于授权人被代理日志查询：按 {@code businessType + targetId} 过滤，
   * 按时间倒序排列。
   *
   * @param businessType 业务类型
   * @param targetId 目标人 ID
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 审计日志 VO 列表
   */
  List<FlowAuditLogVO> findByBusinessTypeAndTarget(
      String businessType, String targetId, int offset, int limit);

  /**
   * 按业务类型 + 操作动作列表统计审计日志数量（带租户与时间范围过滤）。
   *
   * <p>用于代批率统计：统计 {@code businessType + action IN (...)} 且租户匹配、
   * 时间在 {@code [startTime, endTime]} 区间内的记录数。
   *
   * @param businessType 业务类型
   * @param actions 操作动作列表（如 PASS / REJECT）
   * @param tenantId 租户 ID（可为 null，表示不过滤）
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @return 符合条件的记录数
   */
  long countByBusinessTypeAndActions(
      String businessType,
      List<String> actions,
      String tenantId,
      LocalDateTime startTime,
      LocalDateTime endTime);

  /**
   * 分页查询流程实例的加签历史记录（P1-8: 数据库级分页，避免内存分页 OOM 风险）。
   *
   * <p>按 action IN (加签动作列表) + instance_id 过滤，支持 LIMIT/OFFSET 分页。
   *
   * @param instanceId 流程实例 ID
   * @param actions 加签动作列表
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 审计日志 VO 列表
   */
  List<FlowAuditLogVO> findCountersignByInstance(
      String instanceId, List<String> actions, int offset, int limit);

  /**
   * 统计流程实例的加签历史记录总数（P1-8: 配合分页查询返回 total）。
   *
   * @param instanceId 流程实例 ID
   * @param actions 加签动作列表
   * @return 符合条件的记录总数
   */
  long countCountersignByInstance(String instanceId, List<String> actions);
}
