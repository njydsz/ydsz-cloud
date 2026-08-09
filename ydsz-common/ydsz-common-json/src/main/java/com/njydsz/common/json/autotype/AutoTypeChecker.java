package com.njydsz.common.json.autotype;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON 反序列化 AutoType 安全白名单检查器。
 *
 * <p>对标 FastJSON2 的 {@code AutoTypeCheck} 和 Jackson 的
 * {@code PolymorphicTypeValidator}，用于防止 JSON 反序列化中的
 * 多态类型滥用（Polymorphic Type Abuse）安全漏洞。
 *
 * <p><b>背景：</b></p>
 * <p>JSON 反序列化时若支持多态类型（通过类型鉴别属性如 {@code "@type"} 或 {@code "@class"}），
 * 攻击者可构造恶意 JSON 指定任意危险类进行实例化，导致远程代码执行（RCE）。
 * 本检查器通过维护显式白名单，仅允许已知安全类型参与多态反序列化。</p>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *   <li>缓存导出/导入时校验反序列化目标类型</li>
 *   <li>消息队列消费者处理多态消息体时校验具体子类型</li>
 *   <li>HTTP 反序列化带 @JsonTypeInfo 的抽象类/接口时校验</li>
 * </ul>
 *
 * <p><b>线程安全：</b>本类的所有方法均为线程安全。内部使用 {@link ConcurrentHashMap} 存储白名单，
 * 读取不加锁，写入使用 {@code putIfAbsent} / {@code remove} 原子操作。</p>
 *
 * <p><b>层次化匹配：</b></p>
 * <p>{@link #isAllowed(Class)} 在检查时不仅匹配精确类型，还会递归检查父类和接口。
 * 例如将 {@code ArrayList} 加入白名单后，{@code LinkedList} 不会被自动允许
 * （子类型不在白名单），但 {@code List} 加入白名单后，{@code ArrayList} 会被允许
 * （父类型在白名单）。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 添加白名单
 * AutoTypeChecker.addToWhitelist(UserDTO.class);
 * AutoTypeChecker.addToWhitelist(OrderDTO.class);
 *
 * // 检查
 * if (AutoTypeChecker.isAllowed(targetType)) {
 *     return YdszJson.fromJson(json, targetType);
 * } else {
 *     throw new SecurityException("Type not in whitelist: " + targetType.getName());
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see com.njydsz.common.json.JsonSecurityUtils
 */
public final class AutoTypeChecker {

    private static final Logger log = LoggerFactory.getLogger(AutoTypeChecker.class);

    /**
     * 白名单存储（键为 Class 的全限定名，避免持有 Class 引用导致类卸载困难）。
     *
     * <p>使用 ConcurrentHashMap 的 keySet 作为并发 Set 使用（值恒为 Boolean.TRUE）。
     */
    private static final ConcurrentHashMap<String, Boolean> WHITELIST = new ConcurrentHashMap<>();

    /** 层次化匹配最大递归深度，防止恶意构造的深度继承链导致检查耗时过长 */
    private static final int MAX_HIERARCHY_DEPTH = 32;

    private AutoTypeChecker() {
        throw new UnsupportedOperationException("AutoTypeChecker is a utility class and cannot be instantiated");
    }

    // ==================== 核心白名单操作 ====================

    /**
     * 将类型添加到白名单。
     *
     * <p>添加后，{@link #isAllowed(Class)} 对此类型及其子类（当父类型已注册时）返回 {@code true}。
     * 重复添加同一类型不会产生副作用。
     *
     * @param type 要添加到白名单的类型，不能为 null
     * @throws IllegalArgumentException 如果 type 为 null
     * @since 1.2.0
     */
    public static void addToWhitelist(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        String name = type.getName();
        Boolean previous = WHITELIST.putIfAbsent(name, Boolean.TRUE);
        if (previous == null) {
            log.debug("Added type to AutoType whitelist: {}", name);
        }
    }

    /**
     * 从白名单中移除类型。
     *
     * @param type 要从白名单移除的类型，不能为 null
     * @return 如果类型原本在白名单中并成功移除返回 {@code true}，否则返回 {@code false}
     * @throws IllegalArgumentException 如果 type 为 null
     * @since 1.2.0
     */
    public static boolean removeFromWhitelist(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        Boolean removed = WHITELIST.remove(type.getName());
        boolean wasPresent = removed != null;
        if (wasPresent) {
            log.debug("Removed type from AutoType whitelist: {}", type.getName());
        }
        return wasPresent;
    }

    /**
     * 检查类型是否在白名单中允许反序列化。
     *
     * <p>层次化匹配算法：
     * <ol>
     *   <li>精确类型匹配（类本身在白名单中）</li>
     *   <li>父类向上追溯（检查所有父类是否在白名单中）</li>
     *   <li>接口匹配（检查所有实现的接口是否在白名单中）</li>
     * </ol>
     *
     * <p>空类型返回 {@code false}。
     *
     * @param type 要检查的类型
     * @return 如果类型在白名单中（精确匹配或层次化匹配）返回 {@code true}
     * @since 1.2.0
     */
    public static boolean isAllowed(Class<?> type) {
        if (type == null) {
            return false;
        }
        // 快速路径：精确匹配
        if (WHITELIST.containsKey(type.getName())) {
            return true;
        }
        // 层次化匹配：检查父类和接口
        return isHierarchyAllowed(type);
    }

    /**
     * 递归检查类型层次结构中是否有白名单匹配。
     *
     * @param type 待检查类型
     * @return 任意层级匹配返回 true
     */
    private static boolean isHierarchyAllowed(Class<?> type) {
        Class<?> current = type;
        for (int depth = 0; depth < MAX_HIERARCHY_DEPTH && current != null; depth++) {
            // 检查当前类的所有直接接口
            for (Class<?> iface : current.getInterfaces()) {
                if (WHITELIST.containsKey(iface.getName())) {
                    return true;
                }
            }
            current = current.getSuperclass();
            if (current != null && WHITELIST.containsKey(current.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前白名单类型名称集合（只读快照）。
     *
     * <p>返回的集合是白名单的独立副本，修改返回值不会影响内部状态。
     * 仅用于监控、日志记录等场景。
     *
     * @return 白名单类型全限定名集合，不会为 null
     * @since 1.2.0
     */
    public static Set<String> getWhitelist() {
        return Collections.unmodifiableSet(WHITELIST.keySet());
    }

    /**
     * 清空白名单（谨慎使用）。
     *
     * <p>主要用于测试环境重置状态。生产环境调用可能导致大量合法反序列化被拒绝。
     *
     * @since 1.2.0
     */
    public static void clear() {
        WHITELIST.clear();
        log.warn("AutoType whitelist cleared");
    }

    /**
     * 获取当前白名单大小。
     *
     * @return 白名单中注册的类型数量
     * @since 1.2.0
     */
    public static int size() {
        return WHITELIST.size();
    }
}
