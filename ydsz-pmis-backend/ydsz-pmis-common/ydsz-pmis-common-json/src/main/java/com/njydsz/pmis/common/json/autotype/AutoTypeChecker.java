package com.njydsz.pmis.common.json.autotype;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.pmis.common.json.annotation.JsonClass;
import com.njydsz.pmis.common.json.exception.JsonDeserializationException;

/**
 * AutoType 白名单/黑名单检查器
 *
 * <p>提供安全的反序列化类型校验，防止 RCE 攻击。</p>
 *
 * <p><b>检查规则：</b></p>
 * <ul>
 *   <li>黑名单检查（始终生效）：拒绝已知的危险类，即使 SafeMode=false 也生效</li>
 *   <li>内置基础类型白名单（Java 基础类型、集合、日期等）</li>
 *   <li>标注了 @JsonClass 注解的类自动允许反序列化</li>
 *   <li>通过 addToWhitelist() 显式加入白名单的类</li>
 *   <li>标注了 @JsonClass(autoType=true) 的类及其 seeAlso 子类型</li>
 * </ul>
 *
 * <p><b>安全模式：</b></p>
 * <ul>
 *   <li>SafeMode=true（默认）：只有白名单内的类型才能反序列化</li>
 *   <li>SafeMode=false：允许任意类型反序列化（不推荐，存在 RCE 风险），但黑名单仍然生效</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 显式加入白名单
 * AutoTypeChecker.addToWhitelist("com.example.MyClass");
 *
 * // 检查某个类型是否允许反序列化
 * boolean allowed = AutoTypeChecker.isTypeAllowed(MyClass.class);
 *
 * // 关闭安全模式（不推荐，黑名单仍然生效）
 * AutoTypeChecker.setSafeMode(false);
 *
 * // 添加自定义黑名单
 * AutoTypeChecker.addToBlacklist("com.example.DangerousClass");
 * </pre>
 *
 * @since 1.3.0
 */
public final class AutoTypeChecker {

    private static volatile boolean safeMode = true;

    private static final Set<String> EXPLICIT_WHITELIST = ConcurrentHashMap.newKeySet();

    private static final Set<String> BUILTIN_WHITELIST = ConcurrentHashMap.newKeySet();

    private static final Set<String> ANNOTATION_WHITELIST = ConcurrentHashMap.newKeySet();

    private static final Set<String> AUTOTYPE_CLASS_CACHE = ConcurrentHashMap.newKeySet();

    private static final Set<String> BUILTIN_BLACKLIST = ConcurrentHashMap.newKeySet();

    private static final Set<String> EXPLICIT_BLACKLIST = ConcurrentHashMap.newKeySet();

    static {
        initBuiltinWhitelist();
        initBuiltinBlacklist();
    }

    private AutoTypeChecker() {
        throw new UnsupportedOperationException();
    }

    private static void initBuiltinWhitelist() {
        BUILTIN_WHITELIST.add("java.lang.String");
        BUILTIN_WHITELIST.add("java.lang.Boolean");
        BUILTIN_WHITELIST.add("java.lang.Byte");
        BUILTIN_WHITELIST.add("java.lang.Short");
        BUILTIN_WHITELIST.add("java.lang.Integer");
        BUILTIN_WHITELIST.add("java.lang.Long");
        BUILTIN_WHITELIST.add("java.lang.Float");
        BUILTIN_WHITELIST.add("java.lang.Double");
        BUILTIN_WHITELIST.add("java.lang.Character");
        BUILTIN_WHITELIST.add("java.lang.Number");
        BUILTIN_WHITELIST.add("java.math.BigDecimal");
        BUILTIN_WHITELIST.add("java.math.BigInteger");
        BUILTIN_WHITELIST.add("java.util.Date");
        BUILTIN_WHITELIST.add("java.sql.Date");
        BUILTIN_WHITELIST.add("java.sql.Time");
        BUILTIN_WHITELIST.add("java.sql.Timestamp");
        BUILTIN_WHITELIST.add("java.util.Locale");
        BUILTIN_WHITELIST.add("java.util.TimeZone");
        BUILTIN_WHITELIST.add("java.util.Currency");
        BUILTIN_WHITELIST.add("java.util.UUID");
        BUILTIN_WHITELIST.add("java.util.HashMap");
        BUILTIN_WHITELIST.add("java.util.LinkedHashMap");
        BUILTIN_WHITELIST.add("java.util.TreeMap");
        BUILTIN_WHITELIST.add("java.util.Map");
        BUILTIN_WHITELIST.add("java.util.ArrayList");
        BUILTIN_WHITELIST.add("java.util.LinkedList");
        BUILTIN_WHITELIST.add("java.util.List");
        BUILTIN_WHITELIST.add("java.util.HashSet");
        BUILTIN_WHITELIST.add("java.util.LinkedHashSet");
        BUILTIN_WHITELIST.add("java.util.TreeSet");
        BUILTIN_WHITELIST.add("java.util.Properties");
        BUILTIN_WHITELIST.add("java.util.BitSet");
        BUILTIN_WHITELIST.add("java.util.Optional");
        BUILTIN_WHITELIST.add("java.time.LocalDateTime");
        BUILTIN_WHITELIST.add("java.time.LocalDate");
        BUILTIN_WHITELIST.add("java.time.LocalTime");
        BUILTIN_WHITELIST.add("java.time.Instant");
        BUILTIN_WHITELIST.add("java.time.ZonedDateTime");
        BUILTIN_WHITELIST.add(Duration.class.getName());
        BUILTIN_WHITELIST.add("java.time.Period");
        BUILTIN_WHITELIST.add("java.time.ZoneId");
        BUILTIN_WHITELIST.add("java.time.OffsetDateTime");
        BUILTIN_WHITELIST.add("java.time.OffsetTime");
        BUILTIN_WHITELIST.add("java.util.concurrent.ConcurrentHashMap");
        BUILTIN_WHITELIST.add("java.util.concurrent.CopyOnWriteArrayList");
        BUILTIN_WHITELIST.add("java.util.concurrent.atomic.AtomicInteger");
        BUILTIN_WHITELIST.add("java.util.concurrent.atomic.AtomicLong");
        BUILTIN_WHITELIST.add("java.util.concurrent.atomic.AtomicBoolean");
    }

    private static void initBuiltinBlacklist() {
        BUILTIN_BLACKLIST.add("com.sun.rowset.JdbcRowSetImpl");
        BUILTIN_BLACKLIST.add("java.rmi.server.UnicastRemoteObject");
        BUILTIN_BLACKLIST.add("java.rmi.server.UnicastRef");
        BUILTIN_BLACKLIST.add("java.rmi.registry.Registry");
        BUILTIN_BLACKLIST.add("java.rmi.Naming");
        BUILTIN_BLACKLIST.add("java.rmi.activation.Activator");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections.functors.InvokerTransformer");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections.functors.InstantiateTransformer");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections.functors.ConstantTransformer");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections.functors.ChainedTransformer");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections4.functors.InvokerTransformer");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections4.functors.InstantiateTransformer");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections4.functors.ConstantTransformer");
        BUILTIN_BLACKLIST.add("org.apache.commons.collections4.functors.ChainedTransformer");
        BUILTIN_BLACKLIST.add("org.apache.xalan.xsltc.trax.TemplatesImpl");
        BUILTIN_BLACKLIST.add("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl");
        BUILTIN_BLACKLIST.add("java.lang.ProcessBuilder");
        BUILTIN_BLACKLIST.add("java.lang.Runtime");
        BUILTIN_BLACKLIST.add("javax.naming.InitialContext");
        BUILTIN_BLACKLIST.add("javax.naming.spi.NamingManager");
        BUILTIN_BLACKLIST.add("javax.script.ScriptEngineManager");
        BUILTIN_BLACKLIST.add("org.springframework.beans.factory.ObjectFactory");
        BUILTIN_BLACKLIST.add("org.springframework.context.support.ClassPathXmlApplicationContext");
        BUILTIN_BLACKLIST.add("com.sun.org.apache.xerces.internal.impl.XMLEntityManager");
        BUILTIN_BLACKLIST.add("java.util.ServiceLoader");
        BUILTIN_BLACKLIST.add("java.net.URL");
        BUILTIN_BLACKLIST.add("java.net.Inet4Address");
        BUILTIN_BLACKLIST.add("java.net.Inet6Address");
        BUILTIN_BLACKLIST.add("java.net.InetSocketAddress");
        BUILTIN_BLACKLIST.add("java.net.Socket");
        BUILTIN_BLACKLIST.add("java.net.ServerSocket");
        BUILTIN_BLACKLIST.add("java.io.File");
        BUILTIN_BLACKLIST.add("java.io.FileInputStream");
        BUILTIN_BLACKLIST.add("java.io.FileOutputStream");
        BUILTIN_BLACKLIST.add("java.io.RandomAccessFile");
        BUILTIN_BLACKLIST.add("java.lang.Thread");
        BUILTIN_BLACKLIST.add("java.lang.ClassLoader");
        BUILTIN_BLACKLIST.add("java.lang.System");
    }

    /**
     * 检查类型是否允许反序列化
     *
     * @param clazz 待检查的类
     * @throws JsonDeserializationException 如果类型不被允许
     */
    public static void checkType(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        if (!isTypeAllowed(clazz)) {
            throw new JsonDeserializationException(
                JsonDeserializationException.PARSE_ERROR,
                "AutoType check failed: " + clazz.getName()
                    + " is not in the whitelist. "
                    + "Please add @JsonClass annotation or use AutoTypeChecker.addToWhitelist()"
            );
        }
    }

    /**
     * 检查类型是否允许反序列化
     *
     * @param className 待检查的类全限定名
     * @throws JsonDeserializationException 如果类型不被允许
     */
    public static void checkType(String className) {
        if (className == null || className.isEmpty()) {
            return;
        }
        if (!isTypeAllowed(className)) {
            throw new JsonDeserializationException(
                JsonDeserializationException.PARSE_ERROR,
                "AutoType check failed: " + className
                    + " is not in the whitelist. "
                    + "Please add @JsonClass annotation or use AutoTypeChecker.addToWhitelist()"
            );
        }
    }

    /**
     * 判断类型是否允许反序列化
     *
     * @param clazz 待检查的类
     * @return 是否允许
     */
    public static boolean isTypeAllowed(Class<?> clazz) {
        if (clazz == null) {
            return true;
        }
        return isTypeAllowed(clazz.getName());
    }

    /**
     * 判断类型是否允许反序列化
     *
     * @param className 待检查的类全限定名
     * @return 是否允许
     */
    public static boolean isTypeAllowed(String className) {
        if (className == null || className.isEmpty()) {
            return true;
        }
        if (isBlacklisted(className)) {
            return false;
        }
        if (!safeMode) {
            return true;
        }
        if (isPrimitiveOrWrapper(className)) {
            return true;
        }
        if (className.startsWith("[")) {
            return true;
        }
        if (BUILTIN_WHITELIST.contains(className)) {
            return true;
        }
        if (EXPLICIT_WHITELIST.contains(className)) {
            return true;
        }
        if (ANNOTATION_WHITELIST.contains(className)) {
            return true;
        }
        if (AUTOTYPE_CLASS_CACHE.contains(className)) {
            return true;
        }
        if (isAutoTypeClass(className)) {
            AUTOTYPE_CLASS_CACHE.add(className);
            return true;
        }
        return false;
    }

    private static boolean isBlacklisted(String className) {
        if (BUILTIN_BLACKLIST.contains(className)) {
            return true;
        }
        if (EXPLICIT_BLACKLIST.contains(className)) {
            return true;
        }
        for (String prefix : BUILTIN_BLACKLIST) {
            if (className.startsWith(prefix + "$")) {
                return true;
            }
        }
        for (String prefix : EXPLICIT_BLACKLIST) {
            if (className.startsWith(prefix + "$")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrimitiveOrWrapper(String className) {
        return className.equals("boolean") || className.equals("byte")
            || className.equals("short") || className.equals("int")
            || className.equals("long") || className.equals("float")
            || className.equals("double") || className.equals("char")
            || className.equals("void") || className.equals("java.lang.Object");
    }

    private static boolean isAutoTypeClass(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            JsonClass annotation = clazz.getAnnotation(JsonClass.class);
            if (annotation != null) {
                ANNOTATION_WHITELIST.add(className);
                Class<?>[] seeAlso = annotation.seeAlso();
                if (seeAlso != null) {
                    for (Class<?> sub : seeAlso) {
                        AUTOTYPE_CLASS_CACHE.add(sub.getName());
                    }
                }
                return true;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        }
        return false;
    }

    /**
     * 显式将类型加入白名单
     *
     * @param className 类全限定名
     */
    public static void addToWhitelist(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be null or empty");
        }
        EXPLICIT_WHITELIST.add(className);
    }

    /**
     * 从白名单中移除类型
     *
     * @param className 类全限定名
     */
    public static void removeFromWhitelist(String className) {
        EXPLICIT_WHITELIST.remove(className);
    }

    /**
     * 将类型加入黑名单（即使 SafeMode=false 也会拒绝）
     *
     * @param className 类全限定名
     */
    public static void addToBlacklist(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className must not be null or empty");
        }
        EXPLICIT_BLACKLIST.add(className);
    }

    /**
     * 从黑名单中移除类型
     *
     * @param className 类全限定名
     */
    public static void removeFromBlacklist(String className) {
        EXPLICIT_BLACKLIST.remove(className);
    }

    /**
     * 批量加入黑名单
     *
     * @param classNames 类全限定名数组
     */
    public static void addToBlacklist(String... classNames) {
        if (classNames != null) {
            for (String name : classNames) {
                addToBlacklist(name);
            }
        }
    }

    /**
     * 批量加入白名单
     *
     * @param classNames 类全限定名数组
     */
    public static void addToWhitelist(String... classNames) {
        if (classNames != null) {
            for (String name : classNames) {
                addToWhitelist(name);
            }
        }
    }

    /**
     * 设置安全模式
     *
     * @param enabled true=启用安全模式（推荐），false=关闭安全模式
     */
    public static void setSafeMode(boolean enabled) {
        safeMode = enabled;
    }

    /**
     * 获取当前安全模式状态
     *
     * @return 是否启用安全模式
     */
    public static boolean isSafeMode() {
        return safeMode;
    }

    /**
     * 获取显式白名单
     */
    public static Set<String> getExplicitWhitelist() {
        return EXPLICIT_WHITELIST;
    }

    /**
     * 获取内置白名单
     */
    public static Set<String> getBuiltinWhitelist() {
        return Set.copyOf(BUILTIN_WHITELIST);
    }

    /**
     * 获取注解白名单
     */
    public static Set<String> getAnnotationWhitelist() {
        return ANNOTATION_WHITELIST;
    }

    /**
     * 获取内置黑名单（只读副本）
     */
    public static Set<String> getBuiltinBlacklist() {
        return Set.copyOf(BUILTIN_BLACKLIST);
    }

    /**
     * 获取显式黑名单
     */
    public static Set<String> getExplicitBlacklist() {
        return EXPLICIT_BLACKLIST;
    }

    /**
     * 清除所有缓存（用于测试）
     */
    public static void clearCache() {
        ANNOTATION_WHITELIST.clear();
        AUTOTYPE_CLASS_CACHE.clear();
    }

    /**
     * 重置到初始状态（用于测试）
     */
    public static void reset() {
        EXPLICIT_WHITELIST.clear();
        ANNOTATION_WHITELIST.clear();
        AUTOTYPE_CLASS_CACHE.clear();
        EXPLICIT_BLACKLIST.clear();
        safeMode = true;
    }
}
