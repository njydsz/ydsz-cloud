package com.njydsz.common.exception.batch;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.MessageSourceHolder;

import lombok.Getter;

/**
 * 批量操作异常（HTTP 207 Multi-Status）
 *
 * <p>用于批量创建/更新/删除等场景，部分成功部分失败时抛出。
 * 全局异常处理器将把此类异常映射为 HTTP 207 状态码，
 * 响应体包含每个子项的处理结果（成功/失败明细）。
 *
 * <p><b>使用示例：</b>
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
 * @since 2.3.0
 */
@Getter
public class BatchBusinessException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** i18n 消息键 */
    private static final String BATCH_PARTIAL_SUCCESS_KEY = "batch.partial.success";

    /** 成功的子项 ID 列表 */
    private final List<Object> successItems = new ArrayList<>();

    /** 失败的子项详情列表 */
    private final List<FailureItem> failureItems = new ArrayList<>();

    /**
     * 构造批量操作异常
     */
    private BatchBusinessException() {
        super(CoreExceptionCode.BATCH_PARTIAL_SUCCESS);
        setHttpStatus(207); // HTTP Multi-Status
    }

    /**
     * 创建一个批量操作异常实例
     *
     * @return 批量操作异常实例（可继续链式添加成功/失败项）
     */
    public static BatchBusinessException create() {
        return new BatchBusinessException();
    }

    /**
     * 动态生成包含当前成功/失败数量的 i18n 消息。
     *
     * <p>每次调用都基于当前 counts 重新生成消息，
     * 确保无论何时获取消息都能反映最新的批量处理结果。
     *
     * <p>使用 {@link MessageFormat} 处理 i18n 模板中的 {@code {0}} / {@code {1}} 占位符，
     * 与 Spring {@link org.springframework.context.MessageSource} 的消息格式保持一致。
     *
     * @return 处理结果
     */
    @Override
    public String getMessage() {
        String template = MessageSourceHolder.resolve(BATCH_PARTIAL_SUCCESS_KEY,
                new Object[]{getSuccessCount(), getFailureCount()});
        // 如果 i18n 未配置（返回 key 本身），使用默认文案
        if (BATCH_PARTIAL_SUCCESS_KEY.equals(template)) {
            return String.format("Batch operation partially successful: %d succeeded, %d failed",
                    getSuccessCount(), getFailureCount());
        }
        try {
            return MessageFormat.format(template, getSuccessCount(), getFailureCount());
        } catch (Exception e) {
            return template;
        }
    }

    /**
     * 添加成功的子项
     *
     * @param itemId 子项标识（如 ID、序号等）
     * @return this，支持链式调用
     */
    public BatchBusinessException addSuccess(Object itemId) {
        successItems.add(itemId);
        return this;
    }

    /**
     * 添加成功的子项集合
     *
     * @param itemIds 子项标识集合
     * @return this，支持链式调用
     */
    public BatchBusinessException addSuccesses(Collection<?> itemIds) {
        if (itemIds != null) {
            successItems.addAll(itemIds);
        }
        return this;
    }

    /**
     * 添加失败的子项
     *
     * @param itemId  子项标识
     * @param code    错误码
     * @param message 错误消息
     * @return this，支持链式调用
     */
    public BatchBusinessException addFailure(Object itemId, String code, String message) {
        failureItems.add(new FailureItem(itemId, code, message));
        return this;
    }

    /**
     * 添加失败的子项（基于已有的 BusinessException）
     *
     * @param itemId 子项标识
     * @param cause  业务异常原因
     * @return this，支持链式调用
     */
    public BatchBusinessException addFailure(Object itemId, BusinessException cause) {
        failureItems.add(new FailureItem(itemId, cause.getCode(), cause.getMessage()));
        return this;
    }

    /**
     * 判断是否存在失败子项
     *
     * @return 有失败子项返回 true
     */
    public boolean hasFailures() {
        return !failureItems.isEmpty();
    }

    /**
     * 判断是否全部成功
     *
     * @return 无失败子项返回 true
     */
    public boolean isAllSuccess() {
        return failureItems.isEmpty();
    }

    /**
     * 判断是否全部失败
     *
     * @return 无成功子项返回 true
     */
    public boolean isAllFailed() {
        return successItems.isEmpty() && !failureItems.isEmpty();
    }

    /**
     * 获取成功数量
     *
     * @return 成功子项数量
     */
    public int getSuccessCount() {
        return successItems.size();
    }

    /**
     * 获取失败数量
     *
     * @return 失败子项数量
     */
    public int getFailureCount() {
        return failureItems.size();
    }

    /**
     * 获取总数
     *
     * @return 子项总数
     */
    public int getTotalCount() {
        return successItems.size() + failureItems.size();
    }

    /**
     * 获取不可变的成功子项列表
     *
     * @return 成功子项列表
     */
    public List<Object> getSuccessItems() {
        return Collections.unmodifiableList(successItems);
    }

    /**
     * 获取不可变的失败子项列表
     *
     * @return 失败子项列表
     */
    public List<FailureItem> getFailureItems() {
        return Collections.unmodifiableList(failureItems);
    }

    /**
     * 按错误码聚合统计失败子项。
     *
     * <p>便于前端/调用方快速了解"哪种错误最多"，无需遍历全量 failureItems。
     * 返回的 map 保持错误码首次出现的顺序（LinkedHashMap），值为该错误码的出现次数。
     *
     * <p>响应序列化时可通过 {@code aggregation} 字段输出，结构如：
     * <pre>{@code
     * "aggregation": {
     *   "USER_NOT_FOUND": 3,
     *   "PARAM_INVALID": 1
     * }
     * }</pre>
     *
     * @return 错误码 → 出现次数的聚合 map
     */
    public Map<String, Integer> getFailureAggregation() {
        if (failureItems.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> aggregation = new LinkedHashMap<>();
        for (FailureItem item : failureItems) {
            aggregation.merge(item.getCode(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(aggregation);
    }

    /**
     * 按错误码分组失败子项（保留子项 ID 明细）。
     *
     * <p>与 {@link #getFailureAggregation()} 不同，此方法返回每个错误码对应的子项 ID 列表，
     * 供调用方精确定位哪些子项触发了同类错误。
     *
     * @return 错误码 → 子项 ID 列表的分组 map
     */
    public Map<String, List<Object>> getFailureGroupByCode() {
        if (failureItems.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<Object>> group = new LinkedHashMap<>();
        for (FailureItem item : failureItems) {
            group.computeIfAbsent(item.getCode(), k -> new ArrayList<>()).add(item.getItemId());
        }
        return Collections.unmodifiableMap(group);
    }

    /**
     * 失败子项详情
     */
    @Getter
    public static class FailureItem {
        /** 子项标识 */
        private final Object itemId;
        /** 错误码 */
        private final String code;
        /** 错误消息 */
        private final String message;

        public FailureItem(Object itemId, String code, String message) {
            this.itemId = itemId;
            this.code = code;
            this.message = message;
        }
    }
}
