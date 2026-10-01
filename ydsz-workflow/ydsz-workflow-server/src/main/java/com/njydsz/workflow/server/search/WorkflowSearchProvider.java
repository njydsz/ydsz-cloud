package com.njydsz.workflow.server.search;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.api.SearchFilter;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderContext;
import com.njydsz.workflow.domain.repository.FlowTemplateRepository;
import com.njydsz.workflow.domain.vo.FlowTemplateVO;

/**
 * 工作流模板搜索提供者 — 将流程模板数据注册到统一搜索体系。
 *
 * <p><b>架构合规说明（26.10.01 DDD 分层规范修复）：</b>通过 domain 层 Repository 接口访问数据，
 * 禁止 server 层直接注入 infra Mapper（符合 §34.2.3）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowSearchProvider implements SearchProvider<FlowTemplateVO> {

    /** 模板名称匹配权重 */
  private static final float FIELD_WEIGHT = 3.0f;

  private final FlowTemplateRepository flowTemplateRepository;

  /** {@inheritDoc} */
  @Override
  public String getType() {
    return "workflow";
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

  /** {@inheritDoc} */
  @Override
  public IndexDocument toIndexDocument(FlowTemplateVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(vo.getId())
        .type("workflow")
        .title(vo.getTemplateName())
        .subtitle(vo.getCategory())
        .content(vo.getDescription())
        .snippet(vo.getTemplateCode())
        .status(vo.getStatus() != null ? String.valueOf(vo.getStatus()) : null)
        .path("/workflow/template/" + vo.getId())
        .tenantId(vo.getTenantId())
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
   * 全量加载模板（用于全量索引重建），对齐 {@link SearchProvider#loadAll(String)} SPI 契约。
   *
   * @param tenantId 租户 ID；为空表示全量
   * @return 模板列表
   */
  @Override
  public List<FlowTemplateVO> loadAll(String tenantId) {
    return flowTemplateRepository.findAll(tenantId);
  }
}
