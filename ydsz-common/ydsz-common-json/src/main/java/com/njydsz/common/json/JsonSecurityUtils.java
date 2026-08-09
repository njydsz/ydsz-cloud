package com.njydsz.common.json;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.exception.JsonDeserializationException;

/**
 * JSON 安全工具类——封装反序列化安全白名单 API。
 *
 * <p>对标 FastJSON 的 safeMode 开关与 Jackson {@code enableDefaultTyping} 安全检查器
 * 的语义，提供针对反序列化场景的安全校验能力。
 *
 * <p><b>使用方式：</b></p>
 * <pre>{@code
 * // 启动时注册安全类型
 * JsonSecurityUtils.registerCacheTypes(
 *     UserDTO.class, OrderDTO.class
 * );
 *
 * // 在 CacheExportImport 等场景下校验
 * JsonSecurityUtils.validateJsonForDeserialization(cachedJson, UserDTO.class);
 * UserDTO user = YdszJson.fromJson(cachedJson, UserDTO.class);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see AutoTypeChecker
 */
public final class JsonSecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonSecurityUtils.class);

    /** 日志截断长度：防止超长 JSON 输入污染日志文件 */
    private static final int LOG_TRUNCATE_LENGTH = 120;

    private JsonSecurityUtils() {
    }

    /**
     * 检查指定类型是否在安全白名单中。
     *
     * <p>直接委托 {@link AutoTypeChecker#isAllowed(Class)}。
     *
     * @param type 要检查的类型
     * @return true 表示允许反序列化
     */
    public static boolean isTypeAllowed(Class<?> type) {
        return AutoTypeChecker.isAllowed(type);
    }

    /**
     * 批量注册缓存相关类型到安全白名单。
     *
     * <p>防御性编程，供 CacheExportImport、Redis 缓存序列化等场景使用。
     * 应在应用启动阶段调用一次。
     *
     * @param types 要注册的类型数组
     */
    public static void registerCacheTypes(Class<?>... types) {
        if (types == null) {
            return;
        }
        for (Class<?> type : types) {
            AutoTypeChecker.addToWhitelist(type);
        }
    }

    /**
     * 批量注册消息队列相关类型到安全白名单。
     *
     * <p>供 MQ Consumer、消息回调等需要反序列化外部消息的场景使用。
     *
     * @param types 要注册的类型数组
     */
    public static void registerMessageTypes(Class<?>... types) {
        if (types == null) {
            return;
        }
        for (Class<?> type : types) {
            AutoTypeChecker.addToWhitelist(type);
        }
    }

    /**
     * 校验 JSON 字符串是否可安全反序列化到目标类型。
     *
     * <p>校验失败抛出 {@link JsonDeserializationException}（错误码 VALIDATION_ERROR），
     * 而非返回 boolean，避免调用方遗漏判断导致安全漏洞。
     *
     * @param json 待反序列化的 JSON 字符串（仅用于日志）
     * @param targetType 反序列化目标类型
     * @throws JsonDeserializationException 当类型不在白名单时抛出
     */
    public static void validateJsonForDeserialization(String json, Class<?> targetType) {
        if (!isTypeAllowed(targetType)) {
            String truncatedJson = truncateForLog(json);
            log.warn("JsonSecurityUtils: type '{}' not in whitelist, json={}",
                    targetType.getName(), truncatedJson);
            throw new JsonDeserializationException(
                    JsonDeserializationException.VALIDATION_ERROR,
                    "Type not in deserialization whitelist: " + targetType.getName()
            );
        }
    }

    /**
     * 获取当前已注册类型快照。
     *
     * @return 已注册类型全限定名集合
     */
    public static Set<String> getRegisteredTypes() {
        return AutoTypeChecker.getWhitelist();
    }

    /**
     * 获取已注册类型数量。
     *
     * @return 白名单大小
     */
    public static int getRegisteredTypeCount() {
        return AutoTypeChecker.size();
    }

    /**
     * 截断超长字符串用于日志输出。
     */
    private static String truncateForLog(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= LOG_TRUNCATE_LENGTH) {
            return value;
        }
        return value.substring(0, LOG_TRUNCATE_LENGTH) + "...(truncated)";
    }
}
