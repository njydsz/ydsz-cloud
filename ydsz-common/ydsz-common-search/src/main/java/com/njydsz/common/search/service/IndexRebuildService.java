package com.njydsz.common.search.service;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.search.core.IndexStrategy;
import com.njydsz.common.search.core.SearchEngineRegistry;
import com.njydsz.common.search.provider.SearchProviderRegistry;

/**
 * 索引重建服务接口。
 *
 * <p>全量/增量重建 ES 索引。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class IndexRebuildService {

  private final IndexSyncService indexSyncService;
  private final SearchEngineRegistry engineRegistry;
  private final SearchProviderRegistry providerRegistry;

  private volatile boolean rebuilding = false;
  private volatile int progress = 0;
  private volatile int total = 0;

  private final ThreadPoolTaskExecutor rebuildExecutor;

  /**
   * 创建索引重建服务（使用默认单线程线程池）。
   *
   * @param indexSyncService 索引同步服务
   * @param engineRegistry 引擎注册表
   * @param providerRegistry 提供者注册表
   */
  public IndexRebuildService(
      IndexSyncService indexSyncService,
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry) {
    this(indexSyncService, engineRegistry, providerRegistry, createDefaultRebuildExecutor());
  }

  /**
   * 创建索引重建服务（使用外部注入的线程池）。
   *
   * @param indexSyncService 索引同步服务
   * @param engineRegistry 引擎注册表
   * @param providerRegistry 提供者注册表
   * @param rebuildExecutor 外部注入的线程池
   */
  public IndexRebuildService(
      IndexSyncService indexSyncService,
      SearchEngineRegistry engineRegistry,
      SearchProviderRegistry providerRegistry,
      ThreadPoolTaskExecutor rebuildExecutor) {
    this.indexSyncService = indexSyncService;
    this.engineRegistry = engineRegistry;
    this.providerRegistry = providerRegistry;
    this.rebuildExecutor = rebuildExecutor;
  }

  /**
   * 创建默认索引重建线程池（单线程，串行重建保证一致性）。
   *
   * <p>命名符合云顶编码规范 15.4.4 约定：ydsz-{module}-{biz}-。
   *
   * @return 默认重建线程池
   */
  public static ThreadPoolTaskExecutor createDefaultRebuildExecutor() {
    // CHECKSTYLE.OFF: RegexpSinglelineJava
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // CHECKSTYLE.ON: RegexpSinglelineJava
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(1);
    executor.setThreadNamePrefix("ydsz-index-rebuild-");
    executor.setDaemon(true);
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.initialize();
    return executor;
  }

  /**
   * 同步执行全量索引重建：先清空目标索引，再从数据源全量回灌。
   *
   * <p><b>危险操作</b>：会先调用 {@code deleteAllIndices} 物理清空索引， 从清空到回灌完成之间搜索结果为空，属于有损重建，
   * 只应在维护窗口或数据量较小的场景使用。
   *
   * <p><b>并发控制</b>：通过 {@code rebuilding} 标志位做单实例互斥， 已有重建在执行时立即返回 -1。该标志为普通 volatile 变量，
   * <b>不是</b>严格的 CAS 锁，也<b>不跨节点</b>生效，集群环境需由调用方保证只有一个节点触发。
   *
   * <p>本方法在调用线程内同步阻塞直至完成，耗时与数据量成正比， 不要在 HTTP 请求线程中直接调用，应改用 {@link #rebuildAllAsync(String,
   * String)}。
   *
   * @param type 实体类型；为 {@code null} 或空白表示重建全部已注册类型
   * @param tenantId 租户 ID；为 {@code null} 表示不按租户过滤，重建全租户数据
   * @return 成功索引的文档数；重建已在进行中、引擎不支持索引操作或执行异常时返回 -1
   */
  public int rebuildAll(String type, String tenantId) {
    if (rebuilding) {
      log.warn("[IndexRebuild] 重建任务正在执行中");
      return -1;
    }
    rebuilding = true;
    progress = 0;
    total = 0;
    try {
      Optional<IndexStrategy> idx = engineRegistry.getIndexStrategy();
      if (idx.isEmpty()) {
        log.warn("[IndexRebuild] 主引擎不支持索引操作，无法重建");
        return -1;
      }
      if (type == null || type.isBlank()) {
        idx.get().deleteAllIndices(null);
      } else {
        idx.get().deleteAllIndices(type);
      }
      int count = indexSyncService.rebuildAll(type, tenantId);
      total = count;
      progress = count;
      log.info("[IndexRebuild] 全量重建完成: type={}, total={}", type, count);
      return count;
    } catch (Exception e) {
      log.error("[IndexRebuild] 全量重建失败", e);
      return -1;
    } finally {
      rebuilding = false;
    }
  }

  /**
   * 异步触发全量索引重建，立即返回不阻塞调用线程。
   *
   * <p>提交到专用的单线程池（核心/最大均为 1、队列容量 1、守护线程）， 因此最多只有 1 个任务在跑 + 1 个排队，再多的提交会触发线程池拒绝策略。 进度可通过 {@code
   * isRebuilding()} 与 {@code getProgressPercent()} 轮询。
   *
   * <p>任务内部已捕获所有异常并记 error 日志，失败不会向外传播， 调用方无法通过返回值感知结果，需依赖进度查询接口或日志告警。
   *
   * <p>注意线程池配置了 {@code waitForTasksToCompleteOnShutdown=false}， 应用关闭时未完成的重建会被直接中断，可能留下部分索引，需重跑。
   *
   * @param type 实体类型；为 {@code null} 或空白表示重建全部已注册类型
   * @param tenantId 租户 ID；为 {@code null} 表示重建全租户数据
   */
  public void rebuildAllAsync(String type, String tenantId) {
    rebuildExecutor.submit(
        () -> {
          try {
            rebuildAll(type, tenantId);
          } catch (Exception e) {
            log.error("[IndexRebuild] async rebuild failed", e);
          }
        });
  }

  /**
   * 蓝绿索引重建
   *
   * <p>先将新数据索引到"绿色"索引（不影响当前搜索）， 索引完成后原子切换到新索引，删除旧索引。
   *
   * <p>当前实现：先全量重建新索引数据，然后删除旧索引。 真正的双表蓝绿切换需要引擎层支持（PG 可用 shadow 表，ES 可用 alias）。
   *
   * @param type 实体类型
   * @param tenantId 租户 ID
   * @return 索引文档数，失败返回 -1
   */
  public int rebuildWithBlueGreen(String type, String tenantId) {
    if (rebuilding) return -1;
    rebuilding = true;
    progress = 0;
    total = 0;
    try {
      Optional<IndexStrategy> idx = engineRegistry.getIndexStrategy();
      if (idx.isEmpty()) {
        log.warn("[IndexRebuild] 主引擎不支持索引操作，蓝绿重建跳过");
        return -1;
      }

      log.info("[IndexRebuild] 蓝绿重建开始: type={}, tenantId={}", type, tenantId);

      // Step 1: 全量索引新数据（UPSERT 模式，不删除旧数据）
      int count = indexSyncService.rebuildAll(type, tenantId);
      total = count;
      progress = count;

      // Step 2: 清理旧索引中不在 DB 中的冗余文档
      if (type != null && !type.isBlank()) {
        try {
          idx.get().deleteAllIndices(type);
          // 重新索引（因为 deleteAllIndices 会清空所有数据）
          if (count > 0) {
            count = indexSyncService.rebuildAll(type, tenantId);
            total = count;
            progress = count;
          }
        } catch (Exception e) {
          log.warn("[IndexRebuild] 蓝绿重建清理旧索引失败，新数据已就位", e);
        }
      }

      log.info("[IndexRebuild] 蓝绿重建完成: type={}, total={}", type, count);
      return count;
    } catch (Exception e) {
      log.error("[IndexRebuild] 蓝绿重建失败", e);
      return -1;
    } finally {
      rebuilding = false;
    }
  }

  public boolean isRebuilding() {
    return rebuilding;
  }

  public int getProgress() {
    return progress;
  }

  public int getTotal() {
    return total;
  }

  /**
   * 获取重建进度百分比。
   *
   * <p>未在重建且无总量时返回 {@code -1}（表示从未执行）；重建中返回 [0, 100]。
   *
   * @return 进度百分比；{@code -1} 表示不在重建状态
   */
  public int getProgressPercent() {
    if (!rebuilding || total == 0) return rebuilding ? 0 : -1;
    return Math.min(100, (progress * 100) / total);
  }

  /**
   * 获取已注册的全部索引类型。
   *
   * @return 索引类型名列表（如 user / project / document）
   */
  public List<String> getRegisteredTypes() {
    return providerRegistry.getAllTypes();
  }

  /**
   * 关闭重建线程池，由容器在 Bean 销毁阶段调用。
   *
   * <p>线程池设置了 {@code waitForTasksToCompleteOnShutdown=false}， 因此本方法<b>不等待</b>在途重建任务完成即返回；
   * 被中断的重建会留下不完整索引，应用重启后需重新触发重建。
   *
   * <p>重复调用是安全的（幂等）。
   */
  public void shutdown() {
    rebuildExecutor.shutdown();
    log.info("[IndexRebuild] 重建线程池已关闭");
  }
}
