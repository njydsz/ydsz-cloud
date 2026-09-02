package com.njydsz.agent.server.rag;

import java.time.ZoneId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;

/**
 * Agent 定义搜索提供者 — 将 Agent 定义数据注册到统一搜索体系。
 *
 * <p>实现 {@link SearchProvider} SPI，将 Agent 定义实体转换为统一索引文档。
 *
 * <p><b>P1 修复</b>：原实现包含 {@code getTypeLabel} / {@code getSearchableFields} / {@code loadById}
 * 三个方法并标注 {@code @Override}，但 {@link SearchProvider} 接口中并不存在这些方法（幽灵方法）， 编译无法通过；已移除。
 * 若未来需要搜索字段权重配置或单条加载能力，应在 {@link SearchProvider} 接口中统一设计。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class AgentDefinitionSearchProvider implements SearchProvider<AgentDefinitionVO> {

  @Override
  public String getType() {
    return "agent";
  }

  @Override
  public IndexDocument toIndexDocument(AgentDefinitionVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(vo.getId())
        .type("agent")
        .title(vo.getAgentName())
        .subtitle(vo.getAgentType())
        .content(vo.getDescription())
        .snippet(vo.getAgentCode())
        .path("/agent/definition/" + vo.getId())
        .createdBy(vo.getCreatedBy())
        .createdAt(
            vo.getCreatedAt() != null
                ? vo.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .updatedBy(vo.getUpdatedBy())
        .updatedAt(
            vo.getUpdatedAt() != null
                ? vo.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .build();
  }
}
