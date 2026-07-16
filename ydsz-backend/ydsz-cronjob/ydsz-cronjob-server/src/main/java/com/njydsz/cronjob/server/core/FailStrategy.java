package com.njydsz.cronjob.server.core.dag;

import com.njydsz.common.core.dag.DagFailureStrategy;

/**
 * 失败传播策略枚举（P0-1 架构优化：委托到 common.DagFailureStrategy）。
 *
 * <p>保留 cronjob 模块特有的枚举名（FAIL_FAST / CONTINUE_ON_FAIL）以兼容现有代码，
 * 内部映射到 {@link DagFailureStrategy} 统一枚举。
 *
 * <h3>策略说明</h3>
 * <ul>
 *   <li>{@link #FAIL_FAST} → {@link DagFailureStrategy#ABORT}</li>
 *   <li>{@link #CONTINUE_ON_FAIL} → {@link DagFailureStrategy#CONTINUE}</li>
 *   <li>{@link #RETRY} → {@link DagFailureStrategy#RETRY}</li>
 *   <li>{@link #SKIP_SUBSEQUENT} → {@link DagFailureStrategy#SKIP_SUBSEQUENT}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 请直接使用 {@link DagFailureStrategy}，本枚举将在下一个大版本移除。
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public enum FailStrategy {

    /** 前置失败则不触发后继，并标记所有未完成节点为 SKIPPED（默认，关键链路） */
    FAIL_FAST,

    /** 前置失败仍触发后继（适用于通知/清理类后继） */
    CONTINUE_ON_FAIL,

    /** 节点失败时自动重试 */
    RETRY,

    /** 节点失败时跳过该节点的所有直接后继 */
    SKIP_SUBSEQUENT;

    /**
     * 解析策略字符串，大小写不敏感；无效值返回 {@link #FAIL_FAST}。
     */
    public static FailStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return FAIL_FAST;
        }
        try {
            return FailStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FAIL_FAST;
        }
    }

    /**
     * 判断边级策略下是否应触发后继。
     */
    public boolean shouldTriggerOnFailure() {
        return this == CONTINUE_ON_FAIL;
    }

    /**
     * 转换为统一的 {@link DagFailureStrategy}。
     *
     * @return 对应的统一策略枚举
     */
    public DagFailureStrategy toCommon() {
        return switch (this) {
            case FAIL_FAST -> DagFailureStrategy.ABORT;
            case CONTINUE_ON_FAIL -> DagFailureStrategy.CONTINUE;
            case RETRY -> DagFailureStrategy.RETRY;
            case SKIP_SUBSEQUENT -> DagFailureStrategy.SKIP_SUBSEQUENT;
        };
    }
}
