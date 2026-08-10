package com.njydsz.common.exception.code;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * 统一错误码注册表。
 *
 * <p>取代了历史上 {@code com.njydsz.common.exception.enums.ExceptionCodeRegistry}（静态工具类）
 * 与旧版模块注册表的双轨设计，作为唯一的全局错误码来源。
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
 * @author ydsz-team
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
        // 注意：全局 code→ExceptionCode 反查索引（codeIndex）由 registerAll(codeMap) 统一填充，
        // 此处仅维护按模块的 CodeEntry 明细（供 groupByModule / getCodes / lookupByCode 使用）。
        // 早期版本曾在此处调用 lookupByEnumName(enumName) 直接写入 codeIndex，但该辅助方法只能拿到
        // 枚举常量名而无法解析出真实的 ExceptionCode 实例，且 ConcurrentHashMap 禁止 null 值，
        // 会在扫描注册首条常量时抛出 NullPointerException，故移除。
    }

    /**
     * 向全局 code→ExceptionCode 索引注册映射。
     *
     * <p>由 {@link com.njydsz.common.exception.registry.ExceptionCodeScanner} 在扫描枚举后调用。
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
     * 按 code 跨模块反查（兼容 {@code ErrorCodeTable.lookupByCode}）。
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

    // ==================== 内部记录 ====================

    /**
     * 模块元信息。
     *
     * <p>使用普通类而非 record：模块下挂载一个可变的错误码集合（{@link ConcurrentHashMap}），
     * record 不允许声明实例字段（仅允许 record 组件或 static 字段），故此处用常规类承载可变状态。
     *
     * <p>该对象仅作为 {@link #moduleIndex} 的值按模块名（key）检索，不直接参与相等性比较，
     * 因此沿用默认 {@link Object} 的 identity 语义即可。
     */
    public static final class ModuleEntry {
        private final String name;
        private final String description;
        /** 该模块的错误码集合（可变，启动期填充） */
        private final ConcurrentHashMap<String, CodeEntry> codes = new ConcurrentHashMap<>();

        public ModuleEntry(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

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
