package com.njydsz.agent.infra.asynctask;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.asynctask.AsyncTaskStore;

/**
 * 异步任务存储路由器 — 根据配置委托到具体实现。
 *
 * <p>当同时存在多个 {@link AsyncTaskStore} Bean 时（如 memory + jdbc），
 * 路由器根据运行时配置选择当前有效的实现。
 *
 * <p><b>设计说明</b>：由于两个实现类的 {@code @ConditionalOnProperty} 条件互斥
 * （memory 的 matchIfMissing=true、jdbc 的 matchIfMissing=false），
 * 运行时只会存在一个 Bean。路由器通过 {@link ObjectProvider} 延迟注入，
 * 避免因某个实现不存在而导致启动失败。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
public class AsyncTaskStoreRouter {

  private final ObjectProvider<InMemoryAsyncTaskStore> inMemoryStoreProvider;
  private final ObjectProvider<JdbcAsyncTaskStore> jdbcStoreProvider;

  /**
   * 构造路由器。
   *
   * @param inMemoryStoreProvider 内存存储实现提供者
   * @param jdbcStoreProvider    JDBC 存储实现提供者
   */
  public AsyncTaskStoreRouter(
      ObjectProvider<InMemoryAsyncTaskStore> inMemoryStoreProvider,
      ObjectProvider<JdbcAsyncTaskStore> jdbcStoreProvider) {
    this.inMemoryStoreProvider = inMemoryStoreProvider;
    this.jdbcStoreProvider = jdbcStoreProvider;
  }

  /**
   * 根据配置获取当前生效的存储实现。
   *
   * @param storeType 存储类型（memory/jdbc）
   * @return 对应的存储实现
   */
  public AsyncTaskStore getStore(String storeType) {
    if ("jdbc".equalsIgnoreCase(storeType)) {
      JdbcAsyncTaskStore jdbcStore = jdbcStoreProvider.getIfAvailable();
      if (jdbcStore != null) {
        log.debug("[AsyncTask-Router] 使用 JDBC 存储实现");
        return jdbcStore;
      }
      log.warn("[AsyncTask-Router] JDBC 存储实现不可用，降级为内存实现");
    }
    InMemoryAsyncTaskStore memoryStore = inMemoryStoreProvider.getIfAvailable();
    if (memoryStore != null) {
      log.debug("[AsyncTask-Router] 使用内存存储实现");
      return memoryStore;
    }
    throw new IllegalStateException("无可用的 AsyncTaskStore 实现");
  }

  /**
   * 获取内存存储实现。
   *
   * @return 内存存储实例；不存在返回 null
   */
  public InMemoryAsyncTaskStore getInMemoryStore() {
    return inMemoryStoreProvider.getIfAvailable();
  }

  /**
   * 获取 JDBC 存储实现。
   *
   * @return JDBC 存储实例；不存在返回 null
   */
  public JdbcAsyncTaskStore getJdbcStore() {
    return jdbcStoreProvider.getIfAvailable();
  }
}
