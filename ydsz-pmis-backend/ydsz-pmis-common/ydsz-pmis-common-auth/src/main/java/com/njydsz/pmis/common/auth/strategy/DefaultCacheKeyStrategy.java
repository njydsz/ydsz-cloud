package com.njydsz.pmis.common.auth.strategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/**
 * 默认缓存 Key 生成策略。
 *
 * <p>使用 SHA-256 摘要生成缓存 Key，避免角色编码中包含 {@code :} 或 {@code ,} 字符
 * 导致 {@link com.njydsz.pmis.common.auth.service.RbacPermissionEvaluator#clearCachesByRoleCodes(String)}
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
        return KEY_PREFIX + sha256(raw);
    }

    /**
     * 计算 SHA-256 摘要并转为十六进制字符串。
     *
     * @param input 输入字符串
     * @return 十六进制编码的摘要
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
