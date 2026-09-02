package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.dto.AgentDefinitionDTO;
import com.njydsz.agent.domain.repository.AgentDefinitionRepository;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.common.json.YdszJson;

/**
 * Agent 定义 Service 实现
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDefinitionServiceImpl implements AgentDefinitionService {

  /** 默认模型温度：在创造性与稳定性之间取平衡 */
  private static final double DEFAULT_TEMPERATURE = 0.7;

  /** 单次对话最大 Token 默认值，避免单请求占用过多额度 */
  private static final int DEFAULT_MAX_TOKENS = 2048;

  /** Agent 定义 Repository */
  private final AgentDefinitionRepository agentDefinitionRepository;

  /**
   * {@inheritDoc}
   *
   * @return Agent 定义 VO，不存在或已删除时返回 null
   */
  @Override
  public AgentDefinitionVO getById(String id) {
    return agentDefinitionRepository.findById(id).orElse(null);
  }

  /**
   * {@inheritDoc}
   *
   * @param code Agent 编码
   * @return Agent 定义 VO，不存在时返回 null
   */
  @Override
  public AgentDefinitionVO getByCode(String code) {
    return agentDefinitionRepository.findByCode(code).orElse(null);
  }

  /**
   * {@inheritDoc}
   *
   * @return 状态为 ACTIVE 的 Agent 列表（按创建时间降序）
   */
  @Override
  public List<AgentDefinitionVO> listActive() {
    return agentDefinitionRepository.findActive();
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
  public AgentDefinitionVO create(AgentDefinitionDTO dto) {
    // 唯一性校验
    AgentDefinitionVO existing = getByCode(dto.getAgentCode());
    if (existing != null) {
      throw new IllegalArgumentException("Agent code already exists: " + dto.getAgentCode());
    }
    agentDefinitionRepository.insert(dto);
    log.info(
        "[Agent-Def] 创建 Agent: code={}, name={}", dto.getAgentCode(), dto.getAgentName());
    return getByCode(dto.getAgentCode());
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException 当 Agent 不存在或已删除时抛出
   */
  @Override
  @Transactional
  public AgentDefinitionVO update(AgentDefinitionDTO dto) {
    AgentDefinitionVO existing = agentDefinitionRepository.findById(dto.getId()).orElse(null);
    if (existing == null) {
      throw new IllegalArgumentException("Agent not found: id=" + dto.getId());
    }
    agentDefinitionRepository.updateById(dto);
    log.info("[Agent-Def] 更新 Agent: code={}", dto.getAgentCode());
    return agentDefinitionRepository.findById(dto.getId()).orElseThrow();
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
    return agentDefinitionRepository.deleteById(id);
  }

  /**
   * {@inheritDoc}
   *
   * <p>将 VO 转换为领域 AgentDefinition，解析 toolNames（JSON 数组）和 modelConfig（JSON 对象）， agentType 解析失败时降级为
   * CHAT。
   *
   * @param vo 视图对象
   * @return 领域定义对象，vo 为 null 时返回 null
   */
  @Override
  public AgentDefinition toDomain(AgentDefinitionVO vo) {
    if (vo == null) {
      return null;
    }
    List<String> tools = new ArrayList<>(16);
    if (vo.getToolNames() != null && !vo.getToolNames().isBlank()) {
      List<Object> parsed = YdszJson.parseArray(vo.getToolNames());
      for (Object t : parsed) {
        tools.add(String.valueOf(t));
      }
    }
    // 模型温度默认值：在创造性与稳定性之间取平衡，缺省时使用
    double temperature = vo.getTemperature() != null ? vo.getTemperature() : DEFAULT_TEMPERATURE;
    // 单次对话最大 Token 默认值，缺省时使用，避免单请求占用过多额度
    int maxTokens = vo.getMaxTokens() != null ? vo.getMaxTokens() : DEFAULT_MAX_TOKENS;
    // 从 modelConfig JSON 中提取 modelId（如果有）
    String modelId = null;
    // 推理最大迭代次数默认 10，限制 ReAct 循环次数防止无限调用工具/超支
    int maxIterations = 10;
    if (vo.getModelConfig() != null && !vo.getModelConfig().isBlank()) {
      Map<String, Object> config = YdszJson.parseMap(vo.getModelConfig());
      modelId = (String) config.get("modelId");
      Object mi = config.get("maxIterations");
      if (mi instanceof Number num) {
        maxIterations = num.intValue();
      }
    }
    AgentDefinition.Type type;
    try {
      type = AgentDefinition.Type.valueOf(vo.getAgentType().toUpperCase());
    } catch (Exception e) {
      type = AgentDefinition.Type.CHAT;
    }
    return new AgentDefinition(
        vo.getId(),
        vo.getAgentCode(),
        vo.getAgentName(),
        type,
        vo.getSystemPrompt(),
        tools,
        temperature,
        maxTokens,
        maxIterations,
        modelId);
  }
}
