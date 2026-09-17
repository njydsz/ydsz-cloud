package com.njydsz.agent.infra.asynctask;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.njydsz.agent.domain.asynctask.AsyncTaskStore;
import com.njydsz.agent.infra.mapper.AsyncTaskMapper;

/**
 * 异步任务模块自动配置。
 *
 * <p>根据配置项 {@code ydsz.agent.async-task.store-type} 条件化注册 AsyncTaskStore：
 * <ul>
 *   <li>{@code store-type=jdBC} → 注册 {@link JdbcAsyncTaskStore}（需 {@link AsyncTaskMapper}）</li>
 *   <li>默认（未配置）或 {@code store-type=memory} → 注册 {@link InMemoryAsyncTaskStore}</li>
 * </ul>
 *
 * <p><b>装配策略</b>：
 * <ul>
 *   <li>JDBC 实现标注 {@code @Primary}，在同一类型多 Bean 时优先注入</li>
 *   <li>内存实现标注 {@code @ConditionalOnMissingBean}，作为 fallback</li>
 *   <li>路由器始终注册，委托当前生效的 {@link AsyncTaskStore} Bean</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty(
    prefix = "ydsz.agent.async-task",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AgentAsyncTaskAutoConfiguration {

  /**
   * 注册 JDBC 持久化存储（生产环境）。
   *
   * <p>仅当 {@code ydsz.agent.async-task.store-type=jdBC} 时激活。
   * 标注 {@code @Primary} 确保同一类型多 Bean 注入时优先选择本实现。
   *
   * @param asyncTaskMapper MyBatis Plus Mapper
   * @return JDBC 异步任务存储
   */
  @Bean
  @Primary
  @ConditionalOnProperty(
      prefix = "ydsz.agent.async-task",
      name = "store-type",
      havingValue = "jdbc",
      matchIfMissing = false)
  public JdbcAsyncTaskStore jdbcAsyncTaskStore(AsyncTaskMapper asyncTaskMapper) {
    log.info("[AsyncTask-AutoConfig] 注册 JDBC 异步任务存储（生产环境）");
    return new JdbcAsyncTaskStore(asyncTaskMapper);
  }

  /**
   * 注册内存存储（开发环境 / fallback）。
   *
   * <p>仅当容器中不存在其他 {@link AsyncTaskStore} 实现时激活，
   * 作为默认 fallback。
   *
   * @return 内存异步任务存储
   */
  @Bean
  @ConditionalOnMissingBean(AsyncTaskStore.class)
  public InMemoryAsyncTaskStore inMemoryAsyncTaskStore() {
    log.info("[AsyncTask-AutoConfig] 注册内存异步任务存储（开发环境/fallback）");
    return new InMemoryAsyncTaskStore();
  }

  /**
   * 注册存储路由器。
   *
   * <p>依赖当前生效的 {@link AsyncTaskStore} Bean（由上述任一 Bean 提供）。
   * 路由器本身轻量，仅做运行时委托。
   *
   * @param activeStore 当前生效的存储实现
   * @return 存储路由器
   */
  @Bean
  @ConditionalOnMissingBean(AsyncTaskStoreRouter.class)
  public AsyncTaskStoreRouter asyncTaskStoreRouter(AsyncTaskStore activeStore) {
    return new AsyncTaskStoreRouter(activeStore);
  }
}
