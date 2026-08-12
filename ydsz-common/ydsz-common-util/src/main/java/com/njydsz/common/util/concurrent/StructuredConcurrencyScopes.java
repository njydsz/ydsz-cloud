package com.njydsz.common.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.List;
import java.util.ArrayList;

/**
 * JDK 21 结构化并发工具——解决多子任务并行编排问题。
 *
 * <p>传统方案（{@code CompletableFuture.allOf}）的局限：
 * <ul>
 *   <li>不能自动取消兄弟任务（一个失败后其他仍继续执行）</li>
 *   <li>作用域泄露（任务引用逃逸到方法外）</li>
 *   <li>子任务异常难以溯源（{@code allOf} 只抛第一个异常）</li>
 * </ul>
 *
 * <p><b>结构化并发保证：</b>所有子任务在作用域内完成，作用域退出前强制 join + 取消未完成的任务。
 *
 * <p><b>预测未来场景：</b>
 * <ul>
 *   <li>并行查询多个数据源（任一失败则取消其他）</li>
 *   <li>竞速调用多个 AI Agent（首个成功即返回）</li>
 *   <li>批量 RPC 拆分子任务汇总</li>
 *   <li>并行文件下载 / 并行 HTTP 请求</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 场景 1: 所有子任务必须成功（任一失败则取消其他未完成的任务）
 *   try (var scope = StructuredConcurrencyScopes.allSuccess()) {
 *     Subtask&lt;User&gt; userTask = scope.fork(() -> userService.getById(id));
 *     Subtask&lt;List&lt;Order&gt;&gt; orderTask = scope.fork(() -> orderService.getByUserId(id));
 *     scope.join();          // 等待所有任务完成或失败
 *     scope.throwIfFailed(); // 任一失败时抛出异常
 *     return new UserProfile(userTask.get(), orderTask.get());
 *   }
 *
 *   // 场景 2: 首个成功即返回（竞赛模式）
 *   try (var scope = StructuredConcurrencyScopes.firstSuccess()) {
 *     for (LlmProvider provider : providers) {
 *       scope.fork(() -> provider.ask(question));
 *     }
 *     Subtask&lt;?&gt; winner = scope.join();
 *     return winner.get();
 *   }
 *
 *   // 场景 3: 全部收集（不取消，收集所有结果和异常）
 *   try (var scope = StructuredConcurrencyScopes.allSuccess()) {
 *     tasks.forEach(t -> scope.fork(t));
 *     StructuredTaskScope.Subtask&lt;?&gt;[] subtasks = scope.join().toList().toArray(new Subtask[0]);
 *     // 逐个检查每个子任务状态
 *     for (Subtask&lt;?&gt; st : subtasks) {
 *       if (st.state() == Subtask.State.SUCCESS) processResult(st.get());
 *       else handleFailure(st.exception());
 *     }
 *   }
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.0.0（依赖 JDK 21+）
 * @see java.util.concurrent.StructuredTaskScope
 */
public final class StructuredConcurrencyScopes {

    private StructuredConcurrencyScopes() {
        throw new UnsupportedOperationException("StructuredConcurrencyScopes is a utility class");
    }

    // ==================== 工厂方法 ====================

    /**
     * "所有子任务必须成功"模式——任一失败则取消其他未完成任务。
     *
     * <p>适用场景：多数据源查询、并行校验（如库存校验 + 优惠券校验 + 地址校验）。
     *
     * @return ShutdownOnFailure scope
     */
    public static StructuredTaskScope.ShutdownOnFailure allSuccess() {
        return new StructuredTaskScope.ShutdownOnFailure();
    }

    /**
     * "首个成功即返回"模式——首个完成任务后取消其他待执行任务。
     *
     * <p>适用场景：多 LLM 竞速调用、多 CDN 竞速下载、多副本读取。
     *
     * @param <T> 结果类型
     * @return ShutdownOnSuccess scope
     */
    @SuppressWarnings("unchecked")
    public static <T> StructuredTaskScope.ShutdownOnSuccess<T> firstSuccess() {
        return new StructuredTaskScope.ShutdownOnSuccess<>();
    }

    // ==================== 便捷组合方法 ====================

    /**
     * 并行执行所有任务并收集结果（不取消，异常包装在返回值中）。
     *
     * <p>返回每个任务的执行结果：成功时 {@link TaskResult#isSuccess()}=true，失败时包含异常信息。
     *
     * @param tasks 要并行执行的任务列表
     * @return 每个任务的结果列表（与输入顺序一致）
     * @throws InterruptedException 等待期间被中断时
     */
    public static <T> List<TaskResult<T>> allOf(List<Callable<T>> tasks) throws InterruptedException {
        try (var scope = allSuccess()) {
            List<Subtask<T>> subtasks = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(task));
            }
            scope.join();
            scope.throwIfFailed();

            List<TaskResult<T>> results = new ArrayList<>(subtasks.size());
            for (Subtask<T> st : subtasks) {
                results.add(new TaskResult<>(st.get(), null, st.state() == Subtask.State.SUCCESS));
            }
            return results;
        }
    }

    /**
     * 竞速执行任务，首个成功即返回，其他任务自动取消。
     *
     * @param tasks 竞速任务列表
     * @return 首个成功任务的结果
     * @throws StructuredExecutionException 所有任务都失败时
     */
    public static <T> T firstSuccessOf(List<Callable<T>> tasks) throws InterruptedException, StructuredExecutionException {
        try (var scope = firstSuccess()) {
            for (Callable<T> task : tasks) {
                scope.fork(task);
            }
            try {
                Subtask<T> winner = scope.join();
                return winner.get();
            } catch (IllegalStateException e) {
                throw new StructuredExecutionException("All racing tasks failed", e);
            }
        }
    }

    /**
     * 带超时的并行执行。
     *
     * @param tasks 任务列表
     * @param timeout 超时时长
     * @param unit 时间单位
     * @return 每个任务的结果（超时未完成的任务状态为 FAILED）
     * @throws InterruptedException 等待期间被中断时
     */
    public static <T> List<TaskResult<T>> allOfWithTimeout(List<Callable<T>> tasks,
                                                           long timeout,
                                                           java.util.concurrent.TimeUnit unit) throws InterruptedException {
        try (var scope = allSuccess()) {
            List<Subtask<T>> subtasks = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(task));
            }
            scope.joinUntil(java.time.Instant.now().plus(timeout, unit.toChronoUnit()));

            List<TaskResult<T>> results = new ArrayList<>(subtasks.size());
            for (Subtask<T> st : subtasks) {
                if (st.state() == Subtask.State.SUCCESS) {
                    results.add(new TaskResult<>(st.get(), null, true));
                } else {
                    results.add(new TaskResult<>(null,
                            st.state() == Subtask.State.UNSUBMITTED
                                    ? new InterruptedException("Task did not complete in time")
                                    : st.exception(),
                            false));
                }
            }
            return results;
        }
    }

    // ==================== 结果包装 ====================

    /**
     * 任务执行结果包装——明确区分成功/失败，避免 null 语义混淆。
 *
     * @param <T> 结果类型
     */
    public static final class TaskResult<T> {
        private final T value;
        private final Throwable error;
        private final boolean success;

        TaskResult(T value, Throwable error, boolean success) {
            this.value = value;
            this.error = error;
            this.success = success;
        }

        public T getValue() { return value; }
        public Throwable getError() { return error; }
        public boolean isSuccess() { return success; }

        /**
         * 获取值或抛出异常。
         */
        public T orElseThrow() {
            if (success) return value;
            throw new RuntimeException("Task failed", error);
        }

        /**
         * 获取值或返回默认值。
         */
        public T orElse(T defaultValue) {
            return success ? value : defaultValue;
        }
    }

    // ==================== 自定义异常 ====================

    /**
     * 结构化并发执行异常——所有子任务都失败时抛出。
     */
    public static class StructuredExecutionException extends RuntimeException {
        public StructuredExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
