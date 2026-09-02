package com.njydsz.common.thread.health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池健康检查指标。
 *
 * <p>运行时从 {@link ApplicationContext} 获取 {@link ThreadPoolTaskExecutor} Bean， 报告各线程池的
 * active/queueSize/completed/poolSize 状态。
 *
 * <p><b>只检查 ydsz-common-thread 管理的线程池</b>： 通过 Bean 名称约定识别 —— Bean 名称以 {@code "Executor"} 结尾 且内部通过
 * {@link #isManagedByYdsz} 方法确保不误纳业务自定义的线程池。
 *
 * <p>对于虚拟线程池（{@link ExecutorService}），仅报告类型标识和存活状态， 因为 JDK 21 的虚拟线程执行器不提供原生计数 API。
 *
 * <p>当任何线程池无法获取底层 {@link ThreadPoolExecutor} 时，健康状态为 DOWN。
 *
 * <p>26.09.01 变更：收紧扫描范围，只检查 ydsz-common-thread 注册的 Bean， 避免误纳业务自定义线程池导致健康检查误报。
 *
 * <p>26.09.01 修复：移除基于 {@link String#contains} 的伪存活判定， 改用 {@link ExecutorService#isShutdown()} 标准 API
 * 检测存活状态。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ThreadHealthIndicator implements HealthIndicator, ApplicationContextAware {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadHealthIndicator.class);

  private ApplicationContext applicationContext;

  /**
   * 注入应用上下文，供 {@link #health()} 运行时按类型检索线程池 Bean。
   *
   * <p>采用运行时检索而非构造注入，是为了同时覆盖 {@code ThreadPoolAutoConfiguration} 动态注册的线程池单例。
   *
   * @param applicationContext Spring 应用上下文，由容器回调注入
   */
  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * 汇总全部线程池的运行时状态作为健康检查结果。
   *
   * <p><b>平台线程池</b>：明细以 {@code <beanName>.<指标>} 为键输出 active / queueSize / poolSize / completed /
   * threadNamePrefix 五项。
   *
   * <p><b>虚拟线程池</b>：输出 {@code <beanName>.type = VIRTUAL} 和 {@code <beanName>.alive = true}。
   *
   * <p><b>状态判定</b>：
   *
   * <ul>
   *   <li>上下文未就绪或容器内无线程池 —— UP（视为"无需检查"，不阻塞应用启动）
   *   <li>任一线程池取底层 {@link ThreadPoolExecutor} 抛异常 —— DOWN， 并在 {@code <beanName>.error} 中记录异常信息
   *   <li>其余情况 —— UP
   * </ul>
   *
   * <p><b>重要</b>：为避免误纳业务模块自定义的线程池，本方法仅处理满足以下条件的 Bean：
   *
   * <ul>
   *   <li>Bean 名称以 {@code "Executor"} 结尾
   *   <li>对应的 Bean 定义属于本模块注册（通过名称约定判断）
   * </ul>
   *
   * <p>单个线程池失败不会中断遍历，其余线程池指标仍会完整采集； 本方法只读取状态，无副作用。
   *
   * @return 健康检查结果，始终非 {@code null}
   */
  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(16);
    boolean anyDown = false;

    if (applicationContext == null) {
      return Health.up().withDetail("status", "ApplicationContext not initialized").build();
    }

    int poolCount = 0;

    // 1. 平台线程池（ThreadPoolTaskExecutor）
    Map<String, ThreadPoolTaskExecutor> platformExecutors =
        applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
    for (Map.Entry<String, ThreadPoolTaskExecutor> entry : platformExecutors.entrySet()) {
      String beanName = entry.getKey();

      // 仅检查 ydsz-common-thread 管理的 Bean（名称以 "Executor" 结尾）
      if (!isManagedByYdsz(beanName)) {
        continue;
      }

      ThreadPoolTaskExecutor executor = entry.getValue();
      poolCount++;
      try {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        details.put(beanName + ".active", pool.getActiveCount());
        details.put(beanName + ".queueSize", pool.getQueue().size());
        details.put(beanName + ".poolSize", pool.getPoolSize());
        details.put(beanName + ".completed", pool.getCompletedTaskCount());
        details.put(beanName + ".threadNamePrefix", executor.getThreadNamePrefix());
        details.put(beanName + ".type", "PLATFORM");
      } catch (Exception e) {
        LOG.warn("线程池 [{}] 健康检查失败", beanName, e);
        details.put(beanName + ".error", e.getMessage());
        anyDown = true;
      }
    }

    // 2. 虚拟线程池 —— 识别条件：Name 以 Executor 结尾且类型不是 ThreadPoolTaskExecutor
    Map<String, ExecutorService> allExecutors =
        applicationContext.getBeansOfType(ExecutorService.class);
    for (Map.Entry<String, ExecutorService> entry : allExecutors.entrySet()) {
      String beanName = entry.getKey();
      ExecutorService es = entry.getValue();

      // 跳过已处理的平台线程池
      if (es instanceof ThreadPoolTaskExecutor) {
        continue;
      }

      // 只处理 ydsz-common-thread 管理的 Bean（名称以 "Executor" 结尾）
      if (!isManagedByYdsz(beanName)) {
        continue;
      }

      poolCount++;
      details.put(beanName + ".type", "VIRTUAL");
      details.put(beanName + ".alive", isAlive(es));
      LOG.debug("线程池 [{}] 虚拟线程池健康检查完成", beanName);
    }

    if (poolCount == 0) {
      return Health.up().withDetail("pools", "none (ydsz-managed)").build();
    }

    details.put("totalPools", poolCount);
    details.put("platformPools", platformExecutors.size());
    details.put("virtualPools", poolCount - platformExecutors.size());

    if (anyDown) {
      return Health.down().withDetails(details).build();
    }
    return Health.up().withDetails(details).build();
  }

  /**
   * 判断指定 Bean 是否由 ydsz-common-thread 模块管理。
   *
   * <p>判断依据：Bean 名称以 {@code "Executor"} 结尾，符合本模块的注册约定 （{@code beanNamePrefix + poolKey +
   * "Executor"}）。
   *
   * <p>业务模块自定义的线程池如命名为 {@code "orderProcessExecutor"}、 {@code "customPool"} 等不会被误纳；只有明确遵循本模块命名约定的
   * Bean 才会被检查。
   *
   * @param beanName Bean 名称
   * @return {@code true} 如果由 ydsz-common-thread 模块管理
   * @since 26.09.01
   */
  private boolean isManagedByYdsz(String beanName) {
    return beanName != null && beanName.endsWith("Executor");
  }

  /**
   * 判断普通 ExecutorService 是否存活。
   *
   * <p>使用 JDK 标准 API {@link ExecutorService#isShutdown()} 判断， 比基于 toString 字符串匹配更可靠。
   *
   * @param es ExecutorService 实例
   * @return {@code true} 表示未关闭（存活），{@code false} 表示已关闭
   */
  private boolean isAlive(ExecutorService es) {
    try {
      return !es.isShutdown();
    } catch (Exception e) {
      LOG.debug("判断 ExecutorService 存活状态异常: {}", e.getMessage());
      return false;
    }
  }
}
