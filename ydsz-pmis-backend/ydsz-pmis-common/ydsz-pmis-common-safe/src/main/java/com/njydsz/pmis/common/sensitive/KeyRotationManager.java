package com.njydsz.pmis.common.sensitive;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P2-14: 密钥轮换管理器
 *
 * <p>支持加密字段密钥的无停机轮换，满足数据安全合规要求（密钥定期更换）。
 *
 * <h3>轮换流程</h3>
 * <ol>
 *   <li>注册新密钥（newKey），旧密钥保留为 "previous"</li>
 *   <li>新数据加密使用新密钥</li>
 *   <li>解密时先尝试新密钥，失败后尝试旧密钥</li>
 *   <li>后台批量重新加密旧数据（通过 {@code EncryptedFieldMigrationService}）</li>
 *   <li>迁移完成后移除旧密钥</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 注册初始密钥
 * KeyRotationManager.registerKey("pmis.crypto.aes-key", oldKey);
 *
 * // 轮换密钥
 * KeyRotationManager.rotateKey("pmis.crypto.aes-key", newKey);
 *
 * // 解密时自动尝试新/旧密钥
 * String plaintext = KeyRotationManager.decryptWithFallback(cipher, "pmis.crypto.aes-key");
 *
 * // 迁移完成后移除旧密钥
 * KeyRotationManager.completeRotation("pmis.crypto.aes-key");
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
public class KeyRotationManager {

    /** keyRef → 当前密钥 */
    private static final Map<String, byte[]> currentKeys = new ConcurrentHashMap<>();

    /** keyRef → 旧密钥（轮换期间保留，用于解密旧数据） */
    private static final Map<String, byte[]> previousKeys = new ConcurrentHashMap<>();

    /** keyRef → 轮换状态 */
    private static final Map<String, RotationStatus> rotationStatus = new ConcurrentHashMap<>();

    /** 轮换状态枚举 */
    public enum RotationStatus {
        /** 无轮换进行中 */
        IDLE,
        /** 轮换进行中（新密钥已注册，旧数据待迁移） */
        IN_PROGRESS,
        /** 轮换已完成（旧密钥可移除） */
        COMPLETED
    }

    private KeyRotationManager() {
    }

    /**
     * 注册密钥（首次注册或覆盖现有密钥）
     *
     * @param keyRef 密钥引用
     * @param key    32 字节 AES 密钥
     */
    public static void registerKey(String keyRef, byte[] key) {
        validateKey(key);
        currentKeys.put(keyRef, key.clone());
        rotationStatus.put(keyRef, RotationStatus.IDLE);
        log.info("[KeyRotation] 密钥已注册: keyRef={}", keyRef);
    }

    /**
     * 轮换密钥
     *
     * <p>将当前密钥移至 previous，注册新密钥为 current。
     * 轮换期间解密操作会先尝试 current，失败后尝试 previous。
     *
     * @param keyRef 密钥引用
     * @param newKey 新的 32 字节密钥
     */
    public static void rotateKey(String keyRef, byte[] newKey) {
        validateKey(newKey);

        byte[] oldKey = currentKeys.get(keyRef);
        if (oldKey != null) {
            previousKeys.put(keyRef, oldKey);
        }
        currentKeys.put(keyRef, newKey.clone());
        rotationStatus.put(keyRef, RotationStatus.IN_PROGRESS);

        // 记录审计
        DataAccessAuditService auditService = DataAccessAuditService.getInstance();
        if (auditService != null) {
            auditService.recordKeyRotation(keyRef, "AES_GCM");
        }

        log.info("[KeyRotation] 密钥已轮换: keyRef={}, status=IN_PROGRESS", keyRef);
    }

    /**
     * 完成轮换（移除旧密钥）
     *
     * <p>在确认所有旧数据已重新加密后调用。
     *
     * @param keyRef 密钥引用
     */
    public static void completeRotation(String keyRef) {
        previousKeys.remove(keyRef);
        rotationStatus.put(keyRef, RotationStatus.COMPLETED);
        log.info("[KeyRotation] 密钥轮换已完成: keyRef={}, status=COMPLETED", keyRef);
    }

    /**
     * 获取当前密钥
     *
     * @param keyRef 密钥引用
     * @return 当前密钥；未注册返回 null
     */
    public static byte[] getCurrentKey(String keyRef) {
        return currentKeys.get(keyRef);
    }

    /**
     * 获取旧密钥（轮换期间）
     *
     * @param keyRef 密钥引用
     * @return 旧密钥；无轮换或已完成返回 null
     */
    public static byte[] getPreviousKey(String keyRef) {
        return previousKeys.get(keyRef);
    }

    /**
     * 获取轮换状态
     *
     * @param keyRef 密钥引用
     * @return 轮换状态；未注册返回 IDLE
     */
    public static RotationStatus getRotationStatus(String keyRef) {
        return rotationStatus.getOrDefault(keyRef, RotationStatus.IDLE);
    }

    /**
     * 使用当前密钥解密，失败时尝试旧密钥
     *
     * @param cipher 密文
     * @param keyRef 密钥引用
     * @return 解密后的明文
     * @throws SecurityException 解密失败
     */
    public static String decryptWithFallback(String cipher, String keyRef) {
        byte[] currentKey = currentKeys.get(keyRef);
        if (currentKey == null) {
            throw new SecurityException("密钥未注册: " + keyRef);
        }

        // 尝试当前密钥
        try {
            return CryptoUtilAccessor.aesGcmDecrypt(cipher, currentKey);
        } catch (Exception e) {
            // 尝试旧密钥
            byte[] prevKey = previousKeys.get(keyRef);
            if (prevKey != null) {
                try {
                    return CryptoUtilAccessor.aesGcmDecrypt(cipher, prevKey);
                } catch (Exception e2) {
                    throw new SecurityException("密钥轮换期间解密失败（新/旧密钥均无法解密）: " + keyRef, e2);
                }
            }
            throw new SecurityException("解密失败: " + keyRef, e);
        }
    }

    /**
     * 是否需要重新加密（数据使用旧密钥加密）
     *
     * @param cipher 密文
     * @param keyRef 密钥引用
     * @return true 表示数据使用旧密钥加密，需要重新加密
     */
    public static boolean needsReEncryption(String cipher, String keyRef) {
        if (getRotationStatus(keyRef) != RotationStatus.IN_PROGRESS) {
            return false;
        }
        byte[] currentKey = currentKeys.get(keyRef);
        if (currentKey == null) return false;

        try {
            CryptoUtilAccessor.aesGcmDecrypt(cipher, currentKey);
            return false; // 当前密钥可解密，无需重新加密
        } catch (Exception e) {
            return true; // 当前密钥无法解密，可能是旧密钥加密的
        }
    }

    /**
     * 清除所有密钥（仅用于测试）
     */
    static void resetForTest() {
        currentKeys.clear();
        previousKeys.clear();
        rotationStatus.clear();
    }

    private static void validateKey(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("密钥不能为 null");
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("AES-256 密钥必须 32 字节, 实际: " + key.length);
        }
    }

    /**
     * CryptoUtil 访问器（避免循环依赖）
     */
    private static class CryptoUtilAccessor {
        static String aesGcmDecrypt(String cipher, byte[] key) {
            return com.njydsz.pmis.common.util.CryptoUtil.aesGcmDecrypt(cipher, key);
        }
    }
}
