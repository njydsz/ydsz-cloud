package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexOperation;
import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.ProviderTypeBridge;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引同步服务接口。
 * <p>业务数据变更同步到 ES。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
public class IndexSyncService {

    private final SearchEngineRegistry engineRegistry;
    private final SearchProviderRegistry providerRegistry;
    private final SearchProperties properties;
    private final SearchMetrics metrics;
    private final ThreadPoolTaskExecutor executorService;

    private static final int MAX_DLQ_SIZE = 10000;
    private final ConcurrentLinkedQueue<IndexOperation> deadLetterQueue = new ConcurrentLinkedQueue<>();

    public IndexSyncService(SearchEngineRegistry engineRegistry,
                            SearchProviderRegistry providerRegistry,
                            SearchProperties properties,
                            SearchMetrics metrics) {
        this.engineRegistry = engineRegistry;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.metrics = metrics;

        this.executorService = new ThreadPoolTaskExecutor();
        this.executorService.setCorePoolSize(Math.max(2, properties.getIndex().getThreadPoolSize()));
        this.executorService.setMaxPoolSize(Math.max(4, properties.getIndex().getThreadPoolSize() * 2));
        this.executorService.setQueueCapacity(512);
        this.executorService.setThreadNamePrefix("index-sync-");
        this.executorService.setWaitForTasksToCompleteOnShutdown(true);
        this.executorService.setAwaitTerminationSeconds(5);
        this.executorService.initialize();
    }

    /**
     * 同步处理一条索引变更操作（UPSERT / DELETE / BULK）。
     *
     * <p><b>失败重试</b>：内部按 {@code ydsz.search.index.max-retries} 重试，
     * 退避间隔为 {@code retryIntervalMs * (第几次重试)} 的线性递增；
     * 重试耗尽后操作进入内存死信队列（上限 10000 条，满则<b>静默丢弃</b>），
     * 可由 {@link #retryDeadLetterQueue()} 补偿。
     *
     * <p><b>不抛异常</b>：所有失败都被吞掉并转为指标与日志，
     * 保证索引同步失败不会回滚上游业务事务。也正因如此，
     * 索引与数据库之间是<b>最终一致</b>而非强一致。
     *
     * <p>字段缺失的操作会被静默跳过（如 UPSERT 无 document、DELETE 缺 type/id）。
     * 主引擎不支持索引能力时直接返回。
     *
     * <p>本方法在调用线程内同步执行（含重试期间的 sleep），
     * 若在业务事务中调用会延长事务持有时间，建议改用异步入口。
     *
     * @param operation 索引变更操作；为 {@code null} 时直接返回
     */
    public void handleOperation(IndexOperation operation) {
        if (operation == null) return;
        Optional<IndexStrategy> indexStrategy = engineRegistry.getIndexStrategy();
        if (indexStrategy.isEmpty()) {
            log.debug("[IndexSync] 主引擎不支持索引操作，跳过");
            return;
        }
        IndexStrategy idx = indexStrategy.get();

        switch (operation.getOperation()) {
            case UPSERT -> {
                if (operation.getDocument() != null) {
                    executeWithRetry(() -> {
                        idx.index(operation.getDocument());
                    }, operation);
                }
            }
            case DELETE -> {
                if (operation.getType() != null && operation.getDocumentId() != null) {
                    executeWithRetry(() -> {
                        idx.deleteIndex(operation.getType(), operation.getDocumentId());
                    }, operation);
                }
            }
            case BULK -> {
                if (operation.getDocuments() != null && !operation.getDocuments().isEmpty()) {
                    executeWithRetry(() -> {
                        idx.bulkIndex(operation.getDocuments());
                    }, operation);
                }
            }
        }
    }

    /**
     * 异步索引单个实体，立即返回不阻塞业务线程。
     *
     * <p>适合在业务事务提交后触发。注意任务在独立线程池执行，
     * <b>脱离了调用方的事务与 ThreadLocal 上下文</b>（如租户、Locale），
     * 实体到文档的转换若依赖上下文需自行透传。
     *
     * <p><b>无重试</b>：与 {@link #handleOperation} 不同，本方法失败后
     * 仅记 error 日志与失败指标，<b>不重试也不进死信队列</b>，
     * 对一致性要求高的场景请走 {@link #handleOperation}。
     *
     * <p>线程池队列容量 512，堆积超限会触发拒绝策略。
     *
     * @param entity   待索引的业务实体，为 {@code null} 时由 provider 决定行为
     * @param provider 该实体类型对应的搜索提供者，负责实体到 {@link IndexDocument} 的转换，不可为 {@code null}
     * @param <T>      业务实体类型
     */
    public <T> void indexAsync(T entity, SearchProvider<T> provider) {
        executorService.submit(() -> {
            try {
                IndexDocument document = provider.toIndexDocument(entity);
                Optional<IndexStrategy> idx = engineRegistry.getIndexStrategy();
                if (idx.isPresent()) {
                    idx.get().index(document);
                    metrics.recordIndexOp(true);
                }
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                log.error("[IndexSync] 异步索引失败: type={}", provider.getType(), e);
            }
        });
    }

    /**
     * 异步删除单个索引文档，立即返回不阻塞业务线程。
     *
     * <p>与 {@link #indexAsync} 同样<b>无重试、不进死信队列</b>，
     * 失败仅记 error 日志与失败指标。删除失败会导致已删除的业务数据
     * 仍能被搜索到（脏数据），必要时需通过全量重建纠正。
     *
     * <p>主引擎不支持索引能力时静默跳过。
     *
     * @param type       实体类型，对应索引名或索引内的类型字段
     * @param documentId 文档主键；不存在时底层通常视为幂等成功
     */
    public void deleteAsync(String type, String documentId) {
        executorService.submit(() -> {
            try {
                engineRegistry.getIndexStrategy().ifPresent(idx -> {
                    idx.deleteIndex(type, documentId);
                    metrics.recordIndexOp(true);
                });
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                log.error("[IndexSync] 异步删除失败: type={}, id={}", type, documentId, e);
            }
        });
    }

    /**
     * 从数据源全量回灌索引，按 provider 逐类型、按 {@code rebuildBatchSize} 分批 bulk 写入。
     *
     * <p>采用 UPSERT 语义，<b>只写不删</b>：数据库中已删除的记录不会从索引中清除，
     * 需要彻底清理请使用 {@code IndexRebuildService.rebuildAll}（会先清空索引）。
     *
     * <p><b>容错</b>：单个 provider 重建失败只记 error 日志并继续下一个，
     * 单条实体加载失败只记 warn 并跳过，因此本方法几乎不抛异常，
     * 返回值可能小于数据源实际记录数，调用方应比对预期数量判断是否成功。
     *
     * <p>同步阻塞执行，耗时与数据量成正比，禁止在请求线程中直接调用。
     *
     * @param type     实体类型；为 {@code null} 表示重建全部已注册类型
     * @param tenantId 租户 ID；为 {@code null} 表示不按租户过滤
     * @return 实际成功写入索引的文档总数，最小为 0
     */
    public int rebuildAll(String type, String tenantId) {
        log.info("[IndexSync] 开始全量重建: type={}, tenantId={}", type, tenantId);
        AtomicInteger total = new AtomicInteger(0);
        List<SearchProvider<?>> providers = providerRegistry.getProviders(
                type != null ? List.of(type) : Collections.emptyList());
        for (SearchProvider<?> provider : providers) {
            try {
                rebuildProvider(ProviderTypeBridge.cast(provider), tenantId, total);
            } catch (Exception e) {
                log.error("[IndexSync] Provider {} 重建失败", provider.getType(), e);
            }
        }
        log.info("[IndexSync] 全量重建完成: total={}", total.get());
        return total.get();
    }

    /**
     * 获取死信队列快照（同步失败的索引操作）。
     *
     * <p>返回副本而非原队列引用，调用方修改不会影响内部状态；
     * 死信操作可通过 {@link #replayDeadLetters()} 重放补偿。</p>
     *
     * @return 死信操作的副本列表；无死信时返回空列表
     */
    public List<IndexOperation> getDeadLetterQueue() {
        return new ArrayList<>(deadLetterQueue);
    }

    /**
     * 重放死信队列中的失败索引操作，用于故障恢复后的补偿。
     *
     * <p>先将队列整体 poll 成快照再逐条重放，避免「重放失败又入队」导致的死循环；
     * 重放仍失败的操作会重新进入死信队列，等待下一轮补偿。
     *
     * <p>死信队列是<b>纯内存</b>结构（{@link ConcurrentLinkedQueue}），
     * 应用重启即丢失，且容量上限 10000 条，超出部分在入队时已被丢弃。
     * 因此它只能兜住短时抖动，长时间故障后必须走全量重建。
     *
     * <p>同步阻塞执行，队列较大时耗时可观，通常由定时任务调度而非请求线程调用。
     * 多线程并发调用是安全的（各自 poll 到不相交的快照），但会放大对引擎的瞬时压力。
     */
    public void retryDeadLetterQueue() {
        List<IndexOperation> snapshot = new ArrayList<>();
        IndexOperation op;
        while ((op = deadLetterQueue.poll()) != null) {
            snapshot.add(op);
        }
        if (snapshot.isEmpty()) return;
        log.info("[IndexSync] 重试死信队列: size={}", snapshot.size());
        for (IndexOperation operation : snapshot) {
            handleOperation(operation);
        }
    }

    /**
     * 关闭索引同步线程池，由容器在 Bean 销毁阶段调用。
     *
     * <p>线程池配置 {@code waitForTasksToCompleteOnShutdown=true} 且等待 5 秒，
     * 会尽量让队列中的索引任务执行完毕，最多阻塞约 5 秒；
     * 超时未完成的任务被中断，其变更将丢失（死信队列同为内存结构，一并丢失）。
     *
     * <p>重复调用是安全的（幂等）。
     */
    public void shutdown() {
        executorService.shutdown();
        log.info("[IndexSync] 线程池已关闭");
    }

    // ==================== 私有方法 ====================

    private void executeWithRetry(Runnable action, IndexOperation operation) {
        int maxRetries = properties.getIndex().getMaxRetries();
        long retryInterval = properties.getIndex().getRetryIntervalMs();
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                action.run();
                metrics.recordIndexOp(true);
                return;
            } catch (Exception e) {
                metrics.recordIndexOp(false);
                if (attempt < maxRetries) {
                    log.warn("[IndexSync] 索引操作失败，重试: {}/{}", attempt + 1, maxRetries);
                    try {
                        Thread.sleep(retryInterval * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("[IndexSync] 重试耗尽，加入死信队列", e);
                    if (deadLetterQueue.size() < MAX_DLQ_SIZE) {
                        deadLetterQueue.add(operation);
                    }
                }
            }
        }
    }

    private <T> void rebuildProvider(SearchProvider<T> provider, String tenantId, AtomicInteger total) {
        List<String> ids = provider.getAllDocumentIds(tenantId);
        if (ids == null || ids.isEmpty()) return;

        int batchSize = properties.getIndex().getRebuildBatchSize();
        Optional<IndexStrategy> indexStrategyOpt = engineRegistry.getIndexStrategy();
        if (indexStrategyOpt.isEmpty()) {
            log.warn("[IndexSync] 引擎不支持索引操作，跳过重建");
            return;
        }
        IndexStrategy indexStrategy = indexStrategyOpt.get();

        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<String> batch = ids.subList(i, end);
            List<IndexDocument> documents = new ArrayList<>();
            for (String id : batch) {
                try {
                    T entity = provider.loadById(id);
                    if (entity != null) {
                        IndexDocument doc = provider.toIndexDocument(entity);
                        if (doc != null) documents.add(doc);
                    }
                } catch (Exception e) {
                    log.warn("[IndexSync] 加载实体失败: type={}, id={}", provider.getType(), id, e);
                }
            }
            if (!documents.isEmpty()) {
                indexStrategy.bulkIndex(documents);
                total.addAndGet(documents.size());
            }
        }
    }
}
