package com.njydsz.pmis.common.exception.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异常码全局注册表
 *
 * <p>各模块的异常码枚举可通过在静态块中调用 {@link #register} 方法完成注册，
 * 之后可通过 {@link #lookup} 按 code 字符串查找对应的枚举实例。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class ExceptionCodeRegistry {

    private static final Map<String, ExceptionCode> REGISTRY = new ConcurrentHashMap<>();

    private ExceptionCodeRegistry() {
    }

    /**
     * 注册异常码
     *
     * @param code           异常码字符串
     * @param exceptionCode  异常码枚举实例
     */
    public static void register(String code, ExceptionCode exceptionCode) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Exception code cannot be null or blank");
        }
        REGISTRY.putIfAbsent(code, exceptionCode);
    }

    /**
     * 批量注册异常码
     *
     * @param codes 异常码枚举数组
     */
    public static void registerAll(ExceptionCode[] codes) {
        for (ExceptionCode code : codes) {
            register(code.getCode(), code);
        }
    }

    /**
     * 查找已注册的异常码
     *
     * @param code 异常码字符串
     * @return 对应的 ExceptionCode 枚举实例，未找到返回 null
     */
    public static ExceptionCode lookup(String code) {
        return REGISTRY.get(code);
    }

    /**
     * 清空注册表（仅用于测试）
     */
    static void clear() {
        REGISTRY.clear();
    }
}
