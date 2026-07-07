/**
 * Prompt 模板化与版本管理（P2-2）。
 *
 * <p>对标 Coze / Dify 的 Prompt 管理能力：
 * <ul>
 *   <li>{@link com.njydsz.pmis.agent.engine.prompt.PromptTemplateRenderer} — ${var} 变量替换引擎</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.prompt.PromptTemplateRegistry} — 模板注册中心（DB + 内置降级）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.prompt.BuiltInPromptTemplates} — 内置默认模板（无 DB 降级）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.prompt.PromptTemplateCodes} — 模板编码常量</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
package com.njydsz.pmis.agent.engine.prompt;
