/**
 * LLM（大模型）Provider 适配层。
 *
 * <p>封装不同 LLM 服务提供方，统一 PMIS 内部调用 API：
 * <ul>
 *   <li>{@code SpringAiLlmProvider}   - OpenAI 兼容协议适配（P1-4 重构后基于 java.net.http.HttpClient，不依赖 spring-ai）</li>
 *   <li>{@code DashScopeLlmProvider}  - 阿里云通义千问（DashScope，基于 RestClient）</li>
 *   <li>{@code QianfanLlmProvider}    - 百度千帆（基于 RestClient）</li>
 *   <li>{@code MockLlmProvider}       - Mock 实现（单元测试 / 本地开发）</li>
 *   <li>{@code LlmProvider}           - Provider SPI 接口（含 chatForJson 默认方法，支持结构化输出）</li>
 *   <li>{@code LlmProviderRouter}     - 多 Provider 路由（按租户 / 按场景选择）</li>
 *   <li>{@code LlmHealthIndicator}    - LLM 健康检查（Actuator）</li>
 *   <li>{@code AbstractHttpLlmProvider} - HTTP 通用基类（超时/重试/降级/MDC 透传）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>业务侧统一通过 {@code LlmProviderRouter} 获取当前 Provider，禁止直接 new 具体实现</li>
 *   <li>新增 Provider 必须实现 {@code LlmProvider} 接口，并注册到 Router</li>
 *   <li>所有 Provider 必须支持超时（默认 30s）、重试（指数退避）、熔断</li>
 *   <li>API Key 通过 {@code SecretManager} 获取，禁止硬编码</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.engine.llm;
