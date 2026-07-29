package com.njydsz.common.domain.job;

/**
 * 任务上下文持有者（ThreadLocal）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JobContextHolder {

    private static final ThreadLocal<ShardingContext> CONTEXT = new ThreadLocal<>();

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
