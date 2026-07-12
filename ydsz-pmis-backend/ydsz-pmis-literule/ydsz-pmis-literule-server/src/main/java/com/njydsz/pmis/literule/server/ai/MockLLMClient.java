package com.njydsz.pmis.literule.server.ai;

import com.njydsz.pmis.agent.server.engine.llm.impl.MockLlmClient;

/**
 * Mock LLM 客户端（P2-15 AI 增强）
 *
 * <p>提供确定性的离线响应，用于：
 * <ul>
 *   <li>开发环境无 API Key 时的本地调试</li>
 *   <li>单元测试中的可重复断言</li>
 *   <li>CI 流水线中不依赖外部网络</li>
 * </ul>
 *
 * <p>响应基于简单的规则模板（不调用任何真实 LLM），
 * 输出格式与真实 LLM 一致，便于业务层无差别消费。
 *
 * <p><b>P0-2 架构优化</b>：继承 {@link MockLlmClient}（common 模块统一实现），
 * 消除重复代码。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class MockLLMClient extends MockLlmClient implements LLMClient {

}
