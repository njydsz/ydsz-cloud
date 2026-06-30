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

    private static final Pattern PATTERN = Pattern.compile("\\$\\{([\\w.]+)\\}");

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
