package com.njydsz.pmis.common.json.config;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON 反序列化安全配置
 * 
 * <p>提供类型白名单/黑名单和解析深度限制,防止反序列化攻击。</p>
 * 
 * <p><b>安全特性:</b></p>
 * <ul>
 *   <li>类型白名单: 仅允许反序列化指定类型的对象</li>
 *   <li>类型黑名单: 禁止反序列化危险类型(如 ProcessBuilder)</li>
 *   <li>解析深度限制: 防止嵌套过深导致栈溢出</li>
 * </ul>
 * 
 * <p><b>使用示例:</b></p>
 * <pre>
 * DeserializationConfig config = DeserializationConfig.getInstance();
 * config.addToWhitelist("com.example.User");
 * config.addToBlacklist("java.lang.ProcessBuilder");
 * config.setMaxDepth(64);
 * </pre>
 * 
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class DeserializationConfig {
    
    private static final DeserializationConfig INSTANCE = new DeserializationConfig();
    
    public static final int DEFAULT_MAX_DEPTH = 64;
    
    public static final int MAX_ALLOWED_DEPTH = 256;
    
    private final Set<String> typeWhitelist = ConcurrentHashMap.newKeySet();
    
    private final Set<String> typeBlacklist = ConcurrentHashMap.newKeySet();
    
    private volatile int maxDepth = DEFAULT_MAX_DEPTH;
    
    private volatile boolean whitelistEnabled = false;
    
    static {
        INSTANCE.typeBlacklist.add("java.lang.ProcessBuilder");
        INSTANCE.typeBlacklist.add("java.lang.Runtime");
        INSTANCE.typeBlacklist.add("java.lang.ClassLoader");
        INSTANCE.typeBlacklist.add("java.net.URLClassLoader");
        INSTANCE.typeBlacklist.add("javax.script.ScriptEngineManager");
        INSTANCE.typeBlacklist.add("org.apache.commons.collections.functors.InvokerTransformer");
        INSTANCE.typeBlacklist.add("org.apache.commons.collections.Transformer");
        INSTANCE.typeBlacklist.add("org.apache.commons.collections4.functors.InvokerTransformer");
        INSTANCE.typeBlacklist.add("org.apache.commons.beanutils.BeanComparator");
        INSTANCE.typeBlacklist.add("java.util.PriorityQueue");
        INSTANCE.typeBlacklist.add("java.rmi.server.UnicastRemoteObject");
        INSTANCE.typeBlacklist.add("java.beans.EventHandler");
        INSTANCE.typeBlacklist.add("com.sun.rowset.JdbcRowSetImpl");
    }
    
    private DeserializationConfig() {
    }
    
    public static DeserializationConfig getInstance() {
        return INSTANCE;
    }
    
    public void enableWhitelist() {
        this.whitelistEnabled = true;
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
        typeWhitelist.add(className);
    }
    
    public void removeFromWhitelist(String className) {
        typeWhitelist.remove(className);
    }
    
    public Set<String> getWhitelist() {
        return Collections.unmodifiableSet(typeWhitelist);
    }
    
    public void addToBlacklist(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("类名不能为空");
        }
        typeBlacklist.add(className);
    }
    
    public void removeFromBlacklist(String className) {
        typeBlacklist.remove(className);
    }
    
    public Set<String> getBlacklist() {
        return Collections.unmodifiableSet(typeBlacklist);
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
        if (className == null || className.isEmpty()) {
            return false;
        }
        
        if (typeBlacklist.contains(className)) {
            return false;
        }
        
        if (whitelistEnabled) {
            if (typeWhitelist.contains(className)) {
                return true;
            }
            for (String allowed : typeWhitelist) {
                if (className.startsWith(allowed)) {
                    return true;
                }
            }
            return false;
        }
        
        return true;
    }
    
    public void clearAll() {
        typeWhitelist.clear();
        typeBlacklist.clear();
        maxDepth = DEFAULT_MAX_DEPTH;
        whitelistEnabled = false;
        
        typeBlacklist.add("java.lang.ProcessBuilder");
        typeBlacklist.add("java.lang.Runtime");
        typeBlacklist.add("java.lang.ClassLoader");
        typeBlacklist.add("java.net.URLClassLoader");
        typeBlacklist.add("javax.script.ScriptEngineManager");
    }
    
    public String getSecurityStatus() {
        return String.format(
            "Whitelist: %s (enabled=%s), Blacklist: %d, MaxDepth: %d",
            typeWhitelist.size(), whitelistEnabled, typeBlacklist.size(), maxDepth);
    }
}
