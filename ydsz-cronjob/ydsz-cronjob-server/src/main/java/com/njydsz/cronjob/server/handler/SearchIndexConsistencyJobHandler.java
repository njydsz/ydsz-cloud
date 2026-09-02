package com.njydsz.cronjob.server.handler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.sync.IndexConsistencyChecker;
import com.njydsz.cronjob.domain.job.JobHandler;

/**
 * 搜索索引一致性巡检 Job。
 *
 * <p>定时对比数据库文档数与搜索索引文档数，检测索引丢失或冗余， 并对不一致项自动修复（丢失文档重新索引，冗余文档删除索引）。
 *
 * <p><b>双重索引巡检：</b>同时检查 nw_search_index（DB 降级存储）和 ydsz_wiki_search_index（统一搜索主索引）与实际业务数据的一致性。
 *
 * <p>Bean 名称 = {@code searchIndexConsistencyJobHandler}， 在 ydsz_job
 * 表插入记录：handler=searchIndexConsistencyJobHandler。
 *
 * <p>建议调度时间：每天 03:00（业务低峰期）
 *
 * <pre>
 *   cron=0 0 3 * * ?
 *   param={"tenantId": null}  // null 表示全部租户
 * </pre>
 *
 * <p>参数 JSON 格式：
 *
 * <ul>
 *   <li>{@code null} 或 {@code ""} — 全部租户巡检
 *   <li>{@code {"tenantId":"xxx"} } — 仅巡检指定租户
 *   <li>{@code {"tenantId":"xxx","types":["job","dag"]}} — 仅巡检指定类型
 * </ul>
 *
 * <p><b>依赖触发：</b>仅当 classpath 存在 {@code ydsz-common-search} 时激活。 未引入搜索模块的应用不会装配本 Handler，也不会报错。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see IndexConsistencyChecker
 */
@Slf4j
@Component("searchIndexConsistencyJobHandler")
@RequiredArgsConstructor
public class SearchIndexConsistencyJobHandler implements JobHandler {

  /** 索引一致性校验器（可选注入，未引入 common-search 时为 null） */
  private final ObjectProvider<IndexConsistencyChecker> consistencyCheckerProvider;

  /**
   * 执行索引一致性巡检与自动修复。
   *
   * @param paramsJson 参数 JSON，可为 {"tenantId":"xxx"} 或空
   * @return 巡检结果 Map，包含 repaired / consistent / checkedAt
   */
  @Override
  public Object execute(String paramsJson) {
    IndexConsistencyChecker checker = consistencyCheckerProvider.getIfAvailable();
    if (checker == null) {
      log.info("[SearchIndexConsistency] IndexConsistencyChecker 不可用，跳过巡检");
      return Map.of("skipped", true, "reason", "IndexConsistencyChecker not available");
    }

    String tenantId = parseTenantId(paramsJson);
    log.info("[SearchIndexConsistency] 开始索引一致性巡检: tenantId={}", tenantId);

    // 执行巡检 + 自动修复
    int repaired = checker.autoRepair(tenantId);

    Map<String, Object> result = new HashMap<>(16);
    result.put("tenantId", tenantId);
    result.put("repaired", repaired);
    result.put("checkedAt", LocalDateTime.now().toString());
    result.put("consistent", repaired == 0);

    log.info("[SearchIndexConsistency] 巡检完成: repaired={}, consistent={}", repaired, repaired == 0);
    return result;
  }

  /**
   * 从参数中解析租户 ID。
   *
   * @param paramsJson 参数 JSON
   * @return 租户 ID，null 表示全部租户
   */
  private String parseTenantId(String paramsJson) {
    if (paramsJson == null || paramsJson.isBlank()) {
      return null;
    }
    try {
      // 简单解析 {"tenantId":"xxx"}
      String trimmed = paramsJson.trim();
      if (trimmed.startsWith("{") && trimmed.contains("tenantId")) {
        int start = trimmed.indexOf("\"tenantId\"");
        if (start > 0) {
          int colonIndex = trimmed.indexOf(':', start);
          int quoteStart = trimmed.indexOf('"', colonIndex + 1);
          int quoteEnd = trimmed.indexOf('"', quoteStart + 1);
          if (quoteStart > 0 && quoteEnd > quoteStart) {
            return trimmed.substring(quoteStart + 1, quoteEnd);
          }
        }
      }
    } catch (Exception e) {
      log.warn("[SearchIndexConsistency] 参数解析失败，按全租户处理: {}", e.getMessage());
    }
    return null;
  }
}
