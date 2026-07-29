package com.njydsz.common.domain.job;

/**
 * 任务上下文持有者（ThreadLocal）
 *
 * <p>使用 {@link InheritableThreadLocal} 替代普通 ThreadLocal，
 * 使子线程能自动继承父线程的任务上下文。
 *
 * <p><b>注意：</b>对于线程池场景（线程复用），InheritableThreadLocal 仅在线程首次创建时
 * 继承父线程的值，不会在线程复用时更新。如果需要在线程池中正确传播上下文，
 * 请使用 TaskDecorator 或在提交任务前显式调用 {@link #set(ShardingContext)} / {@link #clear()}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JobContextHolder {

    private static final InheritableThreadLocal<ShardingContext> CONTEXT = new InheritableThreadLocal<>();

    private JobContextHolder() {
    }

    /**
     * 设置当前线程的分片上下文
     *
     * @param ctx 分片上下文
     */
    public static void set(ShardingContext ctx) {
        CONTEXT.set(ctx);
    }

    /**
     * 获取当前线程的分片上下文
     *
     * @return 分片上下文（可能为 null）
     */
    public static ShardingContext get() {
        return CONTEXT.get();
    }

    /**
     * 清除当前线程的分片上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
