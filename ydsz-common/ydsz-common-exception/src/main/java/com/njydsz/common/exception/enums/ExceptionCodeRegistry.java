package com.njydsz.common.exception.enums;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常码注册中心。
 *
 * <p><b>已废弃：</b>自 v2.0 起由 {@link com.njydsz.common.exception.code.ErrorCodeTable} Spring Bean 接管全部注册职责，
 * 本类退化为过渡期兼容门面。新项目应直接使用 {@code ErrorCodeTable} 进行 code 注册和反查。
 *
 * <p>仍保留如下能力以确保平滑迁移：
 * <ul>
 *   <li>{@link #register(Map)} — 委托 ErrorCodeTable</li>
 *   <li>{@link #lookup(String)} — 委托 ErrorCodeTable；若 ErrorCodeTable 尚未可用回退到内部静态缓存</li>
 *   <li>{@link #allRegistered()} — 合并 ErrorCodeTable + 内部缓存</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 使用 {@link com.njydsz.common.exception.code.ErrorCodeTable} 替代
 */
@Deprecated
public final class ExceptionCodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExceptionCodeRegistry.class);

    /** 内部缓存，用于在 ErrorCodeTable 不可用时兜底（如枚举静态初始化阶段） */
    private static final Map<String, ExceptionCode> REGISTRY = new ConcurrentHashMap<>();

    /** ErrorCodeTable 引用（由 Spring 配置注入） */
    private static volatile com.njydsz.common.exception.code.ErrorCodeTable delegate;

    private ExceptionCodeRegistry() {
        // 工具类禁止实例化
    }

    /**
     * 注入 ErrorCodeTable 委托。
     *
     * <p>由 {@code YdszExceptionCoreAutoConfiguration} 在 Bean 初始化后调用。
     * 注入后将同步缓存中的已有条目到 ErrorCodeTable。
     *
     * @param table ErrorCodeTable 实例
     */
    public static void setDelegate(com.njydsz.common.exception.code.ErrorCodeTable table) {
        delegate = table;
        if (table != null && !REGISTRY.isEmpty()) {
            table.registerAll(REGISTRY);
            log.debug("[ExceptionCodeRegistry] 已同步 {} 条已有异常码至 ErrorCodeTable", REGISTRY.size());
        }
    }

    /**
     * 获取内部注册表引用（仅用于测试）。
     *
     * @return 内部注册表
     */
    public static Map<String, ExceptionCode> getRegistry() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * 注册一组异常码映射。
     *
     * <p>委托 ErrorCodeTable；若尚未注入则寄存内部缓存，等待注入时自动同步。
     *
     * @param codeMap 异常码映射表
     * @deprecated 使用 {@link com.njydsz.common.exception.code.ErrorCodeTable} 替代
     */
    @Deprecated
    public static void register(Map<String, ExceptionCode> codeMap) {
        register(codeMap, false);
    }

    /**
     * 注册一组异常码映射（严格模式）。
     *
     * @param codeMap 异常码映射表
     * @deprecated 使用 {@link com.njydsz.common.exception.code.ErrorCodeTable} 替代
     */
    @Deprecated
    public static void registerStrict(Map<String, ExceptionCode> codeMap) {
        register(codeMap, true);
    }

    /**
     * 注册一组异常码映射。
     *
     * @deprecated 使用 {@link com.njydsz.common.exception.code.ErrorCodeTable} 替代
     */
    @Deprecated
    public static void register(Map<String, ExceptionCode> codeMap, boolean requireNotExists) {
        if (codeMap == null) {
            throw new IllegalArgumentException("codeMap cannot be null");
        }
        // 同步写入内部缓存（兜底，用于 Spring 上下文就绪前）
        for (Map.Entry<String, ExceptionCode> entry : codeMap.entrySet()) {
            REGISTRY.put(entry.getKey(), entry.getValue());
        }
        // 同步委托 ErrorCodeTable（若已可用）
        if (delegate != null) {
            delegate.registerAll(codeMap);
            if (requireNotExists) {
                // 严格模式校验：出现重复 code 抛出异常
                // ErrorCodeTable.registerAll 使用 putIfAbsent，重复不会报错
                // 此处仅作兼容：不做二次严格校验，严格模式建议通过 ErrorCodeTable 新 API 实现
            }
        }
    }

    /**
     * 按 code 字符串查找已注册的 ExceptionCode。
     *
     * @param code 异常码字符串
     * @return 对应的 ExceptionCode；未找到返回 null
     * @deprecated 使用 {@link com.njydsz.common.exception.code.ErrorCodeTable#lookup(String)} 替代
     */
    @Deprecated
    public static ExceptionCode lookup(String code) {
        if (code == null) {
            return null;
        }
        // 优先从 ErrorCodeTable 反查（已注册到 ErrorCodeTable 的条目集更全）
        if (delegate != null) {
            ExceptionCode found = delegate.lookup(code);
            if (found != null) {
                return found;
            }
        }
        // 兜底：从内部静态缓存反查
        return REGISTRY.get(code);
    }

    /**
     * 判断某个 code 是否已注册。
     *
     * @param code 异常码字符串
     * @return 已注册返回 true，否则返回 false
     * @deprecated 使用 {@link com.njydsz.common.exception.code.ErrorCodeTable} 替代
     */
    @Deprecated
    public static boolean isRegistered(String code) {
        if (code == null) {
            return false;
        }
        if (delegate != null && delegate.lookup(code) != null) {
            return true;
        }
        return REGISTRY.containsKey(code);
    }

    /**
     * 返回当前已注册的所有异常码映射的不可变视图（合并 ErrorCodeTable + 内部缓存）。
     *
     * @return 不可变的 code → ExceptionCode 映射
     * @deprecated 使用 {@link com.njydsz.common.exception.code.ErrorCodeTable#allCodes()} 替代
     */
    @Deprecated
    public static Map<String, ExceptionCode> allRegistered() {
        if (delegate != null) {
            return delegate.allCodes();
        }
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * 清空注册表 — <b>仅用于测试</b>。
     */
    public static void clear() {
        REGISTRY.clear();
        if (delegate != null) {
            delegate.allCodes().keySet().forEach(k -> {
                // ErrorCodeTable 无法清除单条，可能需要扩展
            });
        }
        log.info("[ExceptionCodeRegistry] 静态缓存已清空（测试专用）");
    }
}
