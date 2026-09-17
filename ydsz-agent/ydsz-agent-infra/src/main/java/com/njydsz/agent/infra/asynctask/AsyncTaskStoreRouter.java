package com.njydsz.agent.infra.asynctask;

import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.asynctask.AsyncTaskStore;

/**
 * 异步任务存储路由器 — 根据配置委托到具体实现。
 *
 * <p>持有对内存和 JDBC 两种实现的应用，根据配置项
 * {@code ydsz.agent.async-task.store-type} 选择当前有效的实现。
 *
 * <p><b>设计说明</b>：由于两个 Bean 由 {@link AgentAsyncTaskAutoConfiguration} 条件化注册，
 * 运行时最多存在一个 Bean。路由器提供运行时按配置切换的能力，
 * 同时保持对单一实现 Bean 的直接引用（无需 ObjectProvider）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@RequiredArgsConstructor
public class AsyncTaskStoreRouter {

  private final AsyncTaskStore activeStore;

  /**
   * 获取当前生效的存储实现。
   *
   * @return 当前配置的存储实现（非 null）
   */
  public AsyncTaskStore getActiveStore() {
    return activeStore;
  }

  /**
   * 根据 storeType 配置获取对应的存储实现。
   *
   * <p>当配置为 memory 且当前激活的是内存实现时，返回内存实现；
   * 当配置为 jdbc 且当前激活的是 JDBC 实现时，返回 JDBC 实现；
   * 若配置与当前激活实现不匹配，则返回当前激活的实现（降级策略）。
   *
   * @param storeType 存储类型（memory/jdbc）
   * @return 对应的存储实现
   */
  public AsyncTaskStore getStore(String storeType) {
    Objects.requireNonNull(storeType, "storeType 不能为 null");
    if (activeStore.getType().equalsIgnoreCase(storeType)) {
      return activeStore;
    }
    if ("jdbc".equalsIgnoreCase(storeType)) {
      log.warn("[AsyncTask-Router] JDBC 存储未激活（可能缺少 Mapper 或配置），使用当前激活的实现: {}",
          activeStore.getType());
    }
    return activeStore;
  }

  /**
   * 获取当前存储类型标识。
   *
   * @return 如 "memory"、"jdbc"
   */
  public String getCurrentStoreType() {
    return activeStore.getType();
  }
}
