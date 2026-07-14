package com.njydsz.pmis.common.core.retry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncRetryTemplate {
    private static final Logger log = LoggerFactory.getLogger(AsyncRetryTemplate.class);
    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Executor executor;

    public AsyncRetryTemplate(int maxRetries, long initialDelayMs, double backoffMultiplier, Executor executor) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.executor = executor != null ? executor : ForkJoinPool.commonPool();
    }

    public <T> CompletableFuture<T> executeAsync(String name, Supplier<CompletableFuture<T>> action) {
        return executeAsync(name, action, 0);
    }
    private <T> CompletableFuture<T> executeAsync(String name, Supplier<CompletableFuture<T>> action, int attempt) {
        CompletableFuture<T> future = new CompletableFuture<>();
        action.get().whenComplete((result, ex) -> {
            if (ex == null) { future.complete(result); }
            else if (attempt >= maxRetries) { future.completeExceptionally(ex); }
            else {
                long delay = (long)(initialDelayMs * Math.pow(backoffMultiplier, attempt));
                scheduler.schedule(() -> executeAsync(name, action, attempt + 1).whenComplete((r, e) -> {
                    if (e == null) future.complete(r); else future.completeExceptionally(e);
                }), delay, TimeUnit.MILLISECONDS);
                log.warn("Async retry {} attempt {}/{} after {}ms", name, attempt + 1, maxRetries, delay);
            }
        });
        return future;
    }
    public void shutdown() { scheduler.shutdown(); }
}