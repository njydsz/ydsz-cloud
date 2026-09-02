package com.njydsz.workflow.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowCcVO;

/**
 * 抄送仓储接口（domain 层契约）。
 *
 * <p>定义抄送（ydsz_flow_cc）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作抄送聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowCcVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（instanceId / receiverId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowCcRepository {

  /**
   * 保存抄送（新增）。
   *
   * @param vo 抄送 VO
   * @return 保存后的抄送 VO（含生成的 id 与审计字段）
   */
  FlowCcVO save(FlowCcVO vo);

  /**
   * 批量保存抄送。
   *
   * @param ccList 抄送 VO 列表
   * @return 保存后的抄送 VO 列表
   */
  List<FlowCcVO> saveBatch(List<FlowCcVO> ccList);

  /**
   * 根据 ID 查询抄送。
   *
   * @param id 抄送 ID
   * @return 抄送 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowCcVO> findById(String id);

  /**
   * 根据实例 ID 查询抄送列表。
   *
   * @param instanceId 实例 ID
   * @return 抄送 VO 列表
   */
  List<FlowCcVO> findByInstanceId(String instanceId);

  /**
   * 根据接收人 ID 查询抄送列表。
   *
   * @param receiverId 接收人 ID
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 抄送 VO 列表
   */
  List<FlowCcVO> findByReceiverId(String receiverId, int offset, int limit);

  /**
   * 根据 ID 删除抄送。
   *
   * @param id 抄送 ID
   */
  void deleteById(String id);

  /**
   * 更新抄送。
   *
   * @param vo 抄送 VO（含 id）
   * @return 更新后的抄送 VO
   */
  FlowCcVO update(FlowCcVO vo);

  /**
   * 分页查询抄送我的列表（按接收人 + 租户）。
   *
   * <p>与 {@link #findByReceiverId(String, int, int)} 类似，但额外增加租户隔离条件，
   * 用于多租户场景下「抄送我的」分页查询。按创建时间倒序排列。
   *
   * @param userId 接收人 ID
   * @param tenantId 租户 ID
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 抄送 VO 列表
   */
  List<FlowCcVO> findCcByUserPage(String userId, String tenantId, int offset, int limit);

  /**
   * 统计抄送我的数量（按接收人 + 租户）。
   *
   * @param userId 接收人 ID
   * @param tenantId 租户 ID
   * @return 抄送数量
   */
  long countCcByUser(String userId, String tenantId);

  /**
   * 标记抄送为已读。
   *
   * <p>更新 {@code readStatus = 'READ', readAt = now()}。
   *
   * @param id 抄送 ID
   */
  void markRead(String id);

  /**
   * 标记单条抄送为已读（带操作人与时间戳）。
   *
   * <p>更新 {@code readStatus = 'READ', readAt = readAt}，限制仅抄送接收人本人可操作。
   *
   * @param id 抄送 ID
   * @param userId 当前用户 ID
   * @param readAt 已读时间
   * @return 受影响行数
   */
  int markRead(String id, String userId, LocalDateTime readAt);

  /**
   * 标记某用户当前租户下所有未读抄送为已读。
   *
   * @param tenantId 租户 ID
   * @param userId 用户 ID
   * @param readAt 已读时间
   * @return 受影响行数
   */
  int markAllRead(String tenantId, String userId, LocalDateTime readAt);

  /**
   * 统计用户当前租户下未读抄送数。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 未读抄送数
   */
  long countUnread(String userId, String tenantId);

  /**
   * 分页查询抄送我的列表（支持 readStatus / flowCode 过滤）。
   *
   * <p>与 {@link #findCcByUserPage(String, String, int, int)} 类似，
   * 但额外支持已读状态与流程编码过滤，用于前端「抄送我的」高级查询。
   *
   * @param userId 接收人 ID
   * @param tenantId 租户 ID
   * @param readStatus 已读状态过滤（可为 null 表示不过滤）
   * @param flowCode 流程编码过滤（可为 null 表示不过滤）
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 抄送 VO 列表
   */
  List<FlowCcVO> findCcByUserPage(
      String userId, String tenantId, String readStatus, String flowCode, int offset, int limit);

  /**
   * 统计抄送我的数量（支持 readStatus / flowCode 过滤）。
   *
   * <p>与 {@link #countCcByUser(String, String)} 类似，
   * 但额外支持已读状态与流程编码过滤。
   *
   * @param userId 接收人 ID
   * @param tenantId 租户 ID
   * @param readStatus 已读状态过滤（可为 null 表示不过滤）
   * @param flowCode 流程编码过滤（可为 null 表示不过滤）
   * @return 抄送数量
   */
  long countCcByUser(String userId, String tenantId, String readStatus, String flowCode);

  /**
   * 按实例 ID + 租户查询抄送列表。
   *
   * <p>用于审批详情页「抄送人」Tab 展示。与 {@link #findByInstanceId(String)} 类似，
   * 但增加租户隔离条件。
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID
   * @return 抄送 VO 列表
   */
  List<FlowCcVO> findByInstanceIdAndTenant(String tenantId, String instanceId);
}
