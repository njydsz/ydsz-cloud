package com.njydsz.common.event.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON Schema 注册中心
 *
 * <p>管理事件类型到 JSON Schema 的映射关系。业务方在应用启动时注册各事件类型的 Schema，
 * OutboxService 在写入时自动查找对应 Schema 并执行校验。
 *
 * <p><b>线程安全：</b>本类的所有方法都是线程安全的（基于 ConcurrentHashMap）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;PostConstruct
 * public void init() {
 *     schemaRegistry.register("ORDER_CREATED", orderCreatedSchema);
 *     schemaRegistry.register("ORDER_CANCELLED", orderCancelledSchema);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see JsonSchemaValidator
 * @see SchemaValidationResult
 */
public class JsonSchemaRegistry {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(JsonSchemaRegistry.class);

    /** eventType → schemaJson 映射表 */
    private final Map<String, String> schemas = new ConcurrentHashMap<>();

    /**
     * 注册事件类型的 JSON Schema
     *
     * @param eventType  事件类型（如 ORDER_CREATED）
     * @param schemaJson JSON Schema 字符串
     * @return 本对象（支持链式调用）
     * @throws IllegalArgumentException eventType 或 schemaJson 为 null 或空
     */
    public JsonSchemaRegistry register(String eventType, String schemaJson) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new IllegalArgumentException("schemaJson must not be null or blank");
        }
        schemas.put(eventType.trim(), schemaJson);
        log.debug("Schema registered for eventType={}", eventType);
        return this;
    }

    /**
     * 批量注册事件类型的 JSON Schema
     *
     * @param schemaMap eventType → schemaJson 映射
     * @return 本对象（支持链式调用）
     */
    public JsonSchemaRegistry registerAll(Map<String, String> schemaMap) {
        if (schemaMap != null) {
            schemaMap.forEach(this::register);
        }
        return this;
    }

    /**
     * 注销事件类型的 JSON Schema
     *
     * @param eventType 事件类型
     * @return 本对象（支持链式调用）
     */
    public JsonSchemaRegistry unregister(String eventType) {
        if (eventType != null) {
            schemas.remove(eventType);
            log.debug("Schema unregistered for eventType={}", eventType);
        }
        return this;
    }

    /**
     * 获取事件类型的 JSON Schema
     *
     * @param eventType 事件类型
     * @return JSON Schema 字符串，若未注册则返回 null
     */
    public String getSchema(String eventType) {
        return eventType != null ? schemas.get(eventType) : null;
    }

    /**
     * 判断事件类型是否已注册 Schema
     *
     * @param eventType 事件类型
     * @return true 表示已注册
     */
    public boolean hasSchema(String eventType) {
        return eventType != null && schemas.containsKey(eventType);
    }

    /**
     * 获取已注册 Schema 数量
     *
     * @return 已注册数量
     */
    public int size() {
        return schemas.size();
    }

    /**
     * 清空所有已注册的 Schema
     */
    public void clear() {
        schemas.clear();
        log.debug("All schemas cleared");
    }
}
