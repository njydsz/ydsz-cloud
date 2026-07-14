package com.njydsz.pmis.common.core.context;

import org.springframework.core.task.TaskDecorator;

/**
 * TTL 异步任务装饰器
 *
 * <p>自动包装 Spring {@code @Async} 和 {@code @Scheduled} 任务，
 * 确保 {@code TransmittableThreadLocal} 上下文在异步线程间正确传播。
 *
 * <p>注册为 Bean 后，Spring 会自动将其应用到所有 {@code TaskExecutor}：
 * <ul>
 *   <li>{@code @Async} 方法使用的线程池</li>
 *   <li>{@code @Scheduled} 定时任务线程池</li>
 *   <li>Spring MVC 异步请求处理线程池</li>
 * </ul>
 *
 * <p>注意：此装饰器仅对 Spring 管理的线程池生效。
 * 手动创建的 {@code CompletableFuture.supplyAsync()} 需使用
 * {@link RequestContextExecutor} 提供的线程池。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class TtlTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // TTL 通过 TransmittableThreadLocal 自动 capture/restore，
        // 这里仅确保 Runnable 被正确包装。
        // 如果 TTL agent 已启用（-javaagent），则无需额外操作。
        // 如果未启用 agent，则使用 TtlRunnable 手动包装。
        return com.alibaba.ttl.TtlRunnable.get(runnable);
    }
}
