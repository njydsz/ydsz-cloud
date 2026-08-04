package com.remisoft.common.safe.xss;

import com.remisoft.common.json.deserializer.JsonDeserializer;
import com.remisoft.common.json.reader.JSONReader;

/**
 * XSS 防护反序列化器（基于 YdszJson 引擎）
 *
 * <p>实现 YdszJson {@link JsonDeserializer}，在 JSON 反序列化时对字符串值进行 XSS 清洗。
 * 通过注册到 YdszJson 的全局反序列化器，所有字符串字段在解析时自动过滤 XSS 攻击。
 *
 * <p>清洗规则委托给 {@link EscapeUtils#clean(String)}，包括：
 * <ul>
 *   <li>移除 {@code <script>} 标签及其内容</li>
 *   <li>移除 {@code javascript:}、{@code vbscript:}、{@code data:} 等危险协议</li>
 *   <li>移除 {@code on*} 事件属性（如 onclick、onload 等）</li>
 *   <li>HTML 实体编码特殊字符：{@code < > " ' &}</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class XssStringDeserializer implements JsonDeserializer<String> {

    @Override
    public String deserialize(JSONReader in) {
        String value = in.readString();
        if (value == null) {
            return null;
        }
        return EscapeUtils.clean(value).trim();
    }
}
