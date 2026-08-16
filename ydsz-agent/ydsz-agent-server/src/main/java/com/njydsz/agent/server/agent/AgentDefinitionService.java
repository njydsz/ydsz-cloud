package com.njydsz.agent.server.agent;

import java.util.List;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.entity.AgentDefinitionDO;

/**
 * Agent 定义 Service 接口
 *
 * <p>提供 Agent 定义的 CRUD 操作，支持从数据库加载 Agent 定义并转换为 {@link AgentDefinition} 领域对象供 {@link AgentFactory}
 * 使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentDefinitionService {

  /**
   * 根据 ID 获取 Agent 定义
   *
   * @param id 主键 ID
   * @return Agent 定义 DO
   */
  AgentDefinitionDO getById(String id);

  /**
   * 根据 code 获取 Agent 定义
   *
   * @param code Agent 编码
   * @return Agent 定义 DO
   */
  AgentDefinitionDO getByCode(String code);

  /**
   * 列出所有活跃 Agent 定义
   *
   * @return 活跃 Agent 定义列表
   */
  List<AgentDefinitionDO> listActive();

  /**
   * 创建 Agent 定义
   *
   * @param entity Agent 定义 DO
   * @return 创建后的 Agent 定义 DO
   */
  AgentDefinitionDO create(AgentDefinitionDO entity);

  /**
   * 更新 Agent 定义
   *
   * @param entity Agent 定义 DO
   * @return 更新后的 Agent 定义 DO
   */
  AgentDefinitionDO update(AgentDefinitionDO entity);

  /**
   * 逻辑删除
   *
   * @param id 主键 ID
   * @return true=删除成功
   */
  boolean removeById(String id);

  /**
   * 根据 DO 构建领域对象
   *
   * @param entity Agent 定义 DO
   * @return Agent 定义领域对象
   */
  AgentDefinition toDomain(AgentDefinitionDO entity);
}
