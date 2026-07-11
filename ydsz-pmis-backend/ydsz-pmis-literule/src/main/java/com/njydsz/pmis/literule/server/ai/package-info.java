/**
 * 规则引擎 - AI 辅助规则生成层。
 *
 * <p>基于 LLM 的"自然语言生成规则"能力，业务人员用自然语言描述规则，
 * AI 自动转换为 literule DSL / 表达式。覆盖以下场景：
 * <ul>
 *   <li>规则草稿生成（"当合同金额超过 100 万时触发二级审批"）</li>
 *   <li>规则解释（"这条规则为什么命中"）</li>
 *   <li>规则建议（"建议增加 X 规则覆盖 Y 场景"）</li>
 *   <li>规则冲突检测（多条规则相互矛盾时告警）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>AI 生成结果必须由业务人员确认后才入库</li>
 *   <li>所有 AI 调用通过 {@code Agent} 模块的 LLM Provider 路由</li>
 *   <li>AI 生成的规则与人工规则统一管理，无差别对待</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.server.ai;
