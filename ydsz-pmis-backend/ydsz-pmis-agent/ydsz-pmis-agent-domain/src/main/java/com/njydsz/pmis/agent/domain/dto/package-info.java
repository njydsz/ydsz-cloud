/**
 * Agent 模块 - 数据传输对象（DTO）层。
 *
 * <p>Agent 模块所有 API 的入参 / 出参 DTO，包括：
 * <ul>
 *   <li>{@code AgentRunRequestDTO}    - Agent 运行请求</li>
 *   <li>{@code AgentInternalExecuteDTO} - 内部调用 DTO（Feign 客户端使用）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DTO 必须显式标注 {@code @Schema}（OpenAPI）便于文档生成</li>
 *   <li>内部 DTO（仅服务间调用）放本包，外部 API DTO 放 {@code controller} 包附近</li>
 *   <li>DTO 字段变更必须考虑向后兼容，重大变更新增 v2 版本</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.domain.dto;
