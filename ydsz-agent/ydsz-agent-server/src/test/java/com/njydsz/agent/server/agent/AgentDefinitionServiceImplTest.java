package com.njydsz.agent.server.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.dto.AgentDefinitionDTO;
import com.njydsz.agent.domain.repository.AgentDefinitionRepository;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;

@ExtendWith(MockitoExtension.class)
class AgentDefinitionServiceImplTest {

  @InjectMocks
  private AgentDefinitionServiceImpl agentService;

  @Mock
  private AgentDefinitionRepository agentDefinitionRepository;

  @Test
  @DisplayName("toDomain - 正常解析VO为领域对象（含工具和模型配置）")
  void toDomain_validVo_returnsDomainObject() {
    AgentDefinitionVO vo = new AgentDefinitionVO();
    vo.setId("agt-001");
    vo.setAgentCode("test-agent");
    vo.setAgentName("测试Agent");
    vo.setAgentType("REACT");
    vo.setSystemPrompt("你是一个助手");
    vo.setToolNames("[\"search\",\"calculator\"]");
    vo.setTemperature(new BigDecimal("0.5"));
    vo.setMaxTokens(1024);
    vo.setModelConfig("{\"modelId\":\"gpt-4\",\"maxIterations\":5}");

    AgentDefinition domain = agentService.toDomain(vo);

    assertThat(domain).isNotNull();
    assertThat(domain.getCode()).isEqualTo("test-agent");
    assertThat(domain.getName()).isEqualTo("测试Agent");
    assertThat(domain.getType()).isEqualTo(AgentDefinition.Type.REACT);
    assertThat(domain.getToolNames()).hasSize(2);
    assertThat(domain.getToolNames().get(0)).isEqualTo("search");
    assertThat(domain.getTemperature()).isEqualByComparingTo(new BigDecimal("0.5"));
    assertThat(domain.getMaxTokens()).isEqualTo(1024);
    assertThat(domain.getMaxIterations()).isEqualTo(5);
    assertThat(domain.getModelId()).isEqualTo("gpt-4");
  }

  @Test
  @DisplayName("toDomain - 无效agentType降级为CHAT")
  void toDomain_invalidType_fallsBackToChat() {
    AgentDefinitionVO vo = new AgentDefinitionVO();
    vo.setId("agt-002");
    vo.setAgentCode("fallback-agent");
    vo.setAgentName("降级Agent");
    vo.setAgentType("UNKNOWN_TYPE");
    vo.setSystemPrompt("提示词");

    AgentDefinition domain = agentService.toDomain(vo);

    assertThat(domain.getType()).isEqualTo(AgentDefinition.Type.CHAT);
  }

  @Test
  @DisplayName("toDomain - null输入返回null")
  void toDomain_nullInput_returnsNull() {
    AgentDefinition domain = agentService.toDomain(null);

    assertThat(domain).isNull();
  }

  @Test
  @DisplayName("create - agentCode已存在时抛出异常")
  void create_duplicateCode_throwsException() {
    AgentDefinitionDTO dto = new AgentDefinitionDTO();
    dto.setAgentCode("existing-code");
    dto.setAgentName("已存在Agent");

    AgentDefinitionVO existing = new AgentDefinitionVO();
    existing.setId("agt-exist");
    when(agentDefinitionRepository.findByCode("existing-code")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> agentService.create(dto))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
