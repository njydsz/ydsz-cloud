package com.njydsz.pmis.agent.server.engine.prompt;

import java.util.Map;

/**
 * Prompt 模板渲染器（P2-2 落地）。
 *
 * <p>将模板中的 {@code ${var}} 占位符替换为实际参数值，与消息模块的
 * {@code TemplateEngine} 使用相同的 {@code ${var}} 语法，保持项目一致性。
 *
 * <p>支持 {@code ${a.b.c}} 嵌套 Map 取值；未命中的占位符替换为空串。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
public interface PromptTemplateRenderer {

    /**
     * 渲染模板，将 {@code ${var}} 占位符替换为参数值。
     *
     * @param template 模板内容，含 {@code ${var}} 占位符
     * @param params   参数映射，可为 null
     * @return 渲染后文本；模板为 null/空时返回空串，params 为 null 时返回原模板
     */
    String render(String template, Map<String, Object> params);
}
