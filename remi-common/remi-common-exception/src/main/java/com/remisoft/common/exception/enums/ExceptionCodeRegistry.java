package com.remisoft.common.exception.enums;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 异常码注册中心
 *
 * <p>线程安全的 ExceptionCode 注册和查找工具。各模块的异常码枚举
 * 在静态初始化时通过 {@link #register(Map)} 将 code → ExceptionCode 映射
 * 注册到本中心，之后可通过 {@link #lookup(String)} 按 code 字符串反查枚举实例。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>作为 Spring {@link Component} 暴露，方便单元测试时通过 {@code @MockBean} 替换</li>
 *   <li>内部使用 {@link ConcurrentHashMap} 保证并发安全</li>
 *   <li>register() 支持增量注册，同一 code 重复注册将被忽略（首次注册生效）</li>
 *   <li>registerStrict() / register(map, true) 在重复注册时 fail-fast 抛出异常</li>
 *   <li>lookup() 未找到时返回 null，调用方可按需抛出异常</li>
 *   <li>{@link #clear()} 仅用于测试环境重置状态，生产环境禁止使用</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 枚举类静态块中注册（宽松模式：重复忽略 + warn 日志）
 * static {
 *     Map<String, ExceptionCode> map = new HashMap<>();
 *     for (MyExceptionCode c : values()) {
 *         map.put(c.getCode(), c);
 *     }
 *     ExceptionCodeRegistry.register(map);
 * }
 *
 * // 严格模式：重复注册直接抛异常，避免不同模块误用相同 code
 * static {
 *     Map<String, ExceptionCode> map = new HashMap<>();
 *     for (MyExceptionCode c : values()) {
 *         map.put(c.getCode(), c);
 *     }
 *     ExceptionCodeRegistry.registerStrict(map);
 * }
 *
 * // 全局查找
 * ExceptionCode ec = ExceptionCodeRegistry.lookup("A01001");
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Component
public class ExceptionCodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExceptionCodeRegistry.class);

    /** 存储 code → ExceptionCode 映射的全局注册表 */
    private static final Map<String, ExceptionCode> REGISTRY = new ConcurrentHashMap<>();

    /**
     * 获取内部注册表引用（仅用于测试）
     *
     * @return 内部注册表
     * @since 1.0.0
     */
    public Map<String, ExceptionCode> getRegistry() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * 注册一组异常码映射（宽松模式）
     *
     * <p>将传入的 map 中所有条目合并到全局注册表。如果某个 code 已被注册，
     * 则保留首次注册的映射，不会覆盖，仅输出 warn 日志。
     *
     * <p>等价于 {@code register(codeMap, false)}。
     *
     * @param codeMap 异常码映射表，key 为 code 字符串，value 为 ExceptionCode 枚举实例
     * @throws IllegalArgumentException 如果传入的 map 为 null
     */
    public static void register(Map<String, ExceptionCode> codeMap) {
        register(codeMap, false);
    }

    /**
     * 注册一组异常码映射（严格模式）
     *
     * <p>等价于 {@code register(codeMap, true)}。当发现某个 code 已被注册时，
     * 立即抛出 {@link IllegalStateException}，避免不同模块误用相同 code
     * 而被静默忽略。
     *
     * @param codeMap 异常码映射表，key 为 code 字符串，value 为 ExceptionCode 枚举实例
     * @throws IllegalArgumentException 如果传入的 map 为 null
     * @throws IllegalStateException 如果 requireNotExists=true 且某个 code 已被注册
     * @since 1.0.0
     */
    public static void registerStrict(Map<String, ExceptionCode> codeMap) {
        register(codeMap, true);
    }

    /**
     * 注册一组异常码映射，可指定重复注册时的处理策略
     *
     * <p>将传入的 map 中所有条目合并到全局注册表。
     *
     * @param codeMap 异常码映射表，key 为 code 字符串，value 为 ExceptionCode 枚举实例
     * @param requireNotExists 是否要求所有 code 未被注册过
     *        <ul>
     *          <li>{@code false}：重复注册时保留首次值，仅输出 warn 日志（宽松模式，默认）</li>
     *          <li>{@code true}：重复注册时立即抛出 {@link IllegalStateException}（严格模式，fail-fast）</li>
     *        </ul>
     * @throws IllegalArgumentException 如果传入的 map 为 null
     * @throws IllegalStateException 如果 requireNotExists=true 且某个 code 已被注册
     * @since 1.0.0
     */
    public static void register(Map<String, ExceptionCode> codeMap, boolean requireNotExists) {
        if (codeMap == null) {
            throw new IllegalArgumentException("codeMap cannot be null");
        }
        for (Map.Entry<String, ExceptionCode> entry : codeMap.entrySet()) {
            String code = entry.getKey();
            ExceptionCode newValue = entry.getValue();
            ExceptionCode existing = REGISTRY.putIfAbsent(code, newValue);
            if (existing == null) {
                // 首次注册，无冲突
                continue;
            }
            // 已存在相同 code 的注册
            if (existing == newValue) {
                // 同一实例重复注册，幂等，不告警
                continue;
            }
            // 不同实例冲突
            if (requireNotExists) {
                throw new IllegalStateException(
                    "异常码重复注册冲突 | code=" + code
                    + " | 已注册: " + existing.getClass().getName()
                    + " | 新注册: " + newValue.getClass().getName()
                    + " | requireNotExists=true，请检查不同模块是否误用了相同的异常码"
                );
            }
            log.warn("异常码重复注册被忽略 | code={} | 已注册: {} | 新注册: {}",
                    code, existing.getClass().getName(), newValue.getClass().getName());
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

    /**
     * 清空注册表 — <b>仅用于测试</b>
     *
     * <p>生产环境禁止调用该方法，可能会导致注册状态与启动时不一致。
     * 建议配合 {@code @Before} / @After 在单元测试中重置。
     */
    public static void clear() {
        REGISTRY.clear();
        log.info("[ExceptionCodeRegistry] 注册表已清空（测试专用）");
    }
}
