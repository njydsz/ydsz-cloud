package com.njydsz.cronjob.server.handler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.service.IndexRebuildService;
import com.njydsz.cronjob.domain.job.JobHandler;

/**
 * 搜索索引全量重建 Job。
 *
 * <p>触发统一搜索索引（ydsz_wiki_search_index）的全量重建，适用于：
 *
 * <ul>
 *   <li>索引丢失/损坏后的恢复
 *   <li>首次部署后初始化索引
 *   <li>索引结构变更后的全量重建
 *   <li>数据修复后重建一致性索引
 * </ul>
 *
 * <p>Bean 名称 = {@code searchIndexRebuildJobHandler}， 在 ydsz_job
 * 表插入记录：handler=searchIndexRebuildJobHandler。
 *
 * <p><b>调度建议：</b>
 *
 * <ul>
 *   <li>按需手动触发（运维操作），不建议高频定时执行
 *   <li>如需定时，建议在业务低峰期（如每周日凌晨 03:00）执行一次
 * </ul>
 *
 * <p>参数 JSON 格式：
 *
 * <ul>
 *   <li>{@code null} 或 {@code ""} — 重建全部类型、全租户
 *   <li>{@code {"type":"wiki"}} — 仅重建 wiki 类型
 *   <li>{@code {"type":"wiki","tenantId":"xxx"}} — 仅重建指定租户的 wiki
 * </ul>
 *
 * <p><b>注意事项：</b>全量重建是高耗时操作，底层使用单线程池串行执行， 同时只允许 1 个重建任务运行。提交前请通过 {@code
 * IndexRebuildService.isRebuilding()} 检查是否有任务在执行。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see IndexRebuildService
 */
@Slf4j
@Component("searchIndexRebuildJobHandler")
@RequiredArgsConstructor
public class SearchIndexRebuildJobHandler implements JobHandler {

  /** 索引重建服务（可选注入，未引入 common-search 时为 null） */
  private final ObjectProvider<IndexRebuildService> indexRebuildServiceProvider;

  /**
   * 执行搜索索引全量重建。
   *
   * @param paramsJson 参数 JSON，可为 {"type":"xxx","tenantId":"xxx"} 或空
   * @return 重建结果 Map，包含 rebuilt / type / tenantId / checkedAt
   */
  @Override
  public Object execute(String paramsJson) {
    IndexRebuildService rebuildService = indexRebuildServiceProvider.getIfAvailable();
    if (rebuildService == null) {
      log.info("[SearchIndexRebuild] IndexRebuildService 不可用，跳过重建");
      return Map.of("skipped", true, "reason", "IndexRebuildService not available");
    }

    String type = parseType(paramsJson);
    String tenantId = parseTenantId(paramsJson);

    log.info("[SearchIndexRebuild] 开始全量索引重建: type={}, tenantId={}", type, tenantId);

    // 执行全量重建（内部使用单线程池串行执行）
    int rebuilt = rebuildService.rebuildAll(type, tenantId);

    Map<String, Object> result = new HashMap<>(16);
    result.put("type", type);
    result.put("tenantId", tenantId);
    result.put("rebuilt", rebuilt);
    result.put("checkedAt", LocalDateTime.now().toString());
    result.put("success", rebuilt >= 0);

    if (rebuilt >= 0) {
      log.info("[SearchIndexRebuild] 索引重建完成: type={}, rebuilt={}", type, rebuilt);
    } else {
      log.warn("[SearchIndexRebuild] 索引重建失败或已在执行中: type={}", type);
    }
    return result;
  }

  /**
   * 从参数中解析实体类型。
   *
   * @param paramsJson 参数 JSON
   * @return 实体类型，null 表示全部类型
   */
  private String parseType(String paramsJson) {
    return parseJsonValue(paramsJson, "type");
  }

  /**
   * 从参数中解析租户 ID。
   *
   * @param paramsJson 参数 JSON
   * @return 租户 ID，null 表示全部租户
   */
  private String parseTenantId(String paramsJson) {
    return parseJsonValue(paramsJson, "tenantId");
  }

  /**
   * 从简单 JSON 中提取字符串字段值。
   *
   * @param json JSON 字符串
   * @param field 字段名
   * @return 字段值，解析失败或不存在返回 null
   */
  private String parseJsonValue(String json, String field) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      String trimmed = json.trim();
      if (!trimmed.startsWith("{")) {
        return null;
      }
      int fieldStart = trimmed.indexOf("\"" + field + "\"");
      if (fieldStart < 0) {
        return null;
      }
      int colonIndex = trimmed.indexOf(':', fieldStart);
      if (colonIndex < 0) {
        return null;
      }
      int quoteStart = trimmed.indexOf('"', colonIndex + 1);
      if (quoteStart < 0) {
        return null;
      }
      int quoteEnd = trimmed.indexOf('"', quoteStart + 1);
      if (quoteEnd < quoteStart) {
        return null;
      }
      String value = trimmed.substring(quoteStart + 1, quoteEnd);
      return value.isBlank() ? null : value;
    } catch (Exception e) {
      log.warn("[SearchIndexRebuild] 参数解析失败: field={}, error={}", field, e.getMessage());
      return null;
    }
  }
}
