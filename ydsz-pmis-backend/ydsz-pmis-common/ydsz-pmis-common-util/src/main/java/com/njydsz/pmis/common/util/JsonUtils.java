package com.njydsz.pmis.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 类型安全的 JSON 工具（封装 Jackson ObjectMapper）
 *
 * <p>统一使用 Jackson 作为 JSON 引擎，替代 fastjson2。
 * 优势：
 * <ul>
 *   <li>与 Spring Boot 内置 JSON 引擎一致，避免双引擎序列化差异</li>
 *   <li>Jackson 安全性更好，无 fastjson 历史漏洞</li>
 *   <li>社区活跃度高，生态丰富</li>
 * </ul>
 *
 * <p>所有方法对 {@code null} 和空白字符串返回 {@code null}（或空集合），不会抛出 NPE。
 * 解析异常统一返回 null（保持与原 fastjson2 版本行为一致）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public final class JsonUtils {

    /** 全局共享 ObjectMapper（线程安全） */
    private static final ObjectMapper MAPPER = createMapper();

    /** 全局共享 ObjectMapper（格式化输出） */
    private static final ObjectMapper PRETTY_MAPPER = createMapper().copy()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtils() {
    }

    /**
     * 创建并配置 ObjectMapper
     *
     * @return 配置好的 ObjectMapper
     */
    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 Java 8 时间模块
        mapper.registerModule(new JavaTimeModule());
        // 禁用日期时间序列化为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 反序列化时忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 空对象允许序列化
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }

    /**
     * 获取底层 ObjectMapper（供高级用法使用）
     *
     * @return 共享的 ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return MAPPER;
    }

    /**
     * JSON 字符串 → {@code Map<String, Object>}
     *
     * @param json JSON 字符串，可为 null/空白
     * @return 解析后的 Map；输入为 null/空白时返回 null
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[JsonUtils] parseMap 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON 字符串 → {@code List<Object>}
     *
     * @param json JSON 数组字符串，可为 null/空白
     * @return 解析后的 List；输入为 null/空白时返回 null
     */
    public static List<Object> parseList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[JsonUtils] parseList 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON 字符串 → 指定类型对象
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型 Class
     * @param <T>   目标类型
     * @return 解析后的对象；输入为 null/空白时返回 null
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("[JsonUtils] parseObject 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON 字符串 → 复杂泛型类型对象（基于 {@link TypeReference}，类型安全）
     *
     * <p>示例：
     * <pre>{@code
     * Map<String, List<Integer>> m = JsonUtils.parseObject(json,
     *         new TypeReference<Map<String, List<Integer>>>() {});
     * }</pre>
     *
     * @param json JSON 字符串
     * @param type 目标类型 TypeReference
     * @param <T>  目标类型
     * @return 解析后的对象；输入为 null/空白时返回 null
     */
    public static <T> T parseObject(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("[JsonUtils] parseObject(TypeReference) 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 对象 → JSON 字符串
     *
     * @param obj 任意对象
     * @return JSON 字符串；输入为 null 时返回 null
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[JsonUtils] toJson 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 对象 → 格式化 JSON 字符串（带缩进）
     *
     * @param obj 任意对象
     * @return 格式化的 JSON 字符串；输入为 null 时返回 null
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return PRETTY_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[JsonUtils] toPrettyJson 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON 字符串 → 指定类型对象（宽松模式，解析失败抛出 RuntimeException）
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型 Class
     * @param <T>   目标类型
     * @return 解析后的对象
     * @throws RuntimeException 解析失败时抛出
     */
    public static <T> T parseObjectStrict(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 解析失败: " + e.getMessage(), e);
        }
    }
}
