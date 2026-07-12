package com.njydsz.pmis.common.util;

import java.util.UUID;

/**
 * UUID 工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class UUIDUtils {

    private UUIDUtils() {
    }

    /**
     * 生成不带横线的 UUID
     *
     * @return 32位 UUID 字符串
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成带横线的 UUID
     *
     * @return 36位 UUID 字符串
     */
    public static String uuidWithHyphen() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成有序 UUID（基于时间戳前缀）
     *
     * @return 32位有序 UUID 字符串
     */
    public static String sequentialUUID() {
        long timestamp = System.currentTimeMillis();
        String timestampHex = String.format("%013x", timestamp);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(13);
        return timestampHex + randomPart;
    }
}
