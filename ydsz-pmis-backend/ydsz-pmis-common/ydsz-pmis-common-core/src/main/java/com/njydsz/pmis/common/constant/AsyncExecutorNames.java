package com.njydsz.pmis.common.constant;

/**
 * 异步线程池 Bean 名称常量
 *
 * <p>与 {@code AsyncThreadPoolConfig} 中定义的三个定制线程池一一对应，
 * 供 {@code @Async} 注解显式指定 executor，避免所有异步任务挤入默认的
 * {@code applicationTaskExecutor}（核心 8 / 队列 Integer.MAX_VALUE）导致 OOM。
 *
 * <ul>
 *   <li>{@link #AUDIT} - 审计日志线程池（核心 2 / 最大 4 / 队列 500）</li>
 *   <li>{@link #EXPORT} - 数据导出线程池（核心 1 / 最大 2 / 队列 10）</li>
 *   <li>{@link #AGENT} - AI Agent 调用线程池（核心 2 / 最大 8 / 队列 100）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AsyncExecutorNames {

    /** 审计日志线程池 Bean 名称 */
    String AUDIT = "auditExecutor";

    /** 数据导出线程池 Bean 名称 */
    String EXPORT = "exportExecutor";

    /** AI Agent 调用线程池 Bean 名称 */
    String AGENT = "agentExecutor";
}
