package com.njydsz.common.batch.step;

import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.batch.chunk.ChunkProcessor;
import com.njydsz.common.batch.item.ItemProcessor;
import com.njydsz.common.batch.item.ItemReader;
import com.njydsz.common.batch.item.ItemWriter;
import com.njydsz.common.batch.listener.ChunkListener;
import com.njydsz.common.batch.listener.StepListener;
import com.njydsz.common.batch.model.BatchExecutionContext;
import com.njydsz.common.batch.model.StepExecutionContext;
import com.njydsz.common.batch.retry.RetryPolicy;
import com.njydsz.common.batch.skip.SkipPolicy;

import lombok.Getter;

/**
 * 批处理步骤（Step）
 *
 * <p>一个 Step 由 reader / processor / writer 三个组件 + chunk / skip / retry 等策略构成。
 *
 * @param <T> 读取类型
 * @param <R> 写出类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class Step<T, R> {

    private final String name;
    private final ItemReader<T> reader;
    private final ItemProcessor<T, R> processor;
    private final ItemWriter<R> writer;
    private final int commitInterval;
    private final SkipPolicy skipPolicy;
    private final RetryPolicy retryPolicy;
    private final List<StepListener> stepListeners = new ArrayList<>();
    private final List<ChunkListener<R>> chunkListeners = new ArrayList<>();

    private Step(Builder<T, R> b) {
        this.name = b.name;
        this.reader = b.reader;
        this.processor = b.processor;
        this.writer = b.writer;
        this.commitInterval = b.commitInterval;
        this.skipPolicy = b.skipPolicy;
        this.retryPolicy = b.retryPolicy;
        if (b.stepListeners != null) {
            this.stepListeners.addAll(b.stepListeners);
        }
        if (b.chunkListeners != null) {
            this.chunkListeners.addAll(b.chunkListeners);
        }
    }

    public BatchExecutionContext execute() {
        StepExecutionContext stepContext = new StepExecutionContext();
        stepContext.setStepName(name);
        stepContext.setCommitInterval(commitInterval);
        ChunkProcessor<T, R> processor = new ChunkProcessor<>(
                reader, this.processor, writer, commitInterval,
                skipPolicy, retryPolicy, stepListeners, chunkListeners);
        return processor.execute(name, stepContext);
    }

    public static <T, R> Builder<T, R> builder() {
        return new Builder<>();
    }

    public static class Builder<T, R> {
        private String name;
        private ItemReader<T> reader;
        private ItemProcessor<T, R> processor;
        private ItemWriter<R> writer;
        private int commitInterval = 100;
        private SkipPolicy skipPolicy = new SkipPolicy();
        private RetryPolicy retryPolicy;
        private List<StepListener> stepListeners;
        private List<ChunkListener<R>> chunkListeners;

        public Builder<T, R> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<T, R> reader(ItemReader<T> reader) {
            this.reader = reader;
            return this;
        }

        public Builder<T, R> processor(ItemProcessor<T, R> processor) {
            this.processor = processor;
            return this;
        }

        public Builder<T, R> writer(ItemWriter<R> writer) {
            this.writer = writer;
            return this;
        }

        public Builder<T, R> commitInterval(int commitInterval) {
            this.commitInterval = commitInterval;
            return this;
        }

        public Builder<T, R> skipPolicy(SkipPolicy skipPolicy) {
            this.skipPolicy = skipPolicy;
            return this;
        }

        public Builder<T, R> retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder<T, R> stepListener(StepListener listener) {
            if (this.stepListeners == null) {
                this.stepListeners = new ArrayList<>();
            }
            this.stepListeners.add(listener);
            return this;
        }

        public Builder<T, R> chunkListener(ChunkListener<R> listener) {
            if (this.chunkListeners == null) {
                this.chunkListeners = new ArrayList<>();
            }
            this.chunkListeners.add(listener);
            return this;
        }

        public Step<T, R> build() {
            if (name == null) {
                throw new IllegalArgumentException("step name must not be null");
            }
            if (reader == null) {
                throw new IllegalArgumentException("reader must not be null");
            }
            if (writer == null) {
                throw new IllegalArgumentException("writer must not be null");
            }
            return new Step<>(this);
        }
    }
}
