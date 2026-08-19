package com.njydsz.cronjob.server.search;

import java.time.ZoneId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;

/**
 * 定时任务搜索提供者 — 将任务定义注册到统一搜索体系。
 *
 * <p>P0-FIX: 移除旧版 SearchProvider 接口已删除的方法（getTypeLabel/getSearchableFields/loadById），
 * 仅保留新接口契约（getType/toIndexDocument），消除"方法不覆盖"编译错误。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSearchProvider implements SearchProvider<Job> {

  private final JobMapper jobMapper;

  @Override
  public String getType() {
    return "job";
  }

  @Override
  public IndexDocument toIndexDocument(Job entity) {
    if (entity == null || entity.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(entity.getId())
        .type("job")
        .title(entity.getJobName())
        .subtitle(entity.getJobKey())
        .content(entity.getJobRemark())
        .snippet(entity.getCronExpression())
        .status(entity.getScheduleType() != null ? entity.getScheduleType() : "CRON")
        .path("/cronjob/job/" + entity.getId())
        .tenantId(entity.getTenantId())
        .createdBy(entity.getCreatedBy())
        .createdAt(
            entity.getCreatedAt() != null
                ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(
            entity.getUpdatedAt() != null
                ? entity.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : null)
        .build();
  }
}
