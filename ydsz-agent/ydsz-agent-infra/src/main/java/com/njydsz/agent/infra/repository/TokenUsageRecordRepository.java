package com.njydsz.agent.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.agent.domain.entity.TokenUsageRecordDO;

/**
 * Token 用量记录 Repository
 *
 * <p>封装 {@code ydsz_agent_token_usage} 表的数据库访问，为 server 层提供 Token 用量的持久化操作。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_conversation_id — 对话 ID 索引（按会话查询用量）
 *   <li>idx_created_at — 创建时间索引（按时间范围聚合统计）
 *   <li>idx_model_name — 模型名称索引（按模型分组统计）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>用量记录不做逻辑删除，永久保留以供审计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TokenUsageRecordRepository {

  /**
   * 插入 Token 用量记录
   *
   * @param record Token 用量记录 DO
   */
  void insert(TokenUsageRecordDO record);

  /**
   * 按时间范围查询 Token 用量记录（按创建时间升序）
   *
   * @param startTime 开始时间（含）
   * @param endTime 结束时间（含）
   * @return Token 用量记录 DO 列表
   */
  List<TokenUsageRecordDO> findByCreatedAtRange(LocalDateTime startTime, LocalDateTime endTime);
}
