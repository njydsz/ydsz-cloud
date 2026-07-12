package com.njydsz.pmis.common.app.util;

import java.util.UUID;

/**
 * App 端请求 ID 生成器
 *
 * <p>使用雪花算法风格的 ID 生成策略，保证分布式唯一性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class RequestIdGenerator {

    private RequestIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成请求追踪 ID
     *
     * @return 去除连字符的 UUID 字符串
     */
    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
