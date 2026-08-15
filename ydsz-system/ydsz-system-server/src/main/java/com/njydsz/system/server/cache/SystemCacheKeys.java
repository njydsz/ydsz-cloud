package com.njydsz.system.server.cache;

import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 系统模块缓存键工具。
 *
 * <p>统一为缓存键注入 <b>租户命名空间</b>，避免多租户场景下不同租户的同名 key
 * 命中同一份缓存（跨租户数据串味 / 泄露）。
 *
 * <p><b>键格式：</b>{@code {tenantId|default}:{prefix}{key}}
 * <ul>
 *   <li>租户上下文存在：使用真实 {@code tenantId} 作为前缀</li>
 *   <li>租户上下文缺失（定时任务 / 内部调用 / 公开接口）：使用 {@code default} 占位，
 *       保证键始终确定性生成</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SystemCacheKeys {

    /** 租户上下文缺失时使用的默认命名空间 */
    private static final String DEFAULT_TENANT = "default";

    private SystemCacheKeys() {
    }

    /**
     * 生成带租户命名空间的缓存键。
     *
     * @param prefix 业务前缀（如 {@code system:config:value:}），非空
     * @param key    业务键（如 {@code configKey}），可为空字符串
     * @return 完整的缓存键，格式 {@code {tenantId|default}:{prefix}{key}}
     */
    public static String of(String prefix, String key) {
        String tenantId = TenantContextHolder.getTenantId();
        String tenant = tenantId != null && !tenantId.isBlank() ? tenantId : DEFAULT_TENANT;
        return tenant + ":" + prefix + (key != null ? key : "");
    }
}
