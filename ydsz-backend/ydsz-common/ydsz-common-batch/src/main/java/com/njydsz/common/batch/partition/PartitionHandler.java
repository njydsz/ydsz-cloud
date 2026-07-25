package com.njydsz.common.batch.partition;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.njydsz.common.batch.chunk.ChunkProcessor;
import com.njydsz.common.batch.item.ItemProcessor;
import com.njydsz.common.batch.item.ItemReader;
import com.njydsz.common.batch.item.ItemWriter;
import com.njydsz.common.batch.model.BatchExecutionContext;
import com.njydsz.common.batch.model.StepExecutionContext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 分区处理器
 *
 * <p>将大批量任务按 key 切分为多个分区，并行执行以提升吞吐量。
 * 适用于 ETL、批量数据迁移、批量对账等场景。
 *
 * @param <T> 数据类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class PartitionHandler<T> {

    private final ExecutorService executor;
    private final List<Partition<T>> partitions;

    public PartitionHandler(ExecutorService executor, List<Partition<T>> partitions) {
        this.executor = executor;
        this.partitions = partitions;
    }

    /**
     * 并行执行所有分区
     */
    public List<BatchExecutionContext> executeAll(String stepName,
                                                    ItemProcessor<T, T> processor,
                                                    ItemWriter<T> writer) {
        List<CompletableFuture<BatchExecutionContext>> futures = partitions.stream()
                .map(p -> CompletableFuture.supplyAsync(() -> executePartition(stepName, p, processor, writer), executor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private BatchExecutionContext executePartition(String stepName, Partition<T> partition,
                                                    ItemProcessor<T, T> processor,
                                                    ItemWriter<T> writer) {
        PartitionReader<T> reader = new PartitionReader<>(partition);
        StepExecutionContext ctx = new StepExecutionContext();
        ctx.setStepName(stepName + "-partition-" + partition.getPartitionKey());
        ChunkProcessor<T, T> cp = new ChunkProcessor<>(
                reader, processor, writer, partition.getChunkSize(),
                null, null, null, null);
        return cp.execute(ctx.getStepName(), ctx);
    }

    /**
     * 分区定义
     */
    @Data
    @AllArgsConstructor
    public static class Partition<T> {
        /** 分区键 */
        private String partitionKey;
        /** 该分区的数据 */
        private List<T> data;
        /** chunk 大小 */
        private int chunkSize;
    }

    /**
     * 分区读取器（基于内存列表）
     */
    private static class PartitionReader<T> implements ItemReader<T> {
        private final List<T> data;
        private int index = 0;

        PartitionReader(Partition<T> partition) {
            this.data = partition.getData();
        }

        @Override
        public T read() {
            if (index >= data.size()) {
                return null;
            }
            return data.get(index++);
        }
    }
}
