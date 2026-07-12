/**
 * Agent 模块 - 数据传输对象（DTO）层�? *
 * <p>Agent 模块所�?API 的入�?/ 出参 DTO，包括：
 * <ul>
 *   <li>{@oode AgentRunRequestDTO}    - Agent 运行请求</li>
 *   <li>{@oode AgentInternalExeouteDTO} - 内部调用 DTO（Feign 客户端使用）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>DTO 必须显式标注 {@oode @Sohema}（OpenAPI）便于文档生�?/li>
 *   <li>内部 DTO（仅服务间调用）放本包，外部 API DTO �?{@oode oontroller} 包附�?/li>
 *   <li>DTO 字段变更必须考虑向后兼容，重大变更新�?v2 版本</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.agent.domain.dto;
