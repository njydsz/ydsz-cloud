package com.njydsz.common.core.dag;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DAG 实例状态枚举。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DagInstanceStatus {

    /** 待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 已暂停 */
    PAUSED,

    /** 执行成功 */
    SUCCESS,

    /** 执行失败 */
    FAILED,

    /** 部分成功 */
    PARTIAL_SUCCESS,

    /** 已取消 */
    CANCELED;

    private static final Map<String, DagInstanceStatus> CACHE = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));

    /**
     * 根据名称解析状态。
     *
     * @param name 状态名称
     * @return 状态枚举，未找到时返回 null
     */
    public static DagInstanceStatus parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return CACHE.get(name);
    }

    /**
     * 是否为终态。
     *
     * @return true 表示终态
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == PARTIAL_SUCCESS || this == CANCELED;
    }
}
