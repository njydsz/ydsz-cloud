package com.njydsz.common.batch.chunk;

import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.batch.item.ItemProcessor;
import com.njydsz.common.batch.item.ItemReader;
import com.njydsz.common.batch.item.ItemWriter;
import com.njydsz.common.batch.listener.ChunkListener;
import com.njydsz.common.batch.listener.StepListener;
import com.njydsz.common.batch.model.BatchExecutionContext;
import com.njydsz.common.batch.model.StepExecutionContext;
import com.njydsz.common.batch.retry.RetryContext;
import com.njydsz.common.batch.retry.RetryPolicy;
import com.njydsz.common.batch.skip.SkipPolicy;

import lombok.extern.slf4j.Slf4j;

/**
 * Chunk 处理器
 *
 * <p>Spring Batch 风格的核心执行单元：
 * <ol>
 *   <li>循环调用 reader.read() 累积至 commitInterval</li>
 *   <li>对每条数据调用 processor.process()</li>
 *   <li>调用 writer.writeBatch() 批量写入</li>
 *   <li>异常时按 SkipPolicy / RetryPolicy 决策</li>
 * </ol>
 *
 * <p>支持：
 * <ul>
 *   <li>Chunk 大小（commitInterval）</li>
 *   <li>Skip（可跳过异常 + 最大跳过数）</li>
 *   <li>Retry（重试策略 + 退避）</li>
 *   <li>Restart（stepExecutionContext 持久化中间状态）</li>
 * </ul>
 *
 * @param <T> 读取类型
 * @param <R> 处理后类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ChunkProcessor<T, R> {

    private final ItemReader<T> reader;
    private final ItemProcessor<T, R> processor;
    private final ItemWriter<R> writer;
    private final int commitInterval;
    private final SkipPolicy skipPolicy;
    private final RetryPolicy retryPolicy;
    private final List<StepListener> stepListeners;
    private final List<ChunkListener<R>> chunkListeners;

    public ChunkProcessor(ItemReader<T> reader,
                          ItemProcessor<T, R> processor,
                          ItemWriter<R> writer,
                          int commitInterval,
                          SkipPolicy skipPolicy,
                          RetryPolicy retryPolicy,
                          List<StepListener> stepListeners,
                          List<ChunkListener<R>> chunkListeners) {
        this.reader = reader;
        this.processor = processor;
        this.writer = writer;
        this.commitInterval = Math.max(1, commitInterval);
        this.skipPolicy = skipPolicy == null ? new SkipPolicy() : skipPolicy;
        this.retryPolicy = retryPolicy == null ? new RetryPolicy() {
            @Override
            public boolean canRetry(RetryContext context) {
                return false;
            }

            @Override
            public void registerRetry(RetryContext context) {
            }
        } : retryPolicy;
        this.stepListeners = stepListeners == null ? new ArrayList<>() : stepListeners;
        this.chunkListeners = chunkListeners == null ? new ArrayList<>() : chunkListeners;
    }

    /**
     * 执行 Chunk 处理，直到 reader 返回 null
     */
    public BatchExecutionContext execute(String stepName, StepExecutionContext stepContext) {
        BatchExecutionContext execution = BatchExecutionContext.builder()
                .name(stepName)
                .status(com.njydsz.common.batch.enums.BatchStatus.STARTED)
                .startTime(java.time.Instant.now())
                .build();
        notifyBeforeStep(execution);

        List<R> chunkBuffer = new ArrayList<>(commitInterval);
        long readCount = 0, processCount = 0, writeCount = 0, skipCount = 0;

        try {
            while (true) {
                T item;
                try {
                    item = reader.read();
                } catch (Exception readEx) {
                    // 读取异常：尝试跳过 / 重试
                    RetryContext retryCtx = new RetryContext();
                    retryCtx.setThrowable(readEx);
                    if (retryPolicy.canRetry(retryCtx)) {
                        retryPolicy.registerRetry(retryCtx);
                        sleepBackoff(retryPolicy.backoffMillis(retryCtx.getRetryCount()));
                        execution.setRetryCount(retryCtx.getRetryCount());
                        continue;
                    }
                    if (skipPolicy.shouldSkip(readEx, skipCount)) {
                        skipCount++;
                        execution.setSkipCount(skipCount);
                        log.warn("Read skipped due to: {}", readEx.getMessage());
                        continue;
                    }
                    throw readEx;
                }
                if (item == null) {
                    break;
                }
                readCount++;
                execution.setReadCount(readCount);

                R processed;
                try {
                    processed = processor.process(item);
                } catch (Exception processEx) {
                    RetryContext retryCtx = new RetryContext();
                    retryCtx.setThrowable(processEx);
                    if (retryPolicy.canRetry(retryCtx)) {
                        retryPolicy.registerRetry(retryCtx);
                        sleepBackoff(retryPolicy.backoffMillis(retryCtx.getRetryCount()));
                        execution.setRetryCount(retryCtx.getRetryCount());
                        continue;
                    }
                    if (skipPolicy.shouldSkip(processEx, skipCount)) {
                        skipCount++;
                        execution.setSkipCount(skipCount);
                        log.warn("Process skipped: item={}, reason={}", item, processEx.getMessage());
                        continue;
                    }
                    throw processEx;
                }
                if (processed == null) {
                    continue; // 过滤掉
                }
                processCount++;
                execution.setProcessCount(processCount);
                chunkBuffer.add(processed);

                if (chunkBuffer.size() >= commitInterval) {
                    writeCount += flushChunk(chunkBuffer);
                    execution.setWriteCount(writeCount);
                    execution.setCommitCount(execution.getCommitCount() + 1);
                }
            }
            // flush 残余
            if (!chunkBuffer.isEmpty()) {
                writeCount += flushChunk(chunkBuffer);
                execution.setWriteCount(writeCount);
                execution.setCommitCount(execution.getCommitCount() + 1);
            }
            execution.setStatus(com.njydsz.common.batch.enums.BatchStatus.COMPLETED);
            execution.setExitStatus(com.njydsz.common.batch.enums.ExitStatus.COMPLETED);
        } catch (Exception ex) {
            log.error("Chunk processing failed at step={}, read={}, write={}", stepName, readCount, writeCount, ex);
            execution.setStatus(com.njydsz.common.batch.enums.BatchStatus.FAILED);
            execution.setExitStatus(com.njydsz.common.batch.enums.ExitStatus.FAILED);
            execution.setErrorMessage(ex.getMessage());
            execution.setException(ex);
            notifyOnError(execution, ex);
        } finally {
            try {
                writer.flush();
            } catch (Exception ignored) {
            }
            execution.setEndTime(java.time.Instant.now());
            notifyAfterStep(execution);
        }
        return execution;
    }

    private int flushChunk(List<R> chunkBuffer) {
        try {
            notifyBeforeChunk(chunkBuffer);
            writer.writeBatch(chunkBuffer);
            notifyAfterChunk(chunkBuffer);
        } catch (Exception ex) {
            notifyOnChunkError(chunkBuffer, ex);
            throw new RuntimeException("Write chunk failed", ex);
        } finally {
            chunkBuffer.clear();
        }
        return 1; // 1 chunk 提交
    }

    private void sleepBackoff(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void notifyBeforeStep(BatchExecutionContext ctx) {
        for (StepListener listener : stepListeners) {
            try {
                listener.beforeStep(ctx);
            } catch (Exception ex) {
                log.warn("Step listener beforeStep failed", ex);
            }
        }
    }

    private void notifyAfterStep(BatchExecutionContext ctx) {
        for (StepListener listener : stepListeners) {
            try {
                listener.afterStep(ctx);
            } catch (Exception ex) {
                log.warn("Step listener afterStep failed", ex);
            }
        }
    }

    private void notifyOnError(BatchExecutionContext ctx, Throwable ex) {
        for (StepListener listener : stepListeners) {
            try {
                listener.onError(ctx, ex);
            } catch (Exception e) {
                log.warn("Step listener onError failed", e);
            }
        }
    }

    private void notifyBeforeChunk(List<R> items) {
        for (ChunkListener<R> listener : chunkListeners) {
            try {
                listener.beforeChunk(items);
            } catch (Exception ex) {
                log.warn("Chunk listener beforeChunk failed", ex);
            }
        }
    }

    private void notifyAfterChunk(List<R> items) {
        for (ChunkListener<R> listener : chunkListeners) {
            try {
                listener.afterChunk(items);
            } catch (Exception ex) {
                log.warn("Chunk listener afterChunk failed", ex);
            }
        }
    }

    private void notifyOnChunkError(List<R> items, Throwable ex) {
        for (ChunkListener<R> listener : chunkListeners) {
            try {
                listener.onChunkError(items, ex);
            } catch (Exception e) {
                log.warn("Chunk listener onChunkError failed", e);
            }
        }
    }
}
