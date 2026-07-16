package com.njydsz.agent.domain.prompt;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt 模板值对象
 *
 * <p>支持 SpEL 变量替换的 Prompt 模板，如：
 * <pre>{@code
 * PromptTemplate t = new PromptTemplate("RT001", "你是项目管理助手。当前项目：#{projectName}", "v1");
 * String rendered = t.render(Map.of("projectName", "南京云顶 PMIS"));
 * }</pre>
 *
 * @author ydsy-pmis-team
 * @since 1.0.0
 */
public final class PromptTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String content;
    private final String version;
    private final String description;

    public PromptTemplate(String code, String content, String version, String description) {
        this.code = Objects.requireNonNull(code, "code 不能为 null");
        this.content = Objects.requireNonNull(content, "content 不能为 null");
        this.version = version != null ? version : "1.0";
        this.description = description;
    }

    /**
     * 渲染模板（简单 #{var} 替换）
     *
     * <p>使用 SpEL 风格的 #{variableName} 占位符。
     * 实际 SpEL 渲染由 {@code SpelPromptRenderer} 实现。
     *
     * @param variables 变量映射
     * @return 渲染后的字符串
     */
    public String render(Map<String, Object> variables) {
        String result = content;
        if (variables == null || variables.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "#{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    public String getCode() { return code; }
    public String getContent() { return content; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return "PromptTemplate{code='" + code + "', version='" + version + "'}";
    }
}
