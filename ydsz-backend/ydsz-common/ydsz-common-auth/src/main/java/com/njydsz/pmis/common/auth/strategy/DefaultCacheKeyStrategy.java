package com.njydsz.common.auth.strategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

import com.njydsz.common.auth.util.AuthDigestUtils;

/**
 * 默认缓存 Key 生成策略。
 *
 * <p>使用 SHA-256 摘要生成缓存 Key，避免角色编码中包含 {@code :} 或 {@code ,} 字符
 * 导致 {@link com.njydsz.common.auth.service.RbacPermissionEvaluator#clearCachesByRoleCodes(String)}
 * 解析 Key 时产生分隔符冲突。
 *
 * <p>Key 格式：{@code auth:rp:<sha256(tenantId|sortedRole1,role2,...)>}
 *
 * @since 1.0.0

 */
public class DefaultCacheKeyStrategy implements CacheKeyStrategy {

    /**
     * 默认租户前缀
     */
    private static final String DEFAULT_TENANT_PREFIX = "__default__";

    private static final String KEY_PREFIX = "auth:rp:";

    @Override
    public String generate(String tenantId, Set<String> roleCodes) {
        String prefix = (tenantId != null && !tenantId.isEmpty()) ? tenantId : DEFAULT_TENANT_PREFIX;
        // 使用 TreeSet 保证角色顺序一致性
        String rolesPart = String.join(",", new TreeSet<>(roleCodes));
        String raw = prefix + "|" + rolesPart;
        return KEY_PREFIX + AuthDigestUtils.sha256Hex(raw);
    }

    /**
     * 计算 SHA-256 摘要并转为十六进制字符串。
     *
     * @deprecated 使用 {@link AuthDigestUtils#sha256Hex(String)}
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    private static String sha256(String input) {
        return AuthDigestUtils.sha256Hex(input);
    }
}
