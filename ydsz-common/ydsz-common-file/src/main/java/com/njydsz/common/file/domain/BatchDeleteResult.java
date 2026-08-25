package com.njydsz.common.file.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.njydsz.common.util.message.MessageUtils;

/**
 * 批量删除结果
 *
 * <p>包含成功删除的对象路径列表和失败的对象路径列表（含失败原因）。
 *
 * @param successList 成功删除的对象路径列表
 * @param failedList 失败的对象路径 → 失败原因映射
 * @author ydsz-team
 * @since 1.0.0
 */
public record BatchDeleteResult(List<String> successList, Map<String, String> failedList) {

  /** 全部成功的便捷构造方法 */
  public static BatchDeleteResult allSuccess(List<String> deletedPaths) {
    return new BatchDeleteResult(List.copyOf(deletedPaths), Collections.emptyMap());
  }

  /** 全部失败的便捷构造方法 */
  public static BatchDeleteResult allFailed(Map<String, String> errors) {
    return new BatchDeleteResult(Collections.emptyList(), errors);
  }

  /** 是否有失败项 */
  public boolean hasFailures() {
    return !failedList.isEmpty();
  }

  /** 成功删除的数量 */
  public int successCount() {
    return successList.size();
  }

  /** 失败删除的数量 */
  public int failureCount() {
    return failedList.size();
  }

  /** 是否全部成功 */
  public boolean allSuccess() {
    return failedList.isEmpty();
  }

  /** 全部成功提示文本 i18n key */
  private static final String KEY_ALL_SUCCESS = "file.batch.allSuccess";

  /** 多条失败记录之间的分隔符 i18n key */
  private static final String KEY_FAILURE_SEPARATOR = "file.batch.failureSeparator";

  /** 获取失败摘要信息 */
  public String getFailureSummary() {
    if (failedList.isEmpty()) {
      return MessageUtils.getMessage(KEY_ALL_SUCCESS, "全部成功");
    }
    String separator = MessageUtils.getMessage(KEY_FAILURE_SEPARATOR, "; ");
    return failedList.entrySet().stream()
        .map(e -> e.getKey() + ": " + e.getValue())
        .collect(Collectors.joining(separator));
  }
}
