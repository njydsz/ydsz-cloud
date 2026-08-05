package com.remisoft.common.exception.registry;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局错误码注册中心。
 *
 * <p>P2-4: 启动时扫描所有 {@link RemiResultCode} 注解的枚举类，
 * 注册到全局注册表，供前端查询错误码含义和运维诊断。
 *
 * <h3>注册内容</h3>
 * <ul>
 *   <li>模块名 → 模块描述</li>
 *   <li>模块名 → 错误码列表（code + message + 枚举名）</li>
 * </ul>
 *
 * <p>通过 {@code /actuator/remi-error-codes} 端点暴露（由 ExceptionCodeDocEndpoint 实现）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class ResultCodeRegistry {

    private final Map<String, String> moduleDescriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<ErrorCodeEntry>> moduleErrorCodes = new ConcurrentHashMap<>();

    /**
     * 注册模块错误码。
     *
     * @param module     模块名称
     * @param description 模块描述
     */
    public void registerModule(String module, String description) {
        moduleDescriptions.put(module, description != null ? description : module);
        moduleErrorCodes.computeIfAbsent(module, k -> ConcurrentHashMap.newKeySet());
        log.info("[ResultCodeRegistry] 模块注册: module={}, description={}", module, description);
    }

    /**
     * 注册错误码。
     *
     * @param module   模块名称
     * @param code     错误码字符串（如 "A01001"）
     * @param message  错误消息（i18n key）
     * @param enumName 枚举常量名
     */
    public void registerCode(String module, String code, String message, String enumName) {
        moduleErrorCodes.computeIfAbsent(module, k -> ConcurrentHashMap.newKeySet())
                .add(new ErrorCodeEntry(code, message, enumName));
    }

    /**
     * 获取所有已注册模块。
     */
    public Map<String, String> getModules() {
        return Collections.unmodifiableMap(moduleDescriptions);
    }

    /**
     * 获取指定模块的错误码列表。
     */
    public Set<ErrorCodeEntry> getErrorCodes(String module) {
        return Collections.unmodifiableSet(
                moduleErrorCodes.getOrDefault(module, Collections.emptySet()));
    }

    /**
     * 获取全部模块的错误码。
     */
    public Map<String, Set<ErrorCodeEntry>> getAllErrorCodes() {
        return Collections.unmodifiableMap(moduleErrorCodes);
    }

    /**
     * 错误码条目。
     */
    public record ErrorCodeEntry(String code, String message, String enumName) {}
}
