package com.njydsz.common.exception.enums;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常码注册中心
 *
 * <p>线程安全的 ExceptionCode 注册和查找工具。各模块的异常码枚举
 * 在静态初始化时通过 {@link #register(Map)} 将 code → ExceptionCode 映射
 * 注册到本中心，之后可通过 {@link #lookup(String)} 按 code 字符串反查枚举实例。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>内部使用 {@link ConcurrentHashMap} 保证并发安全</li>
 *   <li>register() 支持增量注册，同一 code 重复注册将被忽略（首次注册生效）</li>
 *   <li>lookup() 未找到时返回 null，调用方可按需抛出异常</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 枚举类静态块中注册
 * static {
 *     Map<String, ExceptionCode> map = new HashMap<>();
 *     for (MyExceptionCode c : values()) {
 *         map.put(c.getCode(), c);
 *     }
 *     ExceptionCodeRegistry.register(map);
 * }
 *
 * // 全局查找
 * ExceptionCode ec = ExceptionCodeRegistry.lookup("A01001");
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public final class ExceptionCodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExceptionCodeRegistry.class);

    /** 存储 code → ExceptionCode 映射的全局注册表 */
    private static final Map<String, ExceptionCode> REGISTRY = new ConcurrentHashMap<>();

    private ExceptionCodeRegistry() {
        // 工具类禁止实例化
    }

    /**
     * 注册一组异常码映射
     *
     * <p>将传入的 map 中所有条目合并到全局注册表。如果某个 code 已被注册，
     * 则保留首次注册的映射，不会覆盖。
     *
     * @param codeMap 异常码映射表，key 为 code 字符串，value 为 ExceptionCode 枚举实例
     * @throws IllegalArgumentException 如果传入的 map 为 null
     */
    public static void register(Map<String, ExceptionCode> codeMap) {
        if (codeMap == null) {
            throw new IllegalArgumentException("codeMap cannot be null");
        }
        // 使用 putIfAbsent 循环实现增量注册，首次注册生效
        for (Map.Entry<String, ExceptionCode> entry : codeMap.entrySet()) {
            ExceptionCode existing = REGISTRY.putIfAbsent(entry.getKey(), entry.getValue());
            if (existing != null && existing != entry.getValue()) {
                log.warn("异常码重复注册被忽略 | code={} | 已注册: {} | 新注册: {}",
                        entry.getKey(), existing.getClass().getName(), entry.getValue().getClass().getName());
            }
        }
    }

    /**
     * 按 code 字符串查找已注册的 ExceptionCode
     *
     * @param code 异常码字符串，如 "A01001"
     * @return 对应的 ExceptionCode 枚举实例；未找到时返回 null
     */
    public static ExceptionCode lookup(String code) {
        if (code == null) {
            return null;
        }
        return REGISTRY.get(code);
    }

    /**
     * 判断某个 code 是否已注册
     *
     * @param code 异常码字符串
     * @return 已注册返回 true，否则返回 false
     */
    public static boolean isRegistered(String code) {
        if (code == null) {
            return false;
        }
        return REGISTRY.containsKey(code);
    }

    /**
     * 返回当前已注册的所有异常码映射的不可变视图
     *
     * @return 不可变的 code → ExceptionCode 映射
     */
    public static Map<String, ExceptionCode> allRegistered() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
