package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.dto.FlowDefinitionDTO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;

/**
 * 流程定义仓储接口（domain 层契约）。
 *
 * <p>定义流程定义（ydsz_flow_definition）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作流程定义聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowDefinitionVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（flowCode / version / tenantId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowDefinitionRepository {

  /**
   * 保存流程定义（新增 or 更新）。
   *
   * <p><b>合规说明（1.0.0 DDD 分层规范）：</b>CUD 入参使用 {@link FlowDefinitionDTO}（dto/ 包），
   * 符合 §34.2.1（dto/ 命令请求参数 以 DTO 结尾）。
   *
   * @param dto 流程定义命令 DTO
   * @return 保存后的流程定义 VO（含生成的 id 与审计字段）
   */
  FlowDefinitionVO save(FlowDefinitionDTO dto);

  /**
   * 保存流程定义（已废弃）。
   *
   * @deprecated 使用 {@link #save(FlowDefinitionDTO)} 替代，CUD 入参应使用 DTO
   */
  @Deprecated
  FlowDefinitionVO save(FlowDefinitionVO vo);

  /**
   * 根据 ID 查询流程定义。
   *
   * @param id 流程定义 ID
   * @return 流程定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findById(String id);

  /**
   * 根据流程编码查询最新已发布的流程定义。
   *
   * @param flowCode 流程编码
   * @param version 版本号（可为 null，表示查询最新版本）
   * @param tenantId 租户 ID
   * @return 流程定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findPublished(String flowCode, String version, String tenantId);

  /**
   * 根据流程编码查询所有版本的流程定义。
   *
   * @param flowCode 流程编码
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findByFlowCode(String flowCode);

  /**
   * 根据流程编码 + 版本号查询流程定义。
   *
   * @param flowCode 流程编码
   * @param version 版本号
   * @return 流程定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findByFlowCodeAndVersion(String flowCode, String version);

  /**
   * 根据 ID 删除流程定义（逻辑删除）。
   *
   * @param id 流程定义 ID
   */
  void deleteById(String id);

  /**
   * 更新流程定义。
   *
   * <p><b>合规说明（1.0.0 DDD 分层规范）：</b>CUD 入参使用 {@link FlowDefinitionDTO}（dto/ 包）。
   *
   * @param dto 流程定义命令 DTO（含 id）
   * @return 更新后的流程定义 VO
   */
  FlowDefinitionVO update(FlowDefinitionDTO dto);

  /**
   * 更新流程定义（已废弃）。
   *
   * @deprecated 使用 {@link #update(FlowDefinitionDTO)} 替代，CUD 入参应使用 DTO
   */
  @Deprecated
  FlowDefinitionVO update(FlowDefinitionVO vo);

  /**
   * 查询流程定义列表（分页）。
   *
   * @param flowCode 流程编码（可选）
   * @param flowName 流程名称（可选）
   * @param tenantId 租户 ID（可选）
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findPage(String flowCode, String flowName, String tenantId, int offset, int limit);

  /**
   * 统计流程定义数量。
   *
   * @param flowCode 流程编码（可选）
   * @param flowName 流程名称（可选）
   * @param tenantId 租户 ID（可选）
   * @return 流程定义数量
   */
  long countPage(String flowCode, String flowName, String tenantId);

  /**
   * 分页查询流程定义（简化版）。
   *
   * <p>返回 {@code activityStatus=1} 且未逻辑删除的记录，按创建时间倒序。
   *
   * @param pageNo 页码（从 1 开始）
   * @param pageSize 每页大小
   * @param category 分类编码过滤（可选）
   * @param flowCode 流程编码模糊过滤（可选）
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findActivePage(int pageNo, int pageSize, String category, String flowCode);

  /**
   * 根据流程编码 + 租户查询所有版本的流程定义。
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findByFlowCodeAndTenantId(String flowCode, String tenantId);

  /**
   * 根据流程编码查询最新版本定义（不限制发布状态）。
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @return 流程定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findLatestByCode(String flowCode, String tenantId);

  /**
   * 按分类查询已启用的流程定义列表。
   *
   * <p>返回 {@code category = ? AND activityStatus = 1 AND isPublish = 1 AND deleted = 0} 的定义列表，
   * 按创建时间倒序排列。用于流程发起页按分类展示可发起的流程。
   *
   * @param categoryCode 流程分类编码
   * @param tenantId 租户 ID
   * @return 流程定义 VO 列表
   */
  List<FlowDefinitionVO> findEnabledByCategory(String categoryCode, String tenantId);

  /**
   * CAS 加锁流程定义（设计器协同编辑）。
   *
   * <p>通过 CAS 乐观锁实现多用户并发加锁：
   * 当当前锁定人非指定用户或锁已超时或未锁定时，更新锁定人为指定用户。
   *
   * @param definitionId 流程定义 ID
   * @param userId 加锁用户 ID
   * @param now 当前时间
   * @param lockedBy 锁定人 ID（用于 WHERE 条件匹配）
   * @param timeoutExpired 超时时间阈值
   * @param revision 当前版本号（乐观锁 CAS 条件）
   * @return 更新行数（1=加锁成功，0=失败）
   */
  int casLock(
      String definitionId,
      String userId,
      java.time.LocalDateTime now,
      String lockedBy,
      java.time.LocalDateTime timeoutExpired,
      Integer revision);

  /**
   * CAS 解锁流程定义（设计器协同编辑）。
   *
   * <p>仅持锁人本人可解锁：当当前锁定人等于指定用户时，清空锁定人。
   *
   * @param definitionId 流程定义 ID
   * @param userId 解锁用户 ID
   * @param revision 当前版本号（乐观锁 CAS 条件）
   * @return 更新行数（1=解锁成功，0=失败）
   */
  int casUnlock(String definitionId, String userId, Integer revision);

  /**
   * 发布流程定义（更新 isPublish 状态）。
   *
   * @param definitionId 流程定义 ID
   * @param isPublish 目标发布状态（1=已发布，9=已废弃）
   */
  void publish(String definitionId, int isPublish);

  /**
   * 按流程编码批量失效已发布版本（切换激活版本用）。
   *
   * <p>将指定 flowCode 下除 targetDefinitionId 外的所有已发布版本置为 isPublish=0。
   *
   * @param flowCode 流程编码
   * @param targetDefinitionId 目标定义 ID（排除在外）
   * @param tenantId 租户 ID
   */
  void deactivateByFlowCode(String flowCode, String targetDefinitionId, String tenantId);

  /**
   * 更新流程定义的 activityStatus。
   *
   * @param definitionId 流程定义 ID
   * @param activityStatus 目标启用状态（1=启用，0=停用）
   */
  void updateActivityStatus(String definitionId, int activityStatus);

  /**
   * 查询上一已发布版本（回滚用）。
   *
   * <p>返回指定 flowCode 下除当前定义 ID 外的最新已发布版本。
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @param excludeDefinitionId 排除的定义 ID
   * @return 上一已发布版本 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDefinitionVO> findPreviousPublishedVersion(String flowCode, String tenantId, String excludeDefinitionId);
}
