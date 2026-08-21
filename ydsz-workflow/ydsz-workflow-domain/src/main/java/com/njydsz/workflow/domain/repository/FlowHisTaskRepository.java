package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowHisTaskVO;

/**
 * 历史任务仓储接口（domain 层契约）。
 *
 * <p>定义历史任务（ydsz_flow_his_task）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作历史任务聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowHisTaskVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / nodeCode / assigneeId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowHisTaskRepository {

  /**
   * 保存历史任务（新增）。
   *
   * @param vo 历史任务 VO
   * @return 保存后的历史任务 VO（含生成的 id 与审计字段）
   */
  FlowHisTaskVO save(FlowHisTaskVO vo);

  /**
   * 根据 ID 查询历史任务。
   *
   * @param id 任务 ID
   * @return 历史任务 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowHisTaskVO> findById(String id);

  /**
   * 根据实例 ID 查询历史任务列表。
   *
   * @param instanceId 实例 ID
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> findByInstanceId(String instanceId);

  /**
   * 根据实例 ID 查询已通过的节点列表（用于撤回场景）。
   *
   * @param instanceId 实例 ID
   * @return 节点列表，每个 Map 包含 nodeCode / nodeName / firstFinishAt / visitCount
   */
  List<Map<String, Object>> listPassedNodes(String instanceId);

  /**
   * 根据实例 ID + 节点编码查询历史任务。
   *
   * @param instanceId 实例 ID
   * @param nodeCode 节点编码
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> findByInstanceAndNode(String instanceId, String nodeCode);

  /**
   * 根据 ID 删除历史任务。
   *
   * @param id 任务 ID
   */
  void deleteById(String id);

  /**
   * 根据办理人查询历史任务列表。
   *
   * <p>查询 {@code assigneeId = ?} 的历史任务，
   * 按完成时间倒序排列，限制返回数量。用于「我审批过的」查询。
   *
   * @param userId 办理人 ID
   * @param limit 返回数量上限
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> findByAssignee(String userId, int limit);

  /**
   * 查询历史任务概览统计（用于分析仪表盘）。
   *
   * <p>返回 total / passed / rejected / avgDurationMs 等聚合指标。
   *
   * @param tenantId 租户 ID（可为 null 表示不过滤）
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @return 统计指标 Map
   */
  Map<String, Object> selectOverviewStats(String tenantId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);

  /**
   * 查询审批人效率排行。
   *
   * <p>返回每个审批人的：taskCount / avgDurationMs / passRate 等。
   *
   * @param tenantId 租户 ID
   * @param startTime 开始时间
   * @param endTime 结束时间
   * @param limit 返回数量上限
   * @return 审批人效率统计列表
   */
  List<Map<String, Object>> selectApproverEfficiency(
      String tenantId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime, int limit);

  /**
   * 查询流程效率对比。
   *
   * <p>按 flowCode 分组统计每个流程的平均耗时和完成率。
   *
   * @param tenantId 租户 ID
   * @param startTime 开始时间
   * @param endTime 结束时间
   * @return 流程效率对比列表
   */
  List<Map<String, Object>> selectFlowEfficiencyComparison(
      String tenantId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);

  /**
   * 查询节点耗时统计。
   *
   * <p>按 nodeCode 分组统计每个节点的平均耗时。
   *
   * @param flowCode 流程编码（可为 null）
   * @param tenantId 租户 ID
   * @return 节点耗时统计列表
   */
  List<Map<String, Object>> selectNodeDurationStats(String flowCode, String tenantId);

  /**
   * 查询审批趋势。
   *
   * <p>按时间粒度（day/week/month）统计审批量趋势。
   *
   * @param tenantId 租户 ID
   * @param startTime 开始时间
   * @param endTime 结束时间
   * @param granularity 时间粒度（day/week/month）
   * @return 趋势数据列表
   */
  List<Map<String, Object>> selectApprovalTrend(
      String tenantId, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime, String granularity);

  /**
   * 查询实例下已审批完成的去重审批人 ID 列表。
   *
   * <p>查询 ydsz_flow_his_task 中属于该实例、已完成的审批人去重后 ID。
   * 用于 P2-7 跨节点去重场景：获取已审批人集合。
   *
   * @param instanceId 实例 ID
   * @return 已审批人 ID 列表
   */
  List<String> selectCompletedAssigneeIds(String instanceId);

  /**
   * 查询办理人的已办列表（按完成时间倒序）。
   *
   * <p>走历史表 ydsz_flow_his_task，查询 assigneeId 对应的已完成任务。
   *
   * @param assigneeId 办理人 ID
   * @param tenantId   租户 ID
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> selectDoneByAssignee(String assigneeId, String tenantId);

  /**
   * 查询办理人的已办分页（真分页：SQL LIMIT/OFFSET）。
   *
   * @param assigneeId 办理人 ID
   * @param tenantId   租户 ID
   * @param offset     偏移量
   * @param limit      每页大小
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> selectDoneByAssigneePage(String assigneeId, String tenantId, int offset, int limit);

  /**
   * 已办多维筛选分页查询。
   *
   * @param assigneeId   办理人 ID
   * @param businessType 业务类型（可为 null）
   * @param flowCode     流程编码（可为 null）
   * @param startTime    开始时间（可为 null）
   * @param endTime      结束时间（可为 null）
   * @param tenantId     租户 ID
   * @param offset       偏移量
   * @param limit        每页大小
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> selectDonePage(String assigneeId, String businessType, String flowCode,
      java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
      String tenantId, int offset, int limit);

  /**
   * 统计办理人的已办总数。
   *
   * @param assigneeId 办理人 ID
   * @param tenantId   租户 ID
   * @return 已办总数
   */
  long countDoneByAssignee(String assigneeId, String tenantId);

  /**
   * 多维条件统计已办总数。
   *
   * @param assigneeId   办理人 ID
   * @param businessType 业务类型
   * @param flowCode     流程编码
   * @param startTime    开始时间
   * @param endTime      结束时间
   * @param tenantId     租户 ID
   * @return 符合条件的已办总数
   */
  long countDone(String assigneeId, String businessType, String flowCode,
      java.time.LocalDateTime startTime, java.time.LocalDateTime endTime, String tenantId);

  /**
   * 按租户 + 时间范围查询历史任务列表（带流程编码过滤）。
   *
   * <p>用于效率分析：查询指定租户下、时间范围内（finishAt 字段）的历史任务，
   * 按 finishAt 倒序排列，限制返回数量。
   *
   * @param tenantId 租户 ID（可为 null，表示不过滤）
   * @param flowCode 流程编码（可为 null，表示不过滤）
   * @param startTime 开始时间（finishAt >= startTime，可为 null）
   * @param endTime 结束时间（finishAt <= endTime，可为 null）
   * @param limit 返回数量上限
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> selectByTimeRange(
      String tenantId,
      String flowCode,
      java.time.LocalDateTime startTime,
      java.time.LocalDateTime endTime,
      int limit);

  /**
   * 按租户查询最近的历史任务（按 finishAt 倒序）。
   *
   * <p>用于高驳回率检测：获取最近 N 个历史任务样本。
   *
   * @param tenantId 租户 ID（可为 null，表示不过滤）
   * @param limit 返回数量上限
   * @return 历史任务 VO 列表
   */
  List<FlowHisTaskVO> selectRecentByTenant(String tenantId, int limit);
}
