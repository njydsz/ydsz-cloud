package com.njydsz.common.tenant.async;

import org.springframework.core.task.TaskDecorator;

import com.njydsz.common.core.context.TenantContext;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;

/**
 * 线程池任务装饰器：自动传播租户上下文到异步线程。
 *
 * <p>配置到所有 {@code ThreadPoolTaskExecutor} 上：
 *
 * <pre>
 * executor.setTaskDecorator(tenantContextTaskDecorator);
 * </pre>
 *
 * <p>上下文传播依赖 {@code ydsz-common-core} 中 {@code RequestContext} 的 TTL 机制
 * （{@link com.alibaba.ttl.TransmittableThreadLocal}）实现自动跨线程传播，
 * 本类仅处理<b>兜底</b>逻辑：
 *
 * <ul>
 *   <li>父线程有 TTL 上下文 → TTL 自动 copy 到子线程（无需本类操作）
 *   <li>父线程无上下文（定时任务/内部调用/MQ Consumer）→ 注入系统租户，防止 fail-closed
 * </ul>
 *
 * <p>与旧版相比：移除了冗余的 {@code RequestContext.snapshot()/restore()} 手动同步代码，
 * 消除双轨并存导致的数据不一致风险。清理操作也简化为仅清理本装饰器注入的系统租户。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Bean
 * public TaskExecutor taskExecutor(TenantContextTaskDecorator decorator) {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setCorePoolSize(4);
 *     executor.setMaxPoolSize(8);
 *     executor.setTaskDecorator(decorator);
 *     return executor;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class TenantContextTaskDecorator implements TaskDecorator {

  private final TenantProperties properties;

  public TenantContextTaskDecorator(TenantProperties properties) {
    this.properties = properties;
  }

  @Override
  public Runnable decorate(Runnable runnable) {
    // TTL 已通过 RequestContext 中的 TransmittableThreadLocal 自动跨线程传播
    // 仅在无上下文时兜底为系统租户，避免异步场景触发 fail-closed 拒绝 SQL
    return () -> {
      TenantContext current = TenantContextHolder.get();
      boolean appliedFallback = false;
      if (current == null) {
        TenantContextHolder.set(TenantContext.system(properties.getSystemTenantId()));
        appliedFallback = true;
      }
      try {
        runnable.run();
      } finally {
        // 兜底场景需要清理；TTL 传播场景由 TTL copy() 保证隔离
        if (appliedFallback) {
          TenantContextHolder.clear();
        }
      }
    };
  }
}
