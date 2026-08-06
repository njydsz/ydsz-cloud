package com.remisoft.common.exception.registry;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局错误码注册中心。
 *
 * <p>启动时扫描所有 {@link RemiResultCode} 注解的枚举类，
 * 注册到全局注册表，供前端查询错误码含义和运维诊断。
 *
 * <h3>注册内容</h3>
 * <ul>
 *   <li>模块名 → 模块描述</li>
 *   <li>模块名 → 错误码 → 条目</li>
 * </ul>
 *
 * <p>通过 {@code /actuator/remi-error-codes} 端点暴露（由 ExceptionCodeDocEndpoint 实现）。
 *
 * <h3>改进</h3>
 * <p>内部使用 {@link ConcurrentHashMap} 替代原始 {@code Set}，code 作为 key，
 * 天然去重且支持按 code O(1) 查找，解决 Set 结构无法快速判断错误的重复。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class ResultCodeRegistry {

    private final Map<String, String> moduleDescriptions = new ConcurrentHashMap<>();

    /** 模块名 → (错误码 code → 条目) */
    private final Map<String, Map<String, ErrorCodeEntry>> moduleErrorCodes = new ConcurrentHashMap<>();

    /**
     * 注册模块错误码。
     *
     * @param module     模块名称
     * @param description 模块描述
     */
    public void registerModule(String module, String description) {
        moduleDescriptions.put(module, description != null ? description : module);
        moduleErrorCodes.computeIfAbsent(module, k -> new ConcurrentHashMap<>());
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
        Map<String, ErrorCodeEntry> codeMap = moduleErrorCodes
                .computeIfAbsent(module, k -> new ConcurrentHashMap<>());
        ErrorCodeEntry prev = codeMap.putIfAbsent(code, new ErrorCodeEntry(code, message, enumName));
        if (prev != null) {
            log.info("[ResultCodeRegistry] 错误码重复注册被忽略 | module={} code={} existing={} new={}",
                    module, code, prev.enumName(), enumName);
        }
    }

    /**
     * 获取所有已注册模块。
     */
    public Map<String, String> getModules() {
        return Collections.unmodifiableMap(moduleDescriptions);
    }

    /**
     * 获取指定模块的错误码列表。
     *
     * @param module 模块名
     * @return code → ErrorCodeEntry 的不可变映射；未注册模块返回空映射
     */
    public Map<String, ErrorCodeEntry> getErrorCodes(String module) {
        Map<String, ErrorCodeEntry> codes = moduleErrorCodes.get(module);
        if (codes == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(codes);
    }

    /**
     * 获取全部模块的错误码。
     *
     * @return 模块名 → (code → ErrorCodeEntry) 的双层不可变映射
     */
    public Map<String, Map<String, ErrorCodeEntry>> getAllErrorCodes() {
        return Collections.unmodifiableMap(moduleErrorCodes);
    }

    /**
     * 按 code 查找错误码条目（跨模块搜索）。
     *
     * <p>当多个模块使用了相同 code（通常是开发期注册冲突），返回第一个找到的条目。
     *
     * @param code 错误码字符串
     * @return 对应条目；未找到返回 null
     * @since 1.0.0
     */
    public ErrorCodeEntry lookupByCode(String code) {
        if (code == null) {
            return null;
        }
        for (Map<String, ErrorCodeEntry> codes : moduleErrorCodes.values()) {
            ErrorCodeEntry entry = codes.get(code);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 错误码条目。
     *
     * @param code      错误码
     * @param message   i18n 消息 key
     * @param enumName  枚举常量名
     */
    public record ErrorCodeEntry(String code, String message, String enumName) {}
}
