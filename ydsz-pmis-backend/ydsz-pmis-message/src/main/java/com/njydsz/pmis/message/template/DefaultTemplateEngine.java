package com.njydsz.pmis.message.template;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认模板引擎实现
 *
 * <p>支持 {@code ${var}} 与 {@code {{var}}} 两种占位符语法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class DefaultTemplateEngine implements TemplateEngine {

    /** 占位符正则：匹配 ${var} 或 ${a.b.c} 形式的变量 */
    private static final Pattern PATTERN = Pattern.compile("\\$\\{([\\w.]+)\\}");

    /**
     * 渲染模板，将 {@code ${var}} 占位符替换为参数值，未命中时替换为空串。
     *
     * @param template 模板内容
     * @param params   参数映射
     * @return 渲染后文本
     */
    @Override
    public String render(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (params == null) {
            return template;
        }
        Matcher m = PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Object value = resolve(params, key);
            String replacement = Matcher.quoteReplacement(value == null ? "" : String.valueOf(value));
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析占位符 key 对应的值，支持 {@code a.b.c} 形式的嵌套 Map 取值。
     *
     * @param params 参数映射
     * @param key    占位符 key
     * @return 解析到的值，未命中返回 null
     */
    @SuppressWarnings("unchecked")
    private Object resolve(Map<String, Object> params, String key) {
        if (key.contains(".")) {
            String[] parts = key.split("\\.");
            Object cur = params;
            for (String p : parts) {
                if (cur instanceof Map) {
                    cur = ((Map<String, Object>) cur).get(p);
                } else {
                    return null;
                }
            }
            return cur;
        }
        return params.get(key);
    }
}
