package com.njydsz.common.tenant.async;

import java.util.Map;
import org.springframework.core.task.TaskDecorator;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;

/**
 * 线程池任务装饰器：自动传播租户上下文到异步线程。
 *
 * <p>配置到所有 {@code ThreadPoolTaskExecutor} 上：
 * <pre>
 * executor.setTaskDecorator(tenantContextTaskDecorator);
 * </pre>
 *
 * <p>传播策略：
 * <ul>
 *   <li>父线程有上下文 → snapshot → restore（传播用户租户）</li>
 *   <li>父线程无上下文 → 系统租户（定时任务/内部调用）</li>
 * </ul>
 *
 * <p>基于 {@link RequestContext} 的快照/恢复机制实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    private final TenantProperties properties;

    public TenantContextTaskDecorator(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        // 捕获父线程上下文快照
        Map<String, Object> snapshot = RequestContext.snapshot();

        return () -> {
            if (snapshot != null && !snapshot.isEmpty()) {
                // 传播父线程的租户上下文
                RequestContext.restore(snapshot);
                // 恢复快照时同步 tenantId 字符串（bridgeToMdc 兼容）
                TenantContext ctx = TenantContextHolder.get();
                if (ctx != null) {
                    RequestContext.setTenantId(ctx.getTenantId());
                }
            } else {
                // 无父线程上下文 → 系统租户
                TenantContextHolder.set(TenantContext.system(properties.getSystemTenantId()));
            }
            try {
                runnable.run();
            } finally {
                RequestContext.clear();
            }
        };
    }
}
