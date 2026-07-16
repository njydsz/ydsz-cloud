package com.njydsz.common.file.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批量删除结果
 *
 * <p>包含成功删除的对象路径列表和失败的对象路径列表（含失败原因）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public record BatchDeleteResult(
        List<String> successList,
        Map<String, String> failedList
) {

    /**
     * 全部成功的便捷构造方法
     */
    public static BatchDeleteResult allSuccess(List<String> deletedPaths) {
        return new BatchDeleteResult(
                List.copyOf(deletedPaths),
                Collections.emptyMap()
        );
    }

    /**
     * 全部失败的便捷构造方法
     */
    public static BatchDeleteResult allFailed(Map<String, String> errors) {
        return new BatchDeleteResult(
                Collections.emptyList(),
                errors
        );
    }

    /**
     * 是否有失败项
     */
    public boolean hasFailures() {
        return !failedList.isEmpty();
    }

    /**
     * 成功删除的数量
     */
    public int successCount() {
        return successList.size();
    }

    /**
     * 失败删除的数量
     */
    public int failureCount() {
        return failedList.size();
    }

    /**
     * 是否全部成功
     */
    public boolean allSuccess() {
        return failedList.isEmpty();
    }

    /**
     * 获取失败摘要信息
     */
    public String getFailureSummary() {
        if (failedList.isEmpty()) {
            return "全部成功";
        }
        return failedList.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));
    }
}
