package com.njydsz.agent.server.agent;

import java.util.List;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.dto.AgentDefinitionDTO;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;

/**
 * Agent 定义 Service 接口
 *
 * <p>提供 Agent 定义的 CRUD 操作，支持从数据库加载 Agent 定义并转换为 {@link AgentDefinition} 领域对象供 {@link AgentFactory}
 * 使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface AgentDefinitionService {

  /**
   * 根据 ID 获取 Agent 定义
   *
   * @param id 主键 ID
   * @return Agent 定义 VO
   */
  AgentDefinitionVO getById(String id);

  /**
   * 根据 code 获取 Agent 定义
   *
   * @param code Agent 编码
   * @return Agent 定义 VO
   */
  AgentDefinitionVO getByCode(String code);

  /**
   * 列出所有活跃 Agent 定义
   *
   * @return 活跃 Agent 定义 VO 列表
   */
  List<AgentDefinitionVO> listActive();

  /**
   * 创建 Agent 定义
   *
   * @param dto Agent 定义 DTO（id 字段不传）
   * @return 创建后的 Agent 定义 VO
   */
  AgentDefinitionVO create(AgentDefinitionDTO dto);

  /**
   * 更新 Agent 定义
   *
   * @param dto Agent 定义 DTO（含 id）
   * @return 更新后的 Agent 定义 VO
   */
  AgentDefinitionVO update(AgentDefinitionDTO dto);

  /**
   * 逻辑删除
   *
   * @param id 主键 ID
   * @return true=删除成功
   */
  boolean removeById(String id);

  /**
   * 根据 VO 构建领域对象
   *
   * @param vo Agent 定义 VO
   * @return Agent 定义领域对象
   */
  AgentDefinition toDomain(AgentDefinitionVO vo);
}
