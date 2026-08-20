package com.njydsz.workflow.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;

/**
 * 委托授权仓储接口（domain 层契约）。
 *
 * <p>定义委托授权（ydsz_flow_delegate_auth）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作委托授权聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowDelegateAuthVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（delegatorId / delegateeId / flowCode 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowDelegateAuthRepository {

  /**
   * 保存委托授权（新增）。
   *
   * @param vo 委托授权 VO
   * @return 保存后的委托授权 VO（含生成的 id 与审计字段）
   */
  FlowDelegateAuthVO save(FlowDelegateAuthVO vo);

  /**
   * 根据 ID 查询委托授权。
   *
   * @param id 委托授权 ID
   * @return 委托授权 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowDelegateAuthVO> findById(String id);

  /**
   * 根据委托人 ID 查询委托授权列表。
   *
   * @param delegatorId 委托人 ID
   * @return 委托授权 VO 列表
   */
  List<FlowDelegateAuthVO> findByDelegatorId(String delegatorId);

  /**
   * 根据委托人 ID + 流程编码查询委托授权。
   *
   * @param delegatorId 委托人 ID
   * @param flowCode 流程编码
   * @return 委托授权 VO 列表
   */
  List<FlowDelegateAuthVO> findByDelegatorAndFlow(String delegatorId, String flowCode);

  /**
   * 根据 ID 删除委托授权。
   *
   * @param id 委托授权 ID
   */
  void deleteById(String id);

  /**
   * 更新委托授权。
   *
   * @param vo 委托授权 VO（含 id）
   * @return 更新后的委托授权 VO
   */
  FlowDelegateAuthVO update(FlowDelegateAuthVO vo);

  /**
   * 查询委托人的有效委托授权列表。
   *
   * <p>返回 {@code ownerUserId = ? AND authStatus = 'ACTIVE'
   * AND startTime <= now AND (endTime IS NULL OR endTime >= now)} 的授权列表，
   * 用于离职转交等场景获取当前生效的授权规则。
   *
   * @param ownerId 委托人 ID
   * @param now 当前时间
   * @return 委托授权 VO 列表
   */
  List<FlowDelegateAuthVO> findActiveByOwner(String ownerId, LocalDateTime now);

  /**
   * 匹配委托权限（按委托人和流程编码）。
   *
   * <p>查询 {@code ownerUserId = ? AND flowCode = ? AND authStatus = 'ENABLED'
   * AND startTime <= now AND endTime >= now} 的授权列表，
   * 用于判断某委托人是否对某流程设置了有效委托。
   *
   * @param ownerId 委托人 ID
   * @param flowCode 流程编码
   * @param now 当前时间
   * @return 委托授权 VO 列表
   */
  List<FlowDelegateAuthVO> matchAuth(String ownerId, String flowCode, LocalDateTime now);

  /**
   * 更新委托授权状态。
   *
   * <p>用于批量停用/过期/撤销委托授权。
   *
   * @param id 委托授权 ID
   * @param status 目标状态（ENABLED / DISABLED / EXPIRED / REVOKED）
   */
  void updateStatus(String id, String status);

  /**
   * 查询授权人的授权列表（带状态过滤）。
   *
   * <p>返回 {@code ownerUserId = ? AND tenantId = ?} 的授权列表，
   * 按状态过滤（为 null 时返回全部）。
   *
   * @param tenantId 租户 ID
   * @param ownerUserId 授权人 ID
   * @param status 状态过滤（可为 null）
   * @return 委托授权 VO 列表
   */
  List<FlowDelegateAuthVO> selectByOwner(String tenantId, String ownerUserId, String status);

  /**
   * 查询代理人的受理授权列表（带状态过滤）。
   *
   * <p>返回 {@code delegateUserId = ? AND tenantId = ?} 的授权列表，
   * 按状态过滤（为 null 时返回全部）。
   *
   * @param tenantId 租户 ID
   * @param delegateUserId 代理人 ID
   * @param status 状态过滤（可为 null）
   * @return 委托授权 VO 列表
   */
  List<FlowDelegateAuthVO> selectByDelegate(String tenantId, String delegateUserId, String status);

  /**
   * 匹配某审批任务的代理人（按 scope 优先级）。
   *
   * <p>匹配规则：{@code FLOW_NODE > FLOW > ROLE > ALL}，匹配时校验当前时间在 {@code [startTime, endTime]} 区间内且状态为
   * {@code ENABLED}。
   *
   * @param tenantId 租户 ID
   * @param ownerUserId 原审批人 ID
   * @param flowCode 流程编码（可为 null）
   * @param nodeCode 节点编码（可为 null）
   * @param now 当前时间
   * @return 匹配的授权（无匹配返回 {@code Optional.empty()}）
   */
  Optional<FlowDelegateAuthVO> matchAuthByScope(
      String tenantId, String ownerUserId, String flowCode, String nodeCode, LocalDateTime now);

  /**
   * 扫描并标记过期授权。
   *
   * <p>将 {@code endTime < now} 且状态为 {@code ENABLED} 的授权标记为 {@code EXPIRED}。
   *
   * @param now 当前时间
   * @param endTime 截止时间阈值
   * @return 标记过期的授权数
   */
  int markExpired(LocalDateTime now, LocalDateTime endTime);
}
