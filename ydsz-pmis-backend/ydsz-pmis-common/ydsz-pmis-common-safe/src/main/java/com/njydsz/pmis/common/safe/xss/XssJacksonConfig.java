package com.njydsz.pmis.common.safe.xss;

import com.njydsz.pmis.common.json.YdszJson;

/**
 * XSS 防护配置工具类（基于 YdszJson 引擎）
 *
 * <p>提供注册 XSS 清洗反序列化器到 YdszJson 引擎的工具方法。
 * 注册后，所有 String 类型字段在 JSON 反序列化时自动进行 XSS 清洗。
 *
 * <p>使用方式：
 * <pre>
 * // 注册到 YdszJson 全局反序列化器
 * XssJacksonConfig.registerXssProtection();
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 5.0.0
 */
public class XssJacksonConfig {

    private XssJacksonConfig() {
    }

    /**
     * 注册全局 XSS 清洗反序列化器到 YdszJson 引擎
     *
     * <p>此方法会注册 String 类型的自定义反序列化器，
     * 所有通过 YdszJson 解析的 JSON 字符串字段都会自动进行 XSS 清洗。
     */
    public static void registerXssProtection() {
        YdszJson.register(String.class, new XssStringDeserializer());
    }
}
