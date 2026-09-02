package com.njydsz.cronjob.server.search;

import java.time.ZoneId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 定时任务搜索提供者 — 将任务定义注册到统一搜索体系。
 *
 * <p>P2-修正：移除未使用的 Mapper 注入，改用 VO 作为入参类型以符合 DDD 分层规范。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSearchProvider implements SearchProvider<JobVO> {

  @Override
  public String getType() {
    return "job";
  }

  @Override
  public IndexDocument toIndexDocument(JobVO vo) {
    if (vo == null || vo.getId() == null) {
      return null;
    }
    return IndexDocument.builder()
        .id(vo.getId())
        .type("job")
        .title(vo.getJobName())
        .subtitle(vo.getJobKey())
        .content(vo.getJobRemark())
        .snippet(vo.getCronExpression())
        .status(vo.getScheduleType() != null ? vo.getScheduleType() : "CRON")
        .path("/cronjob/job/" + vo.getId())
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
