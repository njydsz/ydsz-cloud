package com.njydsz.common.exception.batch;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * 批量业务异常 - 用于批量操作时收集成功/失败信息
 *
 * <p>在批量处理场景中，部分操作成功、部分操作失败时使用此类收集所有结果。
 * 可以选择在处理完所有项后，如果有失败项则抛出此异常。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * BatchBusinessException batch = BatchBusinessException.create();
 * for (OrderRequest request : requests) {
 *     try {
 *         orderService.create(request);
 *         batch.addSuccess(request.getOrderId());
 *     } catch (BusinessException e) {
 *         batch.addFailure(request.getOrderId(), e.getCode(), e.getMessage());
 *     }
 * }
 * if (batch.hasFailures()) {
 *     throw batch;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
public class BatchBusinessException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** i18n 消息键 */
  private static final String BATCH_PARTIAL_SUCCESS_KEY = "batch.partial.success";

  /** 成功的子项 ID 列表 */
  private final List<Object> successItems = new ArrayList<>(4);

  /** 失败的子项列表: ID -> [code, message] */
  private final Map<Object, String[]> failureItems = new LinkedHashMap<>(4);

  /** 私有构造器, 使用静态工厂方法创建实例 */
  private BatchBusinessException() {
    super(CoreExceptionCode.BATCH_PARTIAL_SUCCESS);
  }

  /**
   * 创建 BatchBusinessException 实例
   *
   * @return 新的实例
   */
  public static BatchBusinessException create() {
    return new BatchBusinessException();
  }

  /**
   * 添加成功的子项 ID
   *
   * @param itemId 成功项的 ID
   */
  public void addSuccess(Object itemId) {
    successItems.add(itemId);
  }

  /**
   * 添加失败的子项信息
   *
   * @param itemId 失败项的 ID
   * @param code 错误码
   * @param message 错误消息
   */
  public void addFailure(Object itemId, String code, String message) {
    failureItems.put(itemId, new String[]{code, message});
  }

  /**
   * 是否有失败项
   *
   * @return 如果有失败项返回 true
   */
  public boolean hasFailures() {
    return !failureItems.isEmpty();
  }

  /**
   * 是否全部成功（无失败项）
   *
   * @return 如果没有失败项返回 true
   */
  public boolean isAllSuccess() {
    return failureItems.isEmpty();
  }

  /**
   * 获取成功率百分比
   *
   * @return 成功率（0-100）
   */
  public double getSuccessRate() {
    int total = successItems.size() + failureItems.size();
    if (total == 0) {
      return 0;
    }
    return (double) successItems.size() / total * 100;
  }

  /**
   * 获取成功项列表（不可变）
   *
   * @return 成功项列表
   */
  public List<Object> getSuccessItems() {
    return Collections.unmodifiableList(successItems);
  }

  /**
   * 获取失败项列表（不可变）
   *
   * @return 失败项映射
   */
  public Map<Object, String[]> getFailureItems() {
    return Collections.unmodifiableMap(failureItems);
  }

  /**
   * 获取失败聚合信息 - 返回每个失败项的 ID、错误码和消息
   *
   * @return 失败聚合列表
   */
  public List<Map<String, Object>> getFailureAggregation() {
    List<Map<String, Object>> result = new ArrayList<>(failureItems.size());
    for (Map.Entry<Object, String[]> entry : failureItems.entrySet()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", entry.getKey());
      String[] value = entry.getValue();
      if (value != null && value.length >= 2) {
        item.put("code", value[0]);
        item.put("message", value[1]);
      }
      result.add(item);
    }
    return result;
  }

  /**
   * 获取成功项数量
   *
   * @return 成功项数量
   */
  public int getSuccessCount() {
    return successItems.size();
  }

  /**
   * 获取失败项数量
   *
   * @return 失败项数量
   */
  public int getFailureCount() {
    return failureItems.size();
  }

  /**
   * 获取总项数（成功数 + 失败数）
   *
   * @return 总项数
   */
  public int getTotalCount() {
    return successItems.size() + failureItems.size();
  }

  /**
   * 获取格式化的摘要消息
   *
   * @return 摘要消息
   */
  public String getSummary() {
    return MessageFormat.format(
        "Batch completed: {0} success, {1} failure, rate={2}%",
        successItems.size(), failureItems.size(), String.format("%.1f", getSuccessRate()));
  }
}
