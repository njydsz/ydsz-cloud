package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.search.config.SearchProperties;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexOperation;
import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.provider.ProviderTypeBridge;
import com.njydsz.common.search.provider.SearchProvider;
import com.njydsz.common.search.provider.SearchProviderRegistry;
import com.njydsz.common.search.sync.PersistentDeadLetterQueue;

/**
 * 索引同步服务接口。
 *
 * <p>业务数据变更同步到 ES。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class IndexSyncService {

  private final SearchEngineRegistry engineRegistry;
  private final SearchProviderRegistry providerRegistry;
  private final SearchProperties properties;
  private final SearchMetrics metrics;
  private final ThreadPoolTaskExecutor executorService;

  private static final int MAX_DLQ_SIZE = 10000;
  private final ConcurrentLinkedQueue<IndexOperation> deadLetterQueue =
      new ConcurrentLinkedQueue<>();

  /** P6-14: 持久化死信队列（可选，依赖 ydsz-common-jdbc 提供的 DataSource） */
  private PersistentDeadLetterQueue persistentDlq;

  /**
   * 创建索引同步服务（使用外部注入的线程池）。
   *
   * <p>推荐用法：由 {@code SearchAutoConfiguration} 注入通过 {@code ydsz.thread.pools.indexSyncExecutor}
   * 配置的统一管理线程池。
   *
   * @param engineRegistry 引擎注册表
   * @param providerRegistry 提供者注册表
   * @param properties 搜索配置
   * @param metrics 指标采集器
   * @param executorService 外部注入的线程池（不可为 {@code null}）
   */
  public IndexSyncService(
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      SearchProperties properties,
      SearchMetrics metrics,
      ThreadPoolTaskExecutor executorService) {
    this.engineRegistry = engineRegistry;
    this.providerRegistry = providerRegistry;
    this.properties = properties;
    this.metrics = metrics;
    this.executorService = executorService;
  }

  /**
   * 创建索引同步服务（使用默认自创建线程池，兼容无统一线程池场景）。
   *
   * @param engineRegistry 引擎注册表
   * @param providerRegistry 提供者注册表
   * @param properties 搜索配置
   * @param metrics 指标采集器
   */
  public IndexSyncService(
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      SearchProperties properties,
      SearchMetrics metrics) {
    this(
        engineRegistry,
        providerRegistry,
        properties,
        metrics,
        createDefaultIndexSyncExecutor(properties));
  }

  /**
   * 设置持久化死信队列（可选，由装配层按需注入）。
   *
   * @param persistentDlq 持久化死信队列实例
   */
  public void setPersistentDlq(PersistentDeadLetterQueue persistentDlq) {
    this.persistentDlq = persistentDlq;
  }

  /**
   * 创建默认索引同步线程池。
   *
   * @param properties 搜索配置
   * @return 默认索引同步线程池
   */
  public static ThreadPoolTaskExecutor createDefaultIndexSyncExecutor(SearchProperties properties) {
    int coreSize = Math.max(2, properties.getIndex().getThreadPoolSize());
        // CHECKSTYLE.OFF: RegexpSinglelineJava
    // 兜底线程池：仅在外部未注入线程池时使用，生产环境由 ydsz.thread.pools.* 统一管理
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // CHECKSTYLE.ON: RegexpSinglelineJava
    executor.setCorePoolSize(coreSize);
    executor.setMaxPoolSize(Math.max(4, properties.getIndex().getThreadPoolSize() * 2));
    executor.setQueueCapacity(512);
    executor.setThreadNamePrefix("ydsz-index-sync-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(5);
    executor.initialize();
    return executor;
  }

  /**
   * 同步处理一条索引变更操作（UPSERT / DELETE / BULK）。
   *
   * <p><b>失败重试</b>：内部按 {@code ydsz.search.index.max-retries} 重试， 退避间隔为 {@code retryIntervalMs *
   * (第几次重试)} 的线性递增； 重试耗尽后操作进入内存死信队列（上限 10000 条，满则<b>静默丢弃</b>）， 可由 {@link #retryDeadLetterQueue()}
   * 补偿。
   *
   * <p><b>不抛异常</b>：所有失败都被吞掉并转为指标与日志， 保证索引同步失败不会回滚上游业务事务。也正因如此， 索引与数据库之间是<b>最终一致</b>而非强一致。
   *
   * <p>字段缺失的操作会被静默跳过（如 UPSERT 无 document、DELETE 缺 type/id）。 主引擎不支持索引能力时直接返回。
   *
   * <p>本方法在调用线程内同步执行（含重试期间的 sleep）， 若在业务事务中调用会延长事务持有时间，建议改用异步入口。
   *
   * @param operation 索引变更操作；为 {@code null} 时直接返回
   */
  public void handleOperation(IndexOperation operation) {
    if (operation == null) {
      return;
    }
    Optional<IndexStrategy> indexStrategy = engineRegistry.getIndexStrategy();
    if (indexStrategy.isEmpty()) {
      log.debug("[IndexSync] 主引擎不支持索引操作，跳过");
      return;
    }
    IndexStrategy idx = indexStrategy.get();

    switch (operation.getOperation()) {
      case UPSERT -> {
        if (operation.getDocument() != null) {
          executeWithRetry(
              () -> {
                idx.index(operation.getDocument());
              },
              operation);
        }
      }
      case DELETE -> {
        if (operation.getType() != null && operation.getDocumentId() != null) {
          executeWithRetry(
              () -> {
                idx.deleteIndex(operation.getType(), operation.getDocumentId());
              },
              operation);
        }
      }
      case BULK -> {
        if (operation.getDocuments() != null && !operation.getDocuments().isEmpty()) {
          executeWithRetry(
              () -> {
                idx.bulkIndex(operation.getDocuments());
              },
              operation);
        }
      }
      default -> { /* 未知操作类型忽略 */ }
    }
  }

  /**
   * 异步索引单个实体，立即返回不阻塞业务线程。
   *
   * <p>适合在业务事务提交后触发。注意任务在独立线程池执行， <b>脱离了调用方的事务与 ThreadLocal 上下文</b>（如租户、Locale），
   * 实体到文档的转换若依赖上下文需自行透传。
   *
   * <p><b>无重试</b>：与 {@link #handleOperation} 不同，本方法失败后 仅记 error 日志与失败指标，<b>不重试也不进死信队列</b>，
   * 对一致性要求高的场景请走 {@link #handleOperation}。
   *
   * <p>线程池队列容量 512，堆积超限会触发拒绝策略。
   *
   * @param entity 待索引的业务实体，为 {@code null} 时由 provider 决定行为
   * @param provider 该实体类型对应的搜索提供者，负责实体到 {@link IndexDocument} 的转换，不可为 {@code null}
   * @param <T> 业务实体类型
   */
  public <T> void indexAsync(T entity, SearchProvider<T> provider) {
    executorService.submit(
        () -> {
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
   * <p>与 {@link #indexAsync} 同样<b>无重试、不进死信队列</b>， 失败仅记 error 日志与失败指标。删除失败会导致已删除的业务数据
   * 仍能被搜索到（脏数据），必要时需通过全量重建纠正。
   *
   * <p>主引擎不支持索引能力时静默跳过。
   *
   * @param type 实体类型，对应索引名或索引内的类型字段
   * @param documentId 文档主键；不存在时底层通常视为幂等成功
   */
  public void deleteAsync(String type, String documentId) {
    executorService.submit(
        () -> {
          try {
            engineRegistry
                .getIndexStrategy()
                .ifPresent(
                    idx -> {
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
   * <p>采用 UPSERT 语义，<b>只写不删</b>：数据库中已删除的记录不会从索引中清除， 需要彻底清理请使用 {@code
   * IndexRebuildService.rebuildAll}（会先清空索引）。
   *
   * <p><b>容错</b>：单个 provider 重建失败只记 error 日志并继续下一个， 单条实体加载失败只记 warn 并跳过，因此本方法几乎不抛异常，
   * 返回值可能小于数据源实际记录数，调用方应比对预期数量判断是否成功。
   *
   * <p>同步阻塞执行，耗时与数据量成正比，禁止在请求线程中直接调用。
   *
   * @param type 实体类型；为 {@code null} 表示重建全部已注册类型
   * @param tenantId 租户 ID；为 {@code null} 表示不按租户过滤
   * @return 实际成功写入索引的文档总数，最小为 0
   */
  public int rebuildAll(String type, String tenantId) {
    log.info("[IndexSync] 开始全量重建: type={}, tenantId={}", type, tenantId);
    AtomicInteger total = new AtomicInteger(0);
    List<SearchProvider<?>> providers =
        providerRegistry.getProviders(type != null ? List.of(type) : Collections.emptyList());
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
   * <p>返回副本而非原队列引用，调用方修改不会影响内部状态； 死信操作可通过 {@link #replayDeadLetters()} 重放补偿。
   *
   * @return 死信操作的副本列表；无死信时返回空列表
   */
  public List<IndexOperation> getDeadLetterQueue() {
    return new ArrayList<>(deadLetterQueue);
  }

  /**
   * 重放死信队列中的失败索引操作，用于故障恢复后的补偿。
   *
   * <p>处理顺序：先重放持久化 DB 中的死信（按创建顺序），再重放内存队列。 DB 重放使用 SELECT FOR UPDATE SKIP LOCKED 保证多实例不冲突。
   *
   * <p>DB 死信重放成功时更新状态为 RESOLVED，失败时递增 retry_count， 重试超过 5 次标记为 DISCARDED（需人工介入）。
   */
  public void retryDeadLetterQueue() {
    // P6-14: 重放持久化 DB 死信
    if (persistentDlq != null) {
      try {
        persistentDlq.replayPending(
            100,
            record -> {
              // 将 DlqRecord 转回 IndexOperation 并执行
              IndexOperation op = rebuildOperationFromDlq(record);
              if (op != null) {
                handleOperation(op);
              }
            });
      } catch (Exception e) {
        log.error("[IndexSync] DB 死信重放失败: {}", e.getMessage(), e);
      }
    }

    // 重放内存队列（向后兼容）
    List<IndexOperation> snapshot = new ArrayList<>();
    IndexOperation op;
    while ((op = deadLetterQueue.poll()) != null) {
      snapshot.add(op);
    }
    if (!snapshot.isEmpty()) {
      log.info("[IndexSync] 重试内存死信队列: size={}", snapshot.size());
      for (IndexOperation operation : snapshot) {
        handleOperation(operation);
      }
    }
  }

  /**
   * 将 DB 死信记录重建为 IndexOperation。
   *
   * @return 索引操作，null 表示无法重建（记录被丢弃）
   */
  private IndexOperation rebuildOperationFromDlq(PersistentDeadLetterQueue.DlqRecord record) {
    try {
      IndexOperation.IndexOperationBuilder opBuilder = IndexOperation.builder();
      opBuilder.type(record.docType());

      IndexOperation.OperationType opType =
          IndexOperation.OperationType.valueOf(record.operation());
      opBuilder.operation(opType);

      switch (opType) {
        case DELETE -> opBuilder.documentId(record.documentId());
        case UPSERT -> {
          if (record.documentJson() != null) {
            IndexDocument doc =
                YdszJson.fromJson(
                    record.documentJson(), IndexDocument.class);
            opBuilder.document(doc);
          }
        }
        case BULK -> {
          if (record.documentJson() != null) {
            List<IndexDocument> docs =
                YdszJson.fromJson(
                    record.documentJson(), List.class, IndexDocument.class);
            opBuilder.documents(docs);
          }
        }
        default -> { /* 未知操作类型忽略 */ }
      }
      return opBuilder.build();
    } catch (Exception e) {
      log.warn("[IndexSync] 死信记录重建失败（跳过）: id={}, op={}", record.id(), record.operation(), e);
      return null;
    }
  }

  /**
   * 关闭索引同步线程池，由容器在 Bean 销毁阶段调用。
   *
   * <p>线程池配置 {@code waitForTasksToCompleteOnShutdown=true} 且等待 5 秒， 会尽量让队列中的索引任务执行完毕，最多阻塞约 5 秒；
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
          // P6-14: 持久化到 DB（主路径），失败降级到内存
          boolean persisted = false;
          if (persistentDlq != null) {
            persisted = persistentDlq.enqueue(operation, e.getMessage());
          }
          if (!persisted && deadLetterQueue.size() < MAX_DLQ_SIZE) {
            deadLetterQueue.add(operation);
          }
        }
      }
    }
  }

  private <T> void rebuildProvider(
      SearchProvider<T> provider, String tenantId, AtomicInteger total) {
    List<T> entities = provider.loadAll(tenantId);
    if (entities == null || entities.isEmpty()) {
      return;
    }

    int batchSize = properties.getIndex().getRebuildBatchSize();
    Optional<IndexStrategy> indexStrategyOpt = engineRegistry.getIndexStrategy();
    if (indexStrategyOpt.isEmpty()) {
      log.warn("[IndexSync] 引擎不支持索引操作，跳过重建");
      return;
    }
    IndexStrategy indexStrategy = indexStrategyOpt.get();

    for (int i = 0; i < entities.size(); i += batchSize) {
      int end = Math.min(i + batchSize, entities.size());
      List<T> batch = entities.subList(i, end);
      List<IndexDocument> documents = new ArrayList<>();
      for (T entity : batch) {
        try {
          IndexDocument doc = provider.toIndexDocument(entity);
          if (doc != null) {
            documents.add(doc);
          }
        } catch (Exception e) {
          log.warn("[IndexSync] 转换索引文档失败: type={}", provider.getType(), e);
        }
      }
      if (!documents.isEmpty()) {
        indexStrategy.bulkIndex(documents);
        total.addAndGet(documents.size());
      }
    }
  }
}
