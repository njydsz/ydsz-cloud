package com.njydsz.common.seata.api;

import java.util.concurrent.Callable;

/**
 * SAGA 事务步骤
 *
 * <p>定义 SAGA 模式中的一个正向操作及其对应的补偿操作。
 *
 * <p><b>P2-6 新增</b>：增加 {@code timeoutMs} 属性，支持步骤级超时设置。
 * 当正向操作执行超过指定时间未完成时，编排器将中断该步骤并触发补偿。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 步骤级超时 30 秒
 * SagaStep&lt;String&gt; step = SagaStep.of("freeze-inventory", action, compensation, 30000);
 *
 * // 使用 Builder 模式构建复杂步骤
 * SagaStep&lt;OrderResult&gt; step = SagaStep.&lt;OrderResult&gt;builder("create-order")
 *     .forwardAction(() -&gt; orderService.create(dto))
 *     .compensation(() -&gt; orderService.cancel(dto.getId()))
 *     .timeoutMs(60000)  // 60 秒超时
 *     .build();
 * }</pre>
 *
 * @param <T> 正向操作返回值类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SagaStep<T> {

    /** 默认超时时间（毫秒）：5 分钟 */
    public static final long DEFAULT_TIMEOUT_MS = 300000;

    private final String name;
    private final Callable<T> forwardAction;
    private final Runnable compensation;
    private final long timeoutMs;

    private SagaStep(String name, Callable<T> forwardAction, Runnable compensation, long timeoutMs) {
        this.name = name;
        this.forwardAction = forwardAction;
        this.compensation = compensation;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 创建一个带补偿的 SAGA 步骤（使用默认超时）
     *
     * @param name          步骤名称
     * @param forwardAction 正向操作
     * @param compensation  补偿操作
     * @param <T>           返回值类型
     * @return SAGA 步骤
     */
    public static <T> SagaStep<T> of(String name, Callable<T> forwardAction, Runnable compensation) {
        return new SagaStep<>(name, forwardAction, compensation, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 创建一个带补偿和自定义超时的 SAGA 步骤（P2-6 新增）
     *
     * @param name          步骤名称
     * @param forwardAction 正向操作
     * @param compensation  补偿操作
     * @param timeoutMs     超时时间（毫秒），0 表示不限制
     * @param <T>           返回值类型
     * @return SAGA 步骤
     */
    public static <T> SagaStep<T> of(String name, Callable<T> forwardAction, Runnable compensation, long timeoutMs) {
        return new SagaStep<>(name, forwardAction, compensation, Math.max(0, timeoutMs));
    }

    /**
     * 创建一个不带补偿的 SAGA 步骤（最后一步或不可逆操作，使用默认超时）
     *
     * @param name          步骤名称
     * @param forwardAction 正向操作
     * @param <T>           返回值类型
     * @return SAGA 步骤
     */
    public static <T> SagaStep<T> terminal(String name, Callable<T> forwardAction) {
        return new SagaStep<>(name, forwardAction, null, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 创建一个不带补偿但带自定义超时的 SAGA 步骤（P2-6 新增）
     *
     * @param name          步骤名称
     * @param forwardAction 正向操作
     * @param timeoutMs     超时时间（毫秒），0 表示不限制
     * @param <T>           返回值类型
     * @return SAGA 步骤
     */
    public static <T> SagaStep<T> terminal(String name, Callable<T> forwardAction, long timeoutMs) {
        return new SagaStep<>(name, forwardAction, null, Math.max(0, timeoutMs));
    }

    /**
     * 创建 SagaStep Builder（P2-6 新增）
     *
     * @param name 步骤名称
     * @param <T>  返回值类型
     * @return Builder 实例
     */
    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    /**
     * 获取步骤名称
     *
     * @return 步骤名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取正向操作
     *
     * @return 正向操作 Callable
     */
    public Callable<T> getForwardAction() {
        return forwardAction;
    }

    /**
     * 获取补偿操作
     *
     * @return 补偿操作 Runnable，无补偿时返回 null
     */
    public Runnable getCompensation() {
        return compensation;
    }

    /**
     * 判断是否有补偿操作
     *
     * @return 有补偿返回 true，否则返回 false
     */
    public boolean hasCompensation() {
        return compensation != null;
    }

    /**
     * 获取步骤超时时间（毫秒）
     *
     * <p>返回 0 表示不限制超时。
     *
     * @return 超时时间（毫秒）
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * 判断步骤是否设置了超时
     *
     * @return 已设置超时返回 true，否则返回 false
     */
    public boolean hasTimeout() {
        return timeoutMs > 0;
    }

    /**
     * SagaStep Builder（P2-6 新增）
     *
     * <p>提供流式 API 构建 SagaStep，适用于复杂构造场景。
     */
    public static class Builder<T> {
        private final String name;
        private Callable<T> forwardAction;
        private Runnable compensation;
        private long timeoutMs = DEFAULT_TIMEOUT_MS;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * 设置正向操作（必填）
         *
         * @param forwardAction 正向操作
         * @return this
         */
        public Builder<T> forwardAction(Callable<T> forwardAction) {
            this.forwardAction = forwardAction;
            return this;
        }

        /**
         * 设置补偿操作（可选）
         *
         * @param compensation 补偿操作
         * @return this
         */
        public Builder<T> compensation(Runnable compensation) {
            this.compensation = compensation;
            return this;
        }

        /**
         * 设置超时时间（毫秒）
         *
         * @param timeoutMs 超时时间，0 表示不限制
         * @return this
         */
        public Builder<T> timeoutMs(long timeoutMs) {
            this.timeoutMs = Math.max(0, timeoutMs);
            return this;
        }

        /**
         * 构建 SagaStep
         *
         * @return SagaStep 实例
         * @throws IllegalStateException 未设置 forwardAction 时抛出
         */
        public SagaStep<T> build() {
            if (forwardAction == null) {
                throw new IllegalStateException("forwardAction must not be null for step: " + name);
            }
            return new SagaStep<>(name, forwardAction, compensation, timeoutMs);
        }
    }
}
