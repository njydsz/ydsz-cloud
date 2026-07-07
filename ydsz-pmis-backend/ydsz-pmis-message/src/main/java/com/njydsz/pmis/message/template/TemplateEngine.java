package com.njydsz.pmis.message.template;

import java.util.Map;

/**
 * 消息模板引擎接口。
 *
 * <p>用于渲染消息模板中的 {@code ${var}} 占位符，支持 {@code a.b.c} 嵌套 Map 取值。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TemplateEngine {

    /**
     * 渲染模板，将 {@code ${var}} 占位符替换为参数值，未命中时替换为空串。
     *
     * @param template 模板内容，含 {@code ${var}} 占位符
     * @param params   参数映射，可为 null
     * @return 渲染后文本；模板为空时返回空串
     */
    String render(String template, Map<String, Object> params);
}
