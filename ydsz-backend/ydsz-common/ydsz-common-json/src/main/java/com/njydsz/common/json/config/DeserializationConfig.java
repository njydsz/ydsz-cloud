package com.njydsz.common.json.config;

import java.util.Set;

import com.njydsz.common.json.autotype.AutoTypeChecker;

/**
 * JSON 反序列化安全配置（委托至 {@link AutoTypeChecker} 和 {@link YdszJsonConfig}）
 *
 * <p>此类为向后兼容的适配器，所有类型检查和白/黑名单管理均委托给
 * {@link AutoTypeChecker} 统一处理，深度限制委托至 {@link YdszJsonConfig#getMaxDepth()}。</p>
 *
 * <p><b>安全特性:</b></p>
 * <ul>
 *   <li>类型白名单: 通过 {@link AutoTypeChecker#addToWhitelist(String)} 管理</li>
 *   <li>类型黑名单: 通过 {@link AutoTypeChecker#addToBlacklist(String)} 管理</li>
 *   <li>解析深度限制: 委托至 {@link YdszJsonConfig#getMaxDepth()}，默认 256</li>
 * </ul>
 *
 * @deprecated 此类为冗余适配器，所有功能均委托至 {@link AutoTypeChecker} 和 {@link YdszJsonConfig}。
 *             请直接使用 {@link AutoTypeChecker} 管理白/黑名单，使用 {@link YdszJsonConfig} 管理深度限制。
 *             后续版本将删除此类。
 * @author ydsz-team
 * @since 1.0.0
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public class DeserializationConfig {
    
    private static final DeserializationConfig INSTANCE = new DeserializationConfig();
    
    public static final int DEFAULT_MAX_DEPTH = 256;
    
    public static final int MAX_ALLOWED_DEPTH = 256;
    
    private volatile int maxDepth = DEFAULT_MAX_DEPTH; // 同步自 YdszJsonConfig.maxDepth
    
    private volatile boolean whitelistEnabled = false;
    
    static {
        // 初始黑名单已由 AutoTypeChecker 统一管理，此处不再重复注册
    }
    
    private DeserializationConfig() {
    }
    
    public static DeserializationConfig getInstance() {
        return INSTANCE;
    }
    
    public void enableWhitelist() {
        this.whitelistEnabled = true;
        AutoTypeChecker.setSafeMode(true);
    }
    
    public void disableWhitelist() {
        this.whitelistEnabled = false;
    }
    
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }
    
    public void addToWhitelist(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("类名不能为空");
        }
        AutoTypeChecker.addToWhitelist(className);
    }
    
    public void removeFromWhitelist(String className) {
        AutoTypeChecker.removeFromWhitelist(className);
    }
    
    public Set<String> getWhitelist() {
        return AutoTypeChecker.getExplicitWhitelist();
    }
    
    public void addToBlacklist(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("类名不能为空");
        }
        AutoTypeChecker.addToBlacklist(className);
    }
    
    public void removeFromBlacklist(String className) {
        AutoTypeChecker.removeFromBlacklist(className);
    }
    
    public Set<String> getBlacklist() {
        return AutoTypeChecker.getBuiltinBlacklist();
    }
    
    public void setMaxDepth(int maxDepth) {
        if (maxDepth <= 0 || maxDepth > MAX_ALLOWED_DEPTH) {
            throw new IllegalArgumentException(
                "maxDepth 必须在 [1, " + MAX_ALLOWED_DEPTH + "] 之间,当前值: " + maxDepth);
        }
        this.maxDepth = maxDepth;
    }
    
    /**
     * 获取最大解析深度。
     *
     * <p>优先返回 {@link YdszJsonConfig#getMaxDepth()} 的值，确保全局统一。</p>
     *
     * @return 最大解析深度
     */
    public int getMaxDepth() {
        return YdszJsonConfig.getInstance().getMaxDepth();
    }
    
    public boolean isTypeAllowed(String className) {
        return AutoTypeChecker.isTypeAllowed(className);
    }
    
    public void clearAll() {
        maxDepth = DEFAULT_MAX_DEPTH;
        whitelistEnabled = false;
        AutoTypeChecker.setSafeMode(true);
    }
    
    public String getSecurityStatus() {
        return String.format(
            "SafeMode: %s, MaxDepth: %d",
            AutoTypeChecker.isSafeMode(), maxDepth);
    }
}
