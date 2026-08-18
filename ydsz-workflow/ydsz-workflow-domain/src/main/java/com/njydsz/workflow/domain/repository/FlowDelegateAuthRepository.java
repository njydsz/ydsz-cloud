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
   * <p>返回 {@code ownerUserId = ? AND tenantId = ? AND authStatus = 'ENABLED'
   * AND startTime <= now AND endTime >= now} 的授权列表，
   * 用于委托匹配时获取当前生效的授权规则。
   *
   * @param ownerId 委托人 ID
   * @param tenantId 租户 ID
   * @param now 当前时间
   * @return 委托授权 VO 列表
   */
  List<FlowDelegateAuthVO> findActiveByOwner(String ownerId, String tenantId, LocalDateTime now);

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
}
