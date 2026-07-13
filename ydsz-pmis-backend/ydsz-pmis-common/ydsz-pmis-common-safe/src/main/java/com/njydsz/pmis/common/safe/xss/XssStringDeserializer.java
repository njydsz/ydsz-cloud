package com.njydsz.pmis.common.safe.xss;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * XSS 防护 Jackson 反序列化器
 *
 * <p>自定义 Jackson {@link StdDeserializer}，在 JSON 反序列化时对字符串值进行 XSS 清洗。
 * 通过注册到 Jackson ObjectMapper 的全局反序列化器，所有字符串字段在解析时自动过滤 XSS 攻击。
 *
 * <p>清洗规则委托给 {@link EscapeUtils#clean(String)}，包括：
 * <ul>
 *   <li>移除 {@code <script>} 标签及其内容</li>
 *   <li>移除 {@code javascript:}、{@code vbscript:}、{@code data:} 等危险协议</li>
 *   <li>移除 {@code on*} 事件属性（如 onclick、onload 等）</li>
 *   <li>HTML 实体编码特殊字符：{@code < > " ' &}</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 */
public class XssStringDeserializer extends StdDeserializer<String> {

    private static final long serialVersionUID = 1L;

    public XssStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null) {
            return null;
        }
        return EscapeUtils.clean(value).trim();
    }
}
