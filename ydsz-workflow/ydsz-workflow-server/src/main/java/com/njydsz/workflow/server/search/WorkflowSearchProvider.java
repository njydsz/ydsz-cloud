package com.njydsz.workflow.server.search;

import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.workflow.domain.repository.FlowTemplateRepository;
import com.njydsz.workflow.domain.vo.FlowTemplateVO;

/**
 * 工作流模板搜索提供者 — 将流程模板数据注册到统一搜索体系。
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范修复）：</b>通过 domain 层 Repository 接口访问数据，
 * 禁止 server 层直接注入 infra Mapper（符合 §34.2.3）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowSearchProvider implements SearchProvider<FlowTemplateVO> {

    /** 模板名称匹配权重 */
  private static final float FIELD_WEIGHT = 3.0f;

  private final FlowTemplateRepository flowTemplateRepository;

  @Override
  public String getType() {
    return "workflow";
  }

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
