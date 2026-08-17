package com.njydsz.agent.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.agent.domain.entity.AgentApprovalDO;

/**
 * Agent 人工审批请求 Repository
 *
 * <p>封装 {@code ydsz_agent_approval} 表的数据库访问，为 server 层提供审批请求的持久化操作。
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentApprovalRepository {

  /**
   * 插入审批请求记录
   *
   * @param entity 审批请求 DO
   */
  void insert(AgentApprovalDO entity);

  /**
   * 根据 ID 查询审批请求
   *
   * @param id 审批请求 ID
   * @return 审批请求 DO，不存在时返回 null
   */
  AgentApprovalDO findById(String id);

  /**
   * 查询待审批请求列表（按创建时间降序）
   *
   * @param status 审批状态
   * @return 审批请求 DO 列表
   */
  List<AgentApprovalDO> findPending(String status);

  /**
   * 更新审批请求状态（按 ID 精确更新）
   *
   * @param id 审批请求 ID
   * @param status 新状态
   * @param approver 审批人
   * @param comment 审批意见
   * @param resolvedAt 审批时间
   */
  void updateStatus(String id, String status, String approver, String comment, LocalDateTime resolvedAt);

  /**
   * 批量将超时未处理的待审批请求状态置为过期
   *
   * @param status 当前状态
   * @param cutoff 截止时间（创建时间早于此值的记录将被过期）
   * @param expiredStatus 过期状态
   * @param now 当前时间
   */
  void expirePendingBefore(String status, LocalDateTime cutoff, String expiredStatus, LocalDateTime now);
}
