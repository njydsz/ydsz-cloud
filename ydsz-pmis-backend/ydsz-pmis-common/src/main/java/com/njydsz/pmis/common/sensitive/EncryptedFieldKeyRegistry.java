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

    /**
     * 注册加密字段密钥 (AES-256 32 字节)
     *
     * @param keyRef 密钥引用
     * @param key    32 字节 AES 密钥
     * @throws IllegalArgumentException keyRef 空 / key 为 null / 长度非 32
     */
    public static void register(String keyRef, byte[] key) {
        if (keyRef == null || keyRef.isBlank()) {
            throw new IllegalArgumentException("keyRef 不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("AES-256 加密字段密钥必须 32 字节, 实际: " + key.length);
        }
        KEYS.put(keyRef, key.clone());
    }

    /**
     * 注册国密 SM4 加密字段密钥 (16 字节)
     *
     * @param keyRef 密钥引用
     * @param key    16 字节 SM4 密钥
     * @throws IllegalArgumentException keyRef 空 / key 为 null / 长度非 16
     */
    public static void registerSm4(String keyRef, byte[] key) {
        if (keyRef == null || keyRef.isBlank()) {
            throw new IllegalArgumentException("keyRef 不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (key.length != 16) {
            throw new IllegalArgumentException("SM4 加密字段密钥必须 16 字节, 实际: " + key.length);
        }
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
            // 32 字节 (UTF-8) 字符串: "pmis-default-aes-key-32bytes!!!!" (32 chars)
            DEFAULT_KEY = "pmis-default-aes-key-32bytes!!!!".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return DEFAULT_KEY;
    }

    /** 用于单元测试清理 */
    static void resetForTest() {
        KEYS.clear();
        DEFAULT_KEY = null;
    }
}
