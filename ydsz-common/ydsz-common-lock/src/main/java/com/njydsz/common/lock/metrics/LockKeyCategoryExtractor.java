package com.njydsz.common.lock.metrics;


/**
 * 锁键分类提取器
 *
 * <p>用于从精确的锁键中提取低基数的类别标签，防止高基数锁键导致指标标签膨胀。
 *
 * <p>例如：
 * <ul>
 *   <li>{@code order:12345} → {@code order}</li>
 *   <li>{@code user:profile:67890} → {@code user:profile}</li>
 *   <li>{@code unknown} → {@code unknown}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public interface LockKeyCategoryExtractor {

    /**
     * 从精确锁键中提取类别标签
     *
     * <p>提取规则：取冒号分隔的前缀部分作为类别，确保类别数量有限。
     *
     * @param lockKey 精确锁键
     * @return 类别标签（低基数）
     */
    String extractCategory(String lockKey);

    /**
     * 默认实现：提取锁键的第一段（冒号前的部分）作为类别
     */
    LockKeyCategoryExtractor DEFAULT = lockKey -> {
        if (lockKey == null || lockKey.isEmpty()) {
            return "unknown";
        }
        int colonIndex = lockKey.indexOf(':');
        return colonIndex > 0 ? lockKey.substring(0, colonIndex) : lockKey;
    };
}
