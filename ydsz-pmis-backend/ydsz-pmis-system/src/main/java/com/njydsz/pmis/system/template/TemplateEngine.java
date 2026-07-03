package com.njydsz.pmis.message.template;

import java.util.Map;

/**
 * 模板引擎接口
 *
 * <p>用于渲染消息模板中的占位符，模板格式：{@code ${varName}} 或 {@code {{varName}}}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TemplateEngine {

    /**
     * 渲染模板
     *
     * @param template 模板内容，含 {@code ${var}} 占位符
     * @param params   参数映射
     * @return 渲染后文本
     */
    String render(String template, Map<String, Object> params);
}
