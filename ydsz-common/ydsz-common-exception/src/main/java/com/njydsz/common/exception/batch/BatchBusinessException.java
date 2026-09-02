package com.njydsz.common.exception.batch.BatchBusinessException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.MessageSourceHolder;

/**
 * 批量操作异常（HTTP 207 Multi-Status）
 *
 * <p>用于批量创建/更新/删除等场景，部分成功部分失败时抛出。 全局异常处理器将把此类异常映射为 HTTP 207 状态码， 响应体包含每个子项的处理结果（成功/失败明细）。
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