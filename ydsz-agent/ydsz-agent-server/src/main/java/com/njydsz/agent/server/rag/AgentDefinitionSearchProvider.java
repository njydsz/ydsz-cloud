package com.njydsz.agent.server.rag;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.repository.AgentDefinitionRepository;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;

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
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDefinitionSearchProvider implements SearchProvider<AgentDefinitionVO> {

  private final AgentDefinitionRepository agentDefinitionRepository;

  @Override
  public String getType() {
    return "agent";
  }

  /** {@inheritDoc} */
  @Override
  public List<SearchFilter> getFilters(SearchProviderContext context) {
    if (context == null || context.isAdmin()) {
      return List.of();
    }
    List<SearchFilter> filters = new ArrayList<>(2);
    // 租户隔离
    if (context.getTenantId() != null && !context.getTenantId().isBlank()) {
      filters.add(
          SearchFilter.builder()
              .field("tenant_id")
              .values(List.of(context.getTenantId()))
              .operator(SearchFilter.Operator.EQ)
              .build());
    }
    return filters;
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

  /**
   * 全量加载 Agent 定义（用于全量索引重建），对齐 {@link SearchProvider#loadAll(String)} SPI 契约。
   *
   * <p>委托 {@link AgentDefinitionRepository#findAll(String)} 实现租户感知的全量查询；
   * tenantId 为空时返回跨租户全量（适用于管理员触发索引重建场景）。
   *
   * @param tenantId 租户 ID；为空表示全量
   * @return Agent 定义 VO 列表；无数据时返回空列表而非 {@code null}
   */
  @Override
  public List<AgentDefinitionVO> loadAll(String tenantId) {
    return agentDefinitionRepository.findAll(tenantId);
  }
}
