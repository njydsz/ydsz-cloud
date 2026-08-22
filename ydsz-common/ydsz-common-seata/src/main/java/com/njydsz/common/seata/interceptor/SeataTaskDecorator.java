package com.njydsz.common.seata.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;

import com.njydsz.common.seata.context.XidContextHolder;

/**
 * Seata XID 线程池任务装饰器
 *
 * <p>解决事务上下文在异步线程池中的传递问题。
 *
 * <p><b>P1-1 修复</b>：此前使用普通 {@link ThreadLocal} 存储 XID，在 {@code @Async}、 {@code
 * CompletableFuture}、手动线程池场景下 XID 丢失，导致事务链路断裂。
 *
 * <p>通过 Spring {@link TaskDecorator} 机制，在任务提交时捕获当前线程的 XID， 在任务执行时恢复 XID 上下文，确保事务链路在异步线程中延续。
 *
 * <p><b>P2-3 修复</b>：改为依赖独立的 {@link XidContextHolder}， 不再使用 {@code AbstractTransactionManager}
 * 的包级私有方法。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 配置线程池时注入 TaskDecorator
 * @Bean("taskExecutor")
 * public Executor taskExecutor() {
 *     // 从 ydsz.thread.pools.* 注入托管线程池（禁止业务代码自行 new 线程池，云顶规范 15.4），
 *     // 仅通过 setTaskDecorator(new SeataTaskDecorator()) 注入 Seata 上下文装饰器即可。
 * }
 * }</pre>
 *
 * <p>或通过 {@link SeataExecutors} 工厂方法快速创建已装饰的线程池。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SeataTaskDecorator implements TaskDecorator {

  private static final Logger LOG = LoggerFactory.getLogger(SeataTaskDecorator.class);

  /**
   * 装饰 Runnable 任务，在执行前恢复 XID 上下文，执行后清除。
   *
   * <p>流程：
   *
   * <ol>
   *   <li>捕获提交线程的当前 XID（如有）
   *   <li>包装任务，在子线程中绑定 XID
   *   <li>任务执行完毕后解绑 XID（无论成功失败）
   * </ol>
   *
   * @param runnable 原始任务
   * @return 包装后的任务，包含 XID 传递逻辑
   */
  @Override
  public Runnable decorate(Runnable runnable) {
    // 捕获提交线程的 XID 上下文
    String xid = XidContextHolder.getXid();

    return () -> {
      // 在子线程中恢复 XID 上下文
      if (xid != null) {
        XidContextHolder.setXid(xid);
        LOG.debug("XID restored in async thread: {}", xid);
      }
      try {
        runnable.run();
      } finally {
        // 无论成功失败都解绑，防止线程复用污染
        XidContextHolder.remove();
        LOG.debug("XID unbound in async thread: {}", xid);
      }
    };
  }
}
