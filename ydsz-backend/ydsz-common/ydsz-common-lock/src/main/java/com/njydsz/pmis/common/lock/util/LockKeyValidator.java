package com.njydsz.common.lock.util;

/**
 * 锁键校验工具类
 *
 * <p>对用户通过 SpEL 或静态字符串传入的 lockKey 进行校验，防止：
 * <ul>
 *   <li>键过长（超过 512 字符）影响 Redis 性能或导致 Redis 键过长告警</li>
 *   <li>包含换行符、控制字符等非法字符，影响序列化和日志可读性</li>
 *   <li>包含空字符串或 null 值</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class LockKeyValidator {

    /**
     * 锁键最大长度（Redis 建议键长度不超过 512 字符）
     */
    private static final int MAX_KEY_LENGTH = 512;

    private LockKeyValidator() {
    }

    /**
     * 校验锁键合法性
     *
     * @param lockKey 锁的键
     * @throws IllegalArgumentException 当锁键为空、过长或包含非法字符时
     */
    public static void validate(String lockKey) {
        if (lockKey == null || lockKey.isEmpty()) {
            throw new IllegalArgumentException("【分布式锁】锁键不能为空");
        }
        if (lockKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "【分布式锁】锁键长度超过最大限制 " + MAX_KEY_LENGTH + " | actualLength=" + lockKey.length());
        }
        for (int i = 0; i < lockKey.length(); i++) {
            char c = lockKey.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c == '\0') {
                throw new IllegalArgumentException(
                        "【分布式锁】锁键包含非法控制字符 | charCode=" + (int) c + " | index=" + i);
            }
        }
    }

    /**
     * 清理锁键中的非法字符（替换为下划线）
     *
     * @param lockKey 原始锁键
     * @return 清理后的锁键
     */
    public static String sanitize(String lockKey) {
        if (lockKey == null) {
            return "";
        }
        if (lockKey.length() > MAX_KEY_LENGTH) {
            lockKey = lockKey.substring(0, MAX_KEY_LENGTH);
        }
        StringBuilder sb = new StringBuilder(lockKey.length());
        for (int i = 0; i < lockKey.length(); i++) {
            char c = lockKey.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c == '\0') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
