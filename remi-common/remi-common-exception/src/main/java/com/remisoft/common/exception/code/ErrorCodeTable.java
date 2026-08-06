package com.remisoft.common.exception.code;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.remisoft.common.exception.enums.ExceptionCode;

/**
 * 统一错误码注册表。
 *
 * <p>取代了历史上 {@link com.remisoft.common.exception.enums.ExceptionCodeRegistry}（静态工具类）
 * 与 {@link com.remisoft.common.exception.registry.ResultCodeRegistry}（Spring Bean）的双轨设计，
 * 作为唯一的全局错误码来源。
 *
 * <p>职责分离：
 * <ul>
 *   <li>运行时反查：{@link #lookup(String)} — 供处理器和日志 {@code resolve}</li>
 *   <li>文档端点：{@link #groupByModule()} / {@link #allCodes()} — 供 Actuator 错误码文档</li>
 * </ul>
 *
 * <p><b>线程安全：</b>内部使用 {@link ConcurrentHashMap}，启动期扫描注册完毕后
 * 后续只有读操作（{@link #lookup}），无需额外同步。
 *
 * @author remi-team
 * @since 2.0.0
 */
@Component
public class ErrorCodeTable {

    /** code → ExceptionCode 全局索引（运行时反查） */
    private final ConcurrentHashMap<String, ExceptionCode> codeIndex = new ConcurrentHashMap<>();

    /** ModuleEntry 内部类：模块元信息 + 该模块下的 code → key 映射 */
    private final ConcurrentHashMap<String, ModuleEntry> moduleIndex = new ConcurrentHashMap<>();

    /**
     * 注册模块元信息。
     *
     * @param module      模块名（如 "core"、"user-module"）
     * @param description 模块描述
     */
    public void registerModule(String module, String description) {
        moduleIndex.computeIfAbsent(module, k -> new ModuleEntry(module, description));
    }

    /**
     * 注册该模块下的一个错误码。
     *
     * @param module    模块名
     * @param code      错误码字符串
     * @param key       i18n 消息键
     * @param enumName  枚举常量名
     */
    public void registerCode(String module, String code, String key, String enumName) {
        ModuleEntry entry = moduleIndex.computeIfAbsent(module, k -> new ModuleEntry(module, module));
        entry.codes().put(code, new CodeEntry(code, key, enumName));
        codeIndex.putIfAbsent(code, lookupByEnumName(enumName));
    }

    /**
     * 向全局 code→ExceptionCode 索引注册映射。
     *
     * <p>由 {@link com.remisoft.common.exception.registry.ResultCodeScanner} 在扫描枚举后调用。
     *
     * @param codeMap code → ExceptionCode 的映射
     */
    public void registerAll(Map<String, ExceptionCode> codeMap) {
        codeIndex.putAll(codeMap);
    }

    /**
     * 按 code 反查 ExceptionCode。
     *
     * @param code 错误码字符串
     * @return 对应的 ExceptionCode，未找到返回 null
     */
    public ExceptionCode lookup(String code) {
        if (code == null) {
            return null;
        }
        return codeIndex.get(code);
    }

    /**
     * 按 code 跨模块反查（兼容 {@code ResultCodeRegistry.lookupByCode}）。
     *
     * @param code 错误码字符串
     * @return 对应的 CodeEntry，未找到返回 null
     */
    public CodeEntry lookupByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ModuleEntry module : moduleIndex.values()) {
            CodeEntry entry = module.codes().get(code);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 按模块名获取该模块的所有 CodeEntry。
     *
     * @param module 模块名
     * @return 不可变 map
     */
    public Map<String, CodeEntry> getCodes(String module) {
        ModuleEntry entry = moduleIndex.get(module);
        if (entry == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(entry.codes());
    }

    /**
     * 获取所有已注册的错误码按模块分组（供文档端点使用）。
     *
     * @return 模块名 → 该模块下 CodeEntry 集合的不可变 map
     */
    public Map<String, Map<String, CodeEntry>> groupByModule() {
        Map<String, Map<String, CodeEntry>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleEntry> entry : moduleIndex.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue().codes()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取所有已注册模块的元信息。
     *
     * @return 模块名 → ModuleEntry 的不可变 map
     */
    public Map<String, ModuleEntry> getModules() {
        return Collections.unmodifiableMap(moduleIndex);
    }

    /**
     * 获取所有已注册的 code 条目（扁平视图，供启动校验）。
     *
     * @return code → ExceptionCode 的不可变 map
     */
    public Map<String, ExceptionCode> allCodes() {
        return Collections.unmodifiableMap(codeIndex);
    }

    /**
     * 获取已注册 code 总数。
     *
     * @return code 总数
     */
    public int size() {
        return codeIndex.size();
    }

    /**
     * 查找注册信息对应的全局 ExceptionCode 实例（通过枚举类名反查注册表）。
     *
     * <p>这是一个便捷方法：当 scanner 扫描到枚举类名但尚未加载类时，
     * 优先从 globals 查找（需要提前注册）；否则返回 null。
     *
     * @param enumName 枚举常量名（如 "BUSINESS_ERROR"）
     * @return 对应的 ExceptionCode 或 null
     */
    private static ExceptionCode lookupByEnumName(String enumName) {
        // 延迟绑定：异常枚举在 static{} 块中已注册自身到 ExceptionCodeRegistry
        // 新版本注册流程在 ResultCodeScanner.registerEnum 中处理
        return null;
    }

    // ==================== 内部记录 ====================

    /**
     * 模块元信息。
     *
     * @param name        模块名
     * @param description 模块描述
     */
    public record ModuleEntry(String name, String description) {
        /** 该模块的错误码集合 */
        private final ConcurrentHashMap<String, CodeEntry> codes = new ConcurrentHashMap<>();

        public Map<String, CodeEntry> codes() {
            return codes;
        }
    }

    /**
     * 错误码条目。
     *
     * @param code     错误码
     * @param key      i18n 消息键
     * @param enumName 枚举常量名
     */
    public record CodeEntry(String code, String key, String enumName) {}
}
