package com.njydsz.pmis.common.sensitive;

import com.njydsz.pmis.common.util.CryptoUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加密字段密钥注册中心
 *
 * <p>集中管理 {@link EncryptedField} 引用 key 到实际字节密钥的映射。
 * 业务启动时按需注册: {@code EncryptedFieldKeyRegistry.register("pmis.crypto.aes-key", secret32Bytes)}
 */
public final class EncryptedFieldKeyRegistry {

    private static final Map<String, byte[]> KEYS = new ConcurrentHashMap<>();
    private static volatile byte[] DEFAULT_KEY;

    private EncryptedFieldKeyRegistry() {
    }

    public static void register(String keyRef, byte[] key) {
        if (keyRef == null || keyRef.isBlank() || key == null) return;
        KEYS.put(keyRef, key.clone());
    }

    public static byte[] get(String keyRef) {
        if (keyRef == null) return defaultKey();
        byte[] k = KEYS.get(keyRef);
        if (k != null) return k;
        return defaultKey();
    }

    public static boolean has(String keyRef) {
        return keyRef != null && KEYS.containsKey(keyRef);
    }

    public static void clear() {
        KEYS.clear();
        DEFAULT_KEY = null;
    }

    public static synchronized void setDefaultKey(byte[] key) {
        if (key == null) {
            DEFAULT_KEY = null;
            return;
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("默认 AES 密钥必须 32 字节");
        }
        DEFAULT_KEY = key.clone();
    }

    private static byte[] defaultKey() {
        if (DEFAULT_KEY == null) {
            // 仅用于本地开发/单元测试, 真实部署应通过 Nacos/ConfigMap 注入 32 字节密钥
            DEFAULT_KEY = "pmis-default-32byte-aes-key!!!!!!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return DEFAULT_KEY;
    }

    /** 用于单元测试清理 */
    static void resetForTest() {
        KEYS.clear();
        DEFAULT_KEY = null;
    }
}
