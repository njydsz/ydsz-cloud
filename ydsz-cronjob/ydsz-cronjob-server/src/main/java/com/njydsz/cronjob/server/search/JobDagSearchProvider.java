package com.njydsz.cronjob.server.search;

import java.time.ZoneId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.cronjob.domain.vo.JobDagVO;

/**
 * DAG 工作流搜索提供者 — 将 DAG 定义注册到统一搜索体系。
 *
 * <p>P2-修正：移除未使用的 Mapper 注入，改用 VO 作为入参类型以符合 DDD 分层规范。
 * 仅保留新接口契约（getType/toIndexDocument）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobDagSearchProvider implements SearchProvider<JobDagVO> {

  @Override
  public String getType() {
    return "job_dag";
  }

  @Override
  public IndexDocument toIndexDocument(JobDagVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(vo.getId())
        .type("job_dag")
        .title(vo.getDagName())
        .subtitle(vo.getDagKey())
        .content(vo.getDescription())
        .snippet(vo.getTriggerType())
        .status(vo.getDagStatus())
        .path("/cronjob/dag/" + vo.getId())
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
}
