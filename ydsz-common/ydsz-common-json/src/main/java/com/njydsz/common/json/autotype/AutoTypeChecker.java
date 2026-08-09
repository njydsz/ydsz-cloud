package com.njydsz.common.json.autotype;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 反序列化类型安全白名单引擎。
 *
 * <p>对标 FastJSON 的 safeMode 与 Jackson PolymorphicTypeValidator，
 * 通过显式注册白名单类型防止恶意 {@code @type} 字段任意类加载攻击。
 *
 * <p><b>安全策略：</b></p>
 * <ul>
 *   <li>仅明确注册的类型能通过校验</li>
 *   <li>层次化匹配：精确类名 → 父类追溯 → 接口匹配（深度限制 32 层防止恶意继承链 DoS）</li>
 *   <li>以类名为 key 而非 Class 引用（避免影响类卸载，兼容容器热部署）</li>
 * </ul>
 *
 * <p><b>使用方式：</b></p>
 * <pre>{@code
 * // 启动时注册安全类型
 * AutoTypeChecker.addToWhitelist(UserDTO.class);
 * AutoTypeChecker.addToWhitelist(OrderDTO.class);
 *
 * // 反序列化前校验
 * if (AutoTypeChecker.isAllowed(targetClass)) {
 *     YdszJson.fromJson(json, targetClass);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see com.njydsz.common.json.JsonSecurityUtils
 */
public final class AutoTypeChecker {

    /** 类名白名单（ConcurrentHashMap 保证并发读写安全） */
    private static final ConcurrentMap<String, Boolean> WHITELIST = new ConcurrentHashMap<>();

    /**
     * 注册类型时的最大继承链追溯深度。
     *
     * <p>防止恶意构造的超长继承链导致父类追溯耗时指数增长（DoS 攻击）。
     */
    private static final int MAX_HIERARCHY_DEPTH = 32;

    private AutoTypeChecker() {
    }

    /**
     * 将指定类型及其所有父类/接口注册到白名单。
     *
     * <p>注册后该类及其所有子类（需单独注册）可通过 {@link #isAllowed(Class)} 校验。
     *
     * @param type 要注册的类型，不可为 null
     * @throws IllegalArgumentException 如果 type 为 null
     */
    public static void addToWhitelist(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Cannot register null type to whitelist");
        }
        WHITELIST.put(type.getName(), Boolean.TRUE);
    }

    /**
     * 从白名单移除指定类型。
     *
     * @param type 要移除的类型，不可为 null
     */
    public static void removeFromWhitelist(Class<?> type) {
        if (type == null) {
            return;
        }
        WHITELIST.remove(type.getName());
    }

    /**
     * 检查指定类型是否允许反序列化（白名单匹配）。
     *
     * <p>匹配规则（按优先级）：
     * <ol>
     *   <li>精确类名匹配</li>
     *   <li>父类追溯匹配（最多 {@value #MAX_HIERARCHY_DEPTH} 层）</li>
     *   <li>接口匹配（实现任一已注册接口即通过）</li>
     * </ol>
     *
     * @param type 要检查的类型
     * @return true 表示允许反序列化，false 表示不在白名单中
     */
    public static boolean isAllowed(Class<?> type) {
        if (type == null) {
            return false;
        }
        // 1. 精确匹配
        if (WHITELIST.containsKey(type.getName())) {
            return true;
        }
        // 2. 父类追溯 + 接口匹配
        Class<?> current = type;
        int depth = 0;
        while (current != null && depth < MAX_HIERARCHY_DEPTH) {
            current = current.getSuperclass();
            depth++;
            if (current == null || current == Object.class) {
                break;
            }
            if (WHITELIST.containsKey(current.getName())) {
                return true;
            }
            if (checkInterfaces(current)) {
                return true;
            }
        }
        // 3. 直接实现的接口
        return checkInterfaces(type);
    }

    /**
     * 返回当前白名单的不可修改快照。
     *
     * @return 已注册类型全限定名集合
     */
    public static Set<String> getWhitelist() {
        return Collections.unmodifiableSet(WHITELIST.keySet());
    }

    /**
     * 返回白名单大小。
     *
     * @return 已注册类型数量
     */
    public static int size() {
        return WHITELIST.size();
    }

    /**
     * 清空白名单（主要用于单元测试）。
     */
    public static void clear() {
        WHITELIST.clear();
    }

    /**
     * 检查类型实现的接口是否有已注册的。
     */
    private static boolean checkInterfaces(Class<?> type) {
        for (Class<?> iface : type.getInterfaces()) {
            if (WHITELIST.containsKey(iface.getName())) {
                return true;
            }
        }
        return false;
    }
}
