package com.njydsz.workflow.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.njydsz.workflow.domain.dto.FlowInstanceDTO;
import com.njydsz.workflow.domain.query.FlowInstancePageQuery;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;

/**
 * 流程实例仓储接口（domain 层契约）。
 *
 * <p>定义流程实例的持久化抽象，隔离领域模型与具体数据访问技术实现。应用层 Service 通过此接口操作聚合根，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowInstanceVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link FlowInstancePageQuery}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link FlowInstanceDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowInstanceRepository {

  /**
   * 保存流程实例（新增 or 更新）。
   *
   * <p>新增：dto.id 由 Snowflake 生成后传入，内部通过 {@code dtoToEntity} 转换为实体执行 insert。<br>
   * 更新：根据 dto.id 定位并更新，内部通过 {@code dtoToEntityWithId} 转换为实体执行 update。
   *
   * @param dto 流程实例 DTO
   * @return 保存后的流程实例 VO（含生成的 id 与审计字段）
   */
  FlowInstanceVO save(FlowInstanceDTO dto);

  /**
   * 根据 ID 查询流程实例。
   *
   * @param id 实例 ID
   * @return 流程实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowInstanceVO> findById(String id);

  /**
   * 根据租户 ID + 业务类型 + 业务单据 ID 查询流程实例。
   *
   * @param tenantId 租户 ID
   * @param businessType 业务类型
   * @param businessId 业务单据 ID
   * @return 流程实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowInstanceVO> findByBusiness(String tenantId, String businessType, String businessId);

  /**
   * 根据业务类型 + 业务单据 ID + 状态查询流程实例（草稿场景）。
   *
   * <p>用于保存草稿时检查是否已有草稿实例，避免重复创建。
   *
   * @param businessType 业务类型
   * @param businessId 业务单据 ID
   * @param flowStatus 流程状态（如 DRAFT）
   * @return 流程实例 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowInstanceVO> findByBusinessAndStatus(
      String businessType, String businessId, String flowStatus);

  /**
   * 查询发起人的流程实例列表。
   *
   * @param initiatorId 发起人 ID
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> findByInitiatorId(String initiatorId);

  /**
   * 查询发起人发起的指定流程编码的实例列表。
   *
   * @param initiatorId 发起人 ID
   * @param flowCode 流程编码（可为 null，表示不过滤）
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> selectByInitiator(String initiatorId, String flowCode);

  /**
   * 查询父流程下的子流程实例。
   *
   * @param parentInstanceId 父流程实例 ID
   * @return 子流程实例 VO 列表
   */
  List<FlowInstanceVO> findChildren(String parentInstanceId);

  /**
   * 统计某状态下的实例数量。
   *
   * @param flowStatus 流程状态（{@link com.njydsz.workflow.domain.enums.FlowInstanceStatus#name()}）
   * @return 实例数量
   */
  long countByStatus(String flowStatus);

  /**
   * 查询挂起于指定时间之前的实例（用于管理员清理）。
   *
   * @param before 时间阈值
   * @param limit 最大返回数量
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> findSuspendedBefore(java.time.LocalDateTime before, int limit);

  /**
   * 根据 ID 删除流程实例（逻辑删除）。
   *
   * @param id 实例 ID
   */
  void deleteById(String id);

  /**
   * 更新流程变量 JSON。
   *
   * <p>仅更新 variable 字段，不影响其他字段。
   *
   * @param id 实例 ID
   * @param variable 流程变量 JSON
   */
  void updateVariable(String id, String variable);

  /**
   * 更新实例状态。
   *
   * @param id 实例 ID
   * @param flowStatus 流程状态
   * @param currentNodeCode 当前节点编码
   * @param currentNodeName 当前节点名称
   * @param endAt 结束时间
   * @param durationMs 耗时（毫秒）
   */
  void updateStatus(
      String id,
      String flowStatus,
      String currentNodeCode,
      String currentNodeName,
      java.time.LocalDateTime endAt,
      Long durationMs);

  /**
   * 更新实例的 dueAt 字段（子流程超时用）。
   *
   * @param id 实例 ID
   * @param dueAt 超时时间
   */
  void updateDueAt(String id, LocalDateTime dueAt);

  /**
   * 实例多维分页查询。
   *
   * <p>支持按业务类型、发起人、状态、时间范围等多维度过滤。
   *
   * @param query 分页查询参数（包含 offset/limit 分页信息）
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> findPage(FlowInstancePageQuery query);

  /**
   * 实例多维分页计数。
   *
   * <p>与 {@link #findPage(FlowInstancePageQuery)} 配套使用，返回符合条件的总记录数。
   *
   * @param query 分页查询参数
   * @return 总数
   */
  long countPage(FlowInstancePageQuery query);

  /**
   * 统计某流程定义下正在运行的实例数量。
   *
   * @param definitionId 流程定义 ID
   * @return 运行中实例数量
   */
  long countRunningByDefinition(String definitionId);

  /**
   * 查询某流程定义下运行中的实例，按当前节点分组。
   *
   * <p>返回列表，每项含 currentNodeCode / currentNodeName / cnt。
   *
   * @param definitionId 流程定义 ID
   * @return 节点分组统计列表
   */
  List<Map<String, Object>> countRunningGroupByNode(String definitionId);

  /**
   * 查询某流程定义下运行中的实例，按当前节点分组（含节点名称）。
   *
   * <p>返回列表，每项含 currentNodeCode / currentNodeName / cnt。
   *
   * @param definitionId 流程定义 ID
   * @return 节点分组统计列表
   */
  List<Map<String, Object>> selectRunningGroupByNode(String definitionId);

  /**
   * 按状态分组统计实例数量（监控概览用）。
   *
   * @param tenantId 租户 ID（可为 null）
   * @return 状态分组计数列表，每项含 flowStatus / cnt
   */
  List<Map<String, Object>> selectCountGroupByStatus(String tenantId);

  /**
   * 查询今日新增/完成计数。
   *
   * @param tenantId 租户 ID（可为 null）
   * @return Map 含 todayNewCount / todayCompletedCount
   */
  Map<String, Object> selectTodayCount(String tenantId);

  /**
   * 按日期分组统计新增实例数。
   *
   * @param tenantId 租户 ID（可为 null）
   * @param start 开始时间
   * @param end 结束时间
   * @return 每日新增列表
   */
  List<Map<String, Object>> selectDailyNewCount(String tenantId, LocalDateTime start, LocalDateTime end);

  /**
   * 按日期分组统计完成实例数。
   *
   * @param tenantId 租户 ID（可为 null）
   * @param start 开始时间
   * @param end 结束时间
   * @return 每日完成列表
   */
  List<Map<String, Object>> selectDailyCompletedCount(String tenantId, LocalDateTime start, LocalDateTime end);

  /**
   * 按流程编码分组统计实例数（监控分布图用）。
   *
   * @param tenantId 租户 ID（可为 null）
   * @param start 开始时间下界（可为 null）
   * @param end 开始时间上界（可为 null）
   * @return 流程类型分布列表
   */
  List<Map<String, Object>> selectFlowTypeDistribution(
      String tenantId, LocalDateTime start, LocalDateTime end);

  /**
   * 查询父流程下运行中的子流程实例（RUNNING 状态）。
   *
   * <p>用于批量终止时级联终止子流程 / 子流程列表查询。
   *
   * @param parentInstanceId 父流程实例 ID
   * @return 运行中的子流程实例 VO 列表
   */
  List<FlowInstanceVO> findRunningChildrenByParentId(String parentInstanceId);

  /**
   * 查询某流程定义下运行中的实例列表（可按租户过滤）。
   *
   * <p>用于流程实例迁移：获取源定义下所有 RUNNING 实例。
   *
   * @param definitionId 流程定义 ID
   * @param tenantId     租户 ID（可为 null，表示不过滤）
   * @return 运行中实例 VO 列表
   */
  List<FlowInstanceVO> findRunningByDefinition(String definitionId, String tenantId);

  /**
   * 更新流程实例（全字段更新）。
   *
   * <p>用于流程实例迁移：更新 definitionId / flowVersion / currentNodeCode / currentNodeName 等字段。
   *
   * @param vo 流程实例 VO（含 id）
   * @return 更新后的流程实例 VO
   */
  FlowInstanceVO update(FlowInstanceVO vo);

  /**
   * 查询待归档的已完成实例列表。
   *
   * <p>返回满足归档条件的实例：状态为 COMPLETED/TERMINATED/REJECTED 且 endAt 早于指定阈值，
   * 按 endAt 升序排列（先结束先归档）。
   *
   * @param statuses 目标状态列表
   * @param threshold 结束时间阈值
   * @param limit 返回数量上限
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> findArchiveCandidates(List<String> statuses, LocalDateTime threshold, int limit);

  /**
   * 查询长期运行实例（RUNNING 状态且启动时间早于阈值）。
   *
   * <p>用于异常检测：识别运行时间过长的流程实例。
   *
   * @param tenantId 租户 ID（可为 null，表示不过滤）
   * @param threshold 启动时间阈值（查询 startAt 早于此值的实例）
   * @param limit 返回数量上限
   * @return 流程实例 VO 列表
   */
  List<FlowInstanceVO> findLongRunning(String tenantId, LocalDateTime threshold, int limit);
}
