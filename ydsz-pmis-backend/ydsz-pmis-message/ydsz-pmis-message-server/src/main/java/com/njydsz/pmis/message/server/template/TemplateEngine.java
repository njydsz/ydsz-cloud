package com.njydsz.pmis.message.server.template;

import java.util.Map;
import java.util.Set;

/**
 * 消息模板引擎接口。
 *
 * <p>渲染能力分层（P0-3 增强）：
 * <ul>
 *   <li>变量替换：{@code ${var}} / {@code ${a.b.c}} 嵌套 Map 取值，未命中替换为空串</li>
 *   <li>条件渲染：{@code {{#if var}}A{{else}}B{{/if}}}，支持 truthy 判定与 else 分支</li>
 *   <li>循环渲染：{@code {{#each list}}...{{this}}...{{this.prop}}...{{@index}}...{{/each}}}</li>
 *   <li>必填参数校验：{@link #render(String, Map, Set)} 缺失时抛 {@code SysException}</li>
 * </ul>
 *
 * <p>多渠道差异化由 {@code TemplateService.loadByCodeAndChannel} 在模板加载层实现，
 * 引擎仅负责按给定模板内容渲染。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TemplateEngine {

    /**
     * 渲染模板：将 {@code ${var}} 占位符替换为参数值，处理 {@code {{#if}}} / {@code {{#each}}} 块，
     * 未命中变量替换为空串。
     *
     * @param template 模板内容，含 {@code ${var}} 占位符与可选块语法
     * @param params   参数映射，可为 null
     * @return 渲染后文本；模板为空时返回空串
     */
    String render(String template, Map<String, Object> params);

    /**
     * P0-3: 渲染模板并校验必填参数。
     *
     * <p>在渲染前校验 {@code requiredKeys} 中的 key 是否存在于 {@code params} 且非 null / 非空白字符串，
     * 任一缺失抛 {@link com.njydsz.pmis.common.exception.custom.SysException}（错误码 MISSING_PARAMETER）。
     *
     * @param template     模板内容
     * @param params       参数映射，可为 null（此时若有 requiredKeys 则必抛异常）
     * @param requiredKeys 必填参数 key 集合，null 或空时跳过校验
     * @return 渲染后文本
     */
    String render(String template, Map<String, Object> params, Set<String> requiredKeys);
}
