package com.njydsz.pmis.common.auth.strategy;

import java.util.Set;
import java.util.TreeSet;

/**
 * 默认缓存 Key 生成策略。
 *
 * <p>使用 {@code tenantId:role1,role2} 格式生成缓存 Key。
 * 如果租户 ID 为空，使用 {@code __default__} 作为前缀。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class DefaultCacheKeyStrategy implements CacheKeyStrategy {

    /**
     * 默认租户前缀
     */
    private static final String DEFAULT_TENANT_PREFIX = "__default__";

    @Override
    public String generate(String tenantId, Set<String> roleCodes) {
        String prefix = (tenantId != null && !tenantId.isEmpty()) ? tenantId : DEFAULT_TENANT_PREFIX;
        return prefix + ":" + String.join(",", new TreeSet<>(roleCodes));
    }
}
