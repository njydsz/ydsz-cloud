package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.entity.AgentDefinitionDO;
import com.njydsz.agent.infra.mapper.AgentDefinitionMapper;
import com.njydsz.common.json.YdszJson;

/**
 * Agent 定义 Service 实现
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDefinitionServiceImpl implements AgentDefinitionService {

  /** Agent 定义 Mapper */
  private final AgentDefinitionMapper mapper;

  /**
   * {@inheritDoc}
   *
   * @return Agent 定义 DO，不存在或已删除时返回 null
   */
  @Override
  public AgentDefinitionDO getById(String id) {
    AgentDefinitionDO entity = mapper.selectById(id);
    if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
      return null;
    }
    return entity;
  }

  /**
   * {@inheritDoc}
   *
   * @param code Agent 编码
   * @return Agent 定义 DO，不存在时返回 null
   */
  @Override
  public AgentDefinitionDO getByCode(String code) {
    return mapper.selectOne(
        new QueryWrapper<AgentDefinitionDO>()
            .eq("agent_code", code)
            .eq("deleted", false)
            .last("LIMIT 1"));
  }

  /**
   * {@inheritDoc}
   *
   * @return 状态为 ACTIVE 的 Agent 列表（按创建时间降序）
   */
  @Override
  public List<AgentDefinitionDO> listActive() {
    return mapper.selectList(
        new QueryWrapper<AgentDefinitionDO>()
            .eq("status", "ACTIVE")
            .eq("deleted", false)
            .orderByDesc("created_at"));
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行 agentCode 唯一性校验后插入。
   *
   * @throws IllegalArgumentException 当 agentCode 已存在时抛出
   */
  @Override
  @Transactional
  public AgentDefinitionDO create(AgentDefinitionDO entity) {
    // 唯一性校验
    AgentDefinitionDO existing = getByCode(entity.getAgentCode());
    if (existing != null) {
      throw new IllegalArgumentException("Agent code already exists: " + entity.getAgentCode());
    }
    mapper.insert(entity);
    log.info(
        "[Agent-Def] 创建 Agent: code={}, name={}", entity.getAgentCode(), entity.getAgentName());
    return entity;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException 当 Agent 不存在或已删除时抛出
   */
  @Override
  @Transactional
  public AgentDefinitionDO update(AgentDefinitionDO entity) {
    AgentDefinitionDO existing = mapper.selectById(entity.getId());
    if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
      throw new IllegalArgumentException("Agent not found: id=" + entity.getId());
    }
    mapper.updateById(entity);
    log.info("[Agent-Def] 更新 Agent: code={}", entity.getAgentCode());
    return entity;
  }

  /**
   * {@inheritDoc}
   *
   * <p>逻辑删除 Agent 定义（物理删除，依赖 Mapper 的 deleteById）。
   *
   * @param id 主键 ID
   * @return true=删除成功（影响行数 > 0）
   */
  @Override
  @Transactional
  public boolean removeById(String id) {
    return mapper.deleteById(id) > 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>将 DO 转换为领域 AgentDefinition，解析 toolNames（JSON 数组）和 modelConfig（JSON 对象）， agentType 解析失败时降级为
   * CHAT。
   *
   * @param entity 数据库实体
   * @return 领域定义对象，entity 为 null 时返回 null
   */
  @Override
  public AgentDefinition toDomain(AgentDefinitionDO entity) {
    if (entity == null) {
      return null;
    }
    List<String> tools = new ArrayList<>();
    if (entity.getToolNames() != null && !entity.getToolNames().isBlank()) {
      List<Object> parsed = YdszJson.parseArray(entity.getToolNames());
      for (Object t : parsed) {
        tools.add(String.valueOf(t));
      }
    }
    // 模型温度默认值 0.7：在创造性与稳定性之间取平衡，缺省时使用
    double temperature = entity.getTemperature() != null ? entity.getTemperature() : 0.7;
    // 单次对话最大 Token 默认 2048，缺省时使用，避免单请求占用过多额度
    int maxTokens = entity.getMaxTokens() != null ? entity.getMaxTokens() : 2048;
    // 从 modelConfig JSON 中提取 modelId（如果有）
    String modelId = null;
    // 推理最大迭代次数默认 10，限制 ReAct 循环次数防止无限调用工具/超支
    int maxIterations = 10;
    if (entity.getModelConfig() != null && !entity.getModelConfig().isBlank()) {
      Map<String, Object> config = YdszJson.parseMap(entity.getModelConfig());
      modelId = (String) config.get("modelId");
      Object mi = config.get("maxIterations");
      if (mi instanceof Number num) {
        maxIterations = num.intValue();
      }
    }
    AgentDefinition.Type type;
    try {
      type = AgentDefinition.Type.valueOf(entity.getAgentType().toUpperCase());
    } catch (Exception e) {
      type = AgentDefinition.Type.CHAT;
    }
    return new AgentDefinition(
        entity.getId(),
        entity.getAgentCode(),
        entity.getAgentName(),
        type,
        entity.getSystemPrompt(),
        tools,
        temperature,
        maxTokens,
        maxIterations,
        modelId);
  }
}
