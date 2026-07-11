package com.njydsz.pmis.agent.server.engine.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认 Prompt 模板渲染器实现（P2-2 落地）。
 *
 * <p>使用正则 {@code \$\{([\w.]+)\}} 匹配 {@code ${var}} 与 {@code ${a.b.c}} 形式占位符，
 * 支持 {@code a.b.c} 嵌套 Map 取值，未命中替换为空串。
 *
 * <p>与消息模块的 {@code DefaultTemplateEngine} 保持算法一致，确保整个项目
 * 模板渲染行为统一。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Component
public class DefaultPromptTemplateRenderer implements PromptTemplateRenderer {

    /** 占位符正则：匹配 ${var} 或 ${a.b.c} 形式的变量 */
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

    /**
     * 解析占位符 key 对应的值，支持 {@code a.b.c} 嵌套 Map 取值。
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
