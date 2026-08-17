package com.njydsz.agent.infra.repository;

import java.util.List;

import com.njydsz.agent.domain.entity.AgentDefinitionDO;

/**
 * Agent 定义 Repository
 *
 * <p>封装 {@code ydsz_agent_def} 表的数据库访问，为 server 层提供业务语义化的数据操作接口。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_agent_code — Agent 编码唯一索引
 *   <li>idx_status — 状态过滤索引（DRAFT/PUBLISHED/DEPRECATED）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentDefinitionRepository {

  /**
   * 根据 ID 查询 Agent 定义
   *
   * @param id 主键 ID
   * @return Agent 定义 DO，不存在时返回 null
   */
  AgentDefinitionDO findById(String id);

  /**
   * 根据 Agent 编码查询（过滤已删除记录）
   *
   * @param agentCode Agent 编码
   * @return Agent 定义 DO，不存在或已删除时返回 null
   */
  AgentDefinitionDO findByCode(String agentCode);

  /**
   * 查询活跃 Agent 定义列表（状态为 ACTIVE，过滤已删除记录，按创建时间降序）
   *
   * @return Agent 定义 DO 列表
   */
  List<AgentDefinitionDO> findActive();

  /**
   * 插入 Agent 定义
   *
   * @param entity Agent 定义 DO
   */
  void insert(AgentDefinitionDO entity);

  /**
   * 根据 ID 更新 Agent 定义
   *
   * @param entity Agent 定义 DO
   */
  void updateById(AgentDefinitionDO entity);

  /**
   * 根据 ID 逻辑删除 Agent 定义
   *
   * @param id 主键 ID
   * @return true=删除成功（影响行数 > 0）
   */
  boolean deleteById(String id);
}
