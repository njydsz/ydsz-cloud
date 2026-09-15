package com.njydsz.message.server.template;

import java.util.Map;
import java.util.Set;

/**
 * 消息模板引擎接口。
 *
 * <p>渲染能力分层（P0-3 增强）：
 *
 * <ul>
 *   <li>变量替换：{@code ${var}} / {@code ${a.b.c}} 嵌套 Map 取值，未命中替换为空串
 *   <li>条件渲染：{@code {{#if var}}A{{else}}B{{/if}}}，支持 truthy 判定与 else 分支
 *   <li>循环渲染：{@code {{#each list}}...{{this}}...{{this.prop}}...{{@index}}...{{/each}}}
 *   <li>必填参数校验：{@link #render(String, Map, Set)} 缺失时抛 {@code SysException}
 * </ul>
 *
 * <p>多渠道差异化由 {@code TemplateService.loadByCodeAndChannel} 在模板加载层实现， 引擎仅负责按给定模板内容渲染。
 *
 * <p><b>与 ydsz-common-notify 同名接口的边界（ADR-7，见
 * docs/architecture/adr/ADR-009-public-capability-convergence.md）：</b>
 * 本接口是<b>模板内容渲染语法引擎</b>（按传入模板内容渲染，含条件/循环块语法）；
 * common-notify {@code TemplateEngine} 是<b>模板注册与按 ID 渲染入口</b>（SpEL + 模板注册表 + 热加载）。
 * 二者能力分层不同，不合并；非消息域的模板渲染需求一律优先 common-notify。
 * <b>TODO（ADR-7 决议 3）：</b>本接口计划重命名为 {@code MessageTemplateRenderer}，消除与 common-notify 的同名歧义。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface TemplateEngine {

  /**
   * 渲染模板：将 {@code ${var}} 占位符替换为参数值，处理 {@code {{#if}}} / {@code {{#each}}} 块， 未命中变量替换为空串。
   *
   * @param template 模板内容，含 {@code ${var}} 占位符与可选块语法
   * @param params 参数映射，可为 null
   * @return 渲染后文本；模板为空时返回空串
   */
  String render(String template, Map<String, Object> params);

  /**
   * P0-3: 渲染模板并校验必填参数。
   *
   * <p>在渲染前校验 {@code requiredKeys} 中的 key 是否存在于 {@code params} 且非 null / 非空白字符串， 任一缺失抛 {@link
   * com.njydsz.common.exception.custom.SysException}（错误码 MISSING_PARAMETER）。
   *
   * @param template 模板内容
   * @param params 参数映射，可为 null（此时若有 requiredKeys 则必抛异常）
   * @param requiredKeys 必填参数 key 集合，null 或空时跳过校验
   * @return 渲染后文本
   */
  String render(String template, Map<String, Object> params, Set<String> requiredKeys);
}
