package com.njydsz.common.json;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.exception.JsonDeserializationException;

/**
 * JSON 反序列化安全工具类（静态入口）。
 *
 * <p>封装 {@link com.njydsz.common.json.autotype.AutoTypeChecker} 的白名单 API，
 * 为缓存导出/导入、消息队列反序列化等场景提供统一的类型安全校验入口。
 *
 * <p><b>设计定位：</b></p>
 * <ul>
 *   <li>对 {@code CacheExportImport}、MQ Consumer 等反序列化入口提供便捷的白名单管理</li>
 *   <li>"防御性注册"模式：启动时预注册已知安全类型，运行时校验目标类型</li>
 *   <li>所有方法均为静态方法、无状态（委托给 {@link AutoTypeChecker}）</li>
 * </ul>
 *
 * <p><b>典型工作流：</b></p>
 * <pre>{@code
 * // 阶段 1：应用启动时注册安全类型（防御性编程，一劳永逸）
 * JsonSecurityUtils.registerCacheTypes(
 *     UserCacheEntry.class,
 *     DeptCacheEntry.class,
 *     ConfigCacheEntry.class
 * );
 *
 * // 阶段 2：运行时校验
 * if (JsonSecurityUtils.isTypeAllowed(targetClass)) {
 *     return YdszJson.fromJson(json, targetClass);
 * }
 *
 * // 阶段 3：反序列化前校验（异常模式）
 * JsonSecurityUtils.validateJsonForDeserialization(json, targetClass);
 * // 通过校验后可安全反序列化
 * }</pre>
 *
 * <p><b>与 {@link AutoTypeChecker} 的关系：</b></p>
 * <p>AutoTypeChecker 是底层白名单引擎，本工具类在其基础上提供带业务语义的便捷方法
 * （如 "registerCacheTypes" / "registerMessageTypes"），并在验证失败时抛出统一的
 * {@link JsonDeserializationException}。</p>
 *
 * <p><b>规范要求（R10）：</b>禁止在业务代码中直接使用 {@code AutoTypeChecker.addToWhitelist()}，
 * 所有白名单操作应统一通过 {@link JsonSecurityUtils} 进行，便于审计和安全事件溯源。</p>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see com.njydsz.common.json.autotype.AutoTypeChecker
 */
public final class JsonSecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonSecurityUtils.class);

    private JsonSecurityUtils() {
        throw new UnsupportedOperationException("JsonSecurityUtils is a utility class and cannot be instantiated");
    }

    // ==================== 类型白名单校验 ====================

    /**
     * 检查类型是否允许 JSON 反序列化（委托 AutoTypeChecker 校验）。
     *
     * <p>内部使用层次化匹配：精确类型匹配 → 父类向上追溯 → 接口匹配。
     * null 类型始终返回 {@code false}。
     *
     * @param type 要检查的目标类型
     * @return 如果类型在安全白名单中返回 {@code true}
     * @since 1.2.0
     */
    public static boolean isTypeAllowed(Class<?> type) {
        return AutoTypeChecker.isAllowed(type);
    }

    // ==================== 批量注册方法 ====================

    /**
     * 批量注册缓存相关类型到白名单（防御性编程）。
     *
     * <p>供缓存导出/导入模块（如 {@code CacheExportImport}）在应用启动时调用，
     * 将已知的缓存值类型（如 DTO、VO、缓存条目类等）预先注册到白名单。
     *
     * <p>重复注册同一类型不会产生副作用（AutoTypeChecker 内部去重）。
     *
     * @param types 要注册的缓存相关类型变长参数，不能为 null，不能包含 null 元素
     * @throws IllegalArgumentException 如果 types 为 null 或包含 null 元素
     * @since 1.2.0
     */
    public static void registerCacheTypes(Class<?>... types) {
        validateTypesNotNull(types, "cache");
        for (Class<?> type : types) {
            AutoTypeChecker.addToWhitelist(type);
            log.debug("Registered cache type to AutoType whitelist: {}", type.getName());
        }
        if (log.isDebugEnabled()) {
            log.debug("Batch registered {} cache type(s). Total whitelist size: {}",
                    types.length, AutoTypeChecker.size());
        }
    }

    /**
     * 批量注册消息队列相关类型到白名单（防御性编程）。
     *
     * <p>供消息消费者模块在应用启动时调用，将 MQ 消息体中可能出现的具体事件类型、
     * 命令类型等预先注册到白名单。
     *
     * <p>典型使用场景：
     * <pre>{@code
     * // 在 MQ Consumer 配置类中
     * JsonSecurityUtils.registerMessageTypes(
     *     OrderCreatedEvent.class,
     *     OrderPaidEvent.class,
     *     OrderCancelledEvent.class
     * );
     * }</pre>
     *
     * @param types 要注册的消息队列相关类型变长参数，不能为 null，不能包含 null 元素
     * @throws IllegalArgumentException 如果 types 为 null 或包含 null 元素
     * @since 1.2.0
     */
    public static void registerMessageTypes(Class<?>... types) {
        validateTypesNotNull(types, "message");
        for (Class<?> type : types) {
            AutoTypeChecker.addToWhitelist(type);
            log.debug("Registered message type to AutoType whitelist: {}", type.getName());
        }
        if (log.isDebugEnabled()) {
            log.debug("Batch registered {} message type(s). Total whitelist size: {}",
                    types.length, AutoTypeChecker.size());
        }
    }

    // ==================== 反序列化前校验 ====================

    /**
     * 反序列化前校验目标类型是否在安全白名单中。
     *
     * <p>此方法应在调用 {@code YdszJson.fromJson()} 或类似反序列化操作前调用。
     * 校验失败会抛出 {@link JsonDeserializationException}，调用方应阻止反序列化并上报安全事件。
     *
     * <p>校验流程：
     * <ol>
     *   <li>检查 targetType 是否为 null</li>
     *   <li>调用 {@link #isTypeAllowed(Class)} 进行白名单校验</li>
     *   <li>未通过时抛出异常并记录 warn 日志</li>
     * </ol>
     *
     * <p>示例：
     * <pre>{@code
     * try {
     *     JsonSecurityUtils.validateJsonForDeserialization(json, targetType);
     *     // 校验通过，继续反序列化
     *     return YdszJson.fromJson(json, targetType);
     * } catch (JsonDeserializationException e) {
     *     // 阻止反序列化，记录安全事件
     *     securityAudit.log(e);
     *     throw e;
     * }
     * }</pre>
     *
     * @param json 待反序列化的 JSON 字符串（用于日志上下文，可为 null，此时跳过目标类型校验）
     * @param targetType 期望反序列化的目标类型
     * @throws JsonDeserializationException 如果目标类型不在安全白名单中
     * @since 1.2.0
     */
    public static void validateJsonForDeserialization(String json, Class<?> targetType) {
        Objects.requireNonNull(targetType, "Target type must not be null");
        if (!AutoTypeChecker.isAllowed(targetType)) {
            String targetTypeStr = targetType.getName();
            log.warn("Blocked deserialization of non-whitelisted type: {} (json fragments: {})",
                    targetTypeStr,
                    truncateForLog(json, 120));
            throw new JsonDeserializationException(
                    JsonDeserializationException.VALIDATION_ERROR,
                    "Type not in AutoType whitelist, deserialization blocked: " + targetTypeStr
            );
        }
    }

    // ==================== 监控/诊断 API ====================

    /**
     * 获取当前已注册的安全白名单类型快照（供监控/日志）。
     *
     * <p>返回的白名单类型名称为全限定名的不可变集合。可用于：
     * <ul>
     *   <li>运维监控面板展示当前生效的白名单规则</li>
     *   <li>安全审计日志记录</li>
     *   <li>健康检查端点（health check endpoint）</li>
     * </ul>
     *
     * @return 当前白名单类型全限定名集合，不会为 null
     * @since 1.2.0
     */
    public static Set<String> getRegisteredTypes() {
        return AutoTypeChecker.getWhitelist();
    }

    /**
     * 获取当前白名单大小（供监控指标采集）。
     *
     * @return 白名单中已注册的规则数量
     * @since 1.2.0
     */
    public static int getRegisteredTypeCount() {
        return AutoTypeChecker.size();
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 校验类型参数数组不为 null 且不含 null 元素。
     *
     * @param types 类型数组
     * @param category 类别名称（用于错误信息）
     * @throws IllegalArgumentException 如果校验失败
     */
    private static void validateTypesNotNull(Class<?>[] types, String category) {
        if (types == null) {
            throw new IllegalArgumentException(
                    category + " types must not be null");
        }
        for (int i = 0; i < types.length; i++) {
            if (types[i] == null) {
                throw new IllegalArgumentException(
                        category + " types[" + i + "] is null (null elements not allowed)");
            }
        }
    }

    /**
     * 截断字符串用于日志输出，防止超长 JSON 污染日志。
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本（超长时末尾添加 "..."）
     */
    private static String truncateForLog(String text, int maxLength) {
        if (text == null) {
            return "<null>";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
