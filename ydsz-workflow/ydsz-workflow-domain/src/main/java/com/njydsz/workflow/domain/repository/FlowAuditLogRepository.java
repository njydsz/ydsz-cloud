package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowAuditLogVO;

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
 * @since 1.0.0
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
}
