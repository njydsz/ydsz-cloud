package com.njydsz.pmis.common.json.config;

import java.util.Set;

import com.njydsz.pmis.common.json.autotype.AutoTypeChecker;

/**
 * JSON 反序列化安全配置（委托至 {@link AutoTypeChecker}）
 *
 * <p>此类为向后兼容的适配器，所有类型检查和白/黑名单管理均委托给
 * {@link AutoTypeChecker} 统一处理，消除双重安全检查的冗余。</p>
 *
 * <p><b>安全特性:</b></p>
 * <ul>
 *   <li>类型白名单: 通过 {@link AutoTypeChecker#addToWhitelist(String)} 管理</li>
 *   <li>类型黑名单: 通过 {@link AutoTypeChecker#addToBlacklist(String)} 管理</li>
 *   <li>解析深度限制: 委托至 {@link com.njydsz.pmis.common.json.config.JsonConfig#getMaxDepth()}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public class DeserializationConfig {
    
    private static final DeserializationConfig INSTANCE = new DeserializationConfig();
    
    public static final int DEFAULT_MAX_DEPTH = 64;
    
    public static final int MAX_ALLOWED_DEPTH = 256;
    
    private volatile int maxDepth = DEFAULT_MAX_DEPTH;
    
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
    
    public int getMaxDepth() {
        return maxDepth;
    }
    
    public boolean isTypeAllowed(String className) {
        return AutoTypeChecker.isTypeAllowed(className);
    }
    
    public void clearAll() {
        maxDepth = DEFAULT_MAX_DEPTH;
        whitelistEnabled = false;
    }
    
    public String getSecurityStatus() {
        return String.format(
            "SafeMode: %s, MaxDepth: %d",
            AutoTypeChecker.isSafeMode(), maxDepth);
    }
}
