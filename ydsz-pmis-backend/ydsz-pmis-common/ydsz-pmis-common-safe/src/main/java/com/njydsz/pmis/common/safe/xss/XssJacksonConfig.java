package com.njydsz.pmis.common.safe.xss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * XSS 防护 Jackson 配置工具类
 *
 * <p>提供注册 XSS 清洗反序列化器到 Jackson ObjectMapper 的工具方法。
 * 注册后，所有 String 类型字段在 JSON 反序列化时自动进行 XSS 清洗。
 *
 * <p>使用方式：
 * <pre>
 * // 方式一：注册到全局 ObjectMapper
 * ObjectMapper mapper = JsonUtils.getMapper();
 * XssJacksonConfig.registerModule(mapper);
 *
 * // 方式二：注册到自定义 ObjectMapper
 * ObjectMapper customMapper = new ObjectMapper();
 * XssJacksonConfig.registerModule(customMapper);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 */
public class XssJacksonConfig {

    private XssJacksonConfig() {
    }

    /**
     * 注册全局 XSS 清洗反序列化器到指定的 ObjectMapper
     *
     * <p>此方法会注册 String 类型的自定义反序列化器，
     * 所有通过该 ObjectMapper 解析的 JSON 字符串字段都会自动进行 XSS 清洗。
     *
     * @param mapper 待注册的 ObjectMapper
     */
    public static void registerModule(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule("XssStringModule");
        module.addDeserializer(String.class, new XssStringDeserializer());
        mapper.registerModule(module);
    }

    /**
     * 创建独立的 XSS 防护 SimpleModule
     *
     * <p>适用于需要手动控制模块注册时机的场景。
     *
     * @return XSS 防护 Jackson 模块
     */
    public static SimpleModule createModule() {
        SimpleModule module = new SimpleModule("XssStringModule");
        module.addDeserializer(String.class, new XssStringDeserializer());
        return module;
    }
}
