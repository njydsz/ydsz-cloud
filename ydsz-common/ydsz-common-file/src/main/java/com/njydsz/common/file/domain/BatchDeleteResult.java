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

  /**
   * 构造「全部删除成功」的结果。
   *
   * <p>失败映射固定为空 {@link Map}，使 {@link #hasFailures()} 与 {@link #allSuccess()} 返回稳定结果。
   *
   * @param deletedPaths 本次成功删除的对象路径，内部按 {@link List#copyOf} 防御性拷贝； 不允许为 {@code null}，元素不应为 {@code null}
   * @return 不含任何失败项的结果实例，不会为 {@code null}
   */
  public static BatchDeleteResult allSuccess(List<String> deletedPaths) {
    return new BatchDeleteResult(List.copyOf(deletedPaths), Collections.emptyMap());
  }

  /**
   * 构造「全部删除失败」的结果。
   *
   * <p>成功列表固定为空 {@link List}，用于批量删除前置校验整体不通过的场景， 例如权限不足或路径非法导致一个都未删除。
   *
   * @param errors 对象路径 → 失败原因映射，<strong>直接持有引用不做拷贝</strong>， 调用方后续修改会反映到本实例上；不允许为 {@code null}
   * @return 不含任何成功项的结果实例，不会为 {@code null}
   */
  public static BatchDeleteResult allFailed(Map<String, String> errors) {
    return new BatchDeleteResult(Collections.emptyList(), errors);
  }

  /** 是否有失败项 */
  public boolean hasFailures() {
    return !failedList.isEmpty();
  }

  /**
   * 成功删除的对象数量。
   *
   * @return 成功条数，无成功项时为 {@code 0}；等价于 {@code successList.size()}
   */
  public int successCount() {
    return successList.size();
  }

  /**
   * 删除失败的对象数量。
   *
   * @return 失败条数，无失败项时为 {@code 0}；等价于 {@code failedList.size()}
   */
  public int failureCount() {
    return failedList.size();
  }

  /**
   * 判断本次批量删除是否全部成功。
   *
   * <p>以「无失败项」为判据，而非「成功列表非空」，因此待删列表为空、实际删除 0 个对象时同样返回 {@code true}。
   *
   * @return 不存在任何失败项时返回 {@code true}，否则返回 {@code false}；与 {@link #hasFailures()} 恒为反值
   */
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
