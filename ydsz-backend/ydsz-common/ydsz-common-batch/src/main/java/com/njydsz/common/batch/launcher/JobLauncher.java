package com.njydsz.common.batch.launcher;

import com.njydsz.common.batch.job.Job;
import com.njydsz.common.batch.model.BatchExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

/**
 * 作业启动器
 *
 * <p>负责启动 Job，支持同步 / 异步执行，并跟踪执行历史。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobLauncher {

    private final ExecutorService executor;
    private final ConcurrentMap<String, BatchExecutionContext> executions = new ConcurrentHashMap<>();

    public JobLauncher() {
        this(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    }

    public JobLauncher(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * 同步执行
     */
    public BatchExecutionContext run(Job job) {
        log.info("Launching job synchronously: {}", job.getName());
        BatchExecutionContext execution = job.execute();
        executions.put(execution.getJobInstanceId(), execution);
        return execution;
    }

    /**
     * 异步执行
     */
    public CompletableFuture<BatchExecutionContext> runAsync(Job job) {
        log.info("Launching job asynchronously: {}", job.getName());
        return CompletableFuture.supplyAsync(() -> {
            BatchExecutionContext execution = job.execute();
            executions.put(execution.getJobInstanceId(), execution);
            return execution;
        }, executor);
    }

    /**
     * 获取历史执行记录
     */
    public BatchExecutionContext getExecution(String jobInstanceId) {
        return executions.get(jobInstanceId);
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        executor.shutdown();
    }
}
