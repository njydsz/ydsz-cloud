package com.njydsz.pmis.agent.server.engine.llm.impl;

import com.njydsz.pmis.agent.api.llm.LlmClient;
import com.njydsz.pmis.agent.api.llm.LlmException;

import java.util.List;
import java.util.Map;

/**
 * Mock LLM 客户端（P0-2 架构优化，2026-07-12 迁移到 agent 模块）。
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
 * <p>合并自 literule 模块的 {@code MockLLMClient}。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-2)
 */
public class MockLlmClient implements LlmClient {

    /** 提供方标识 */
    public static final String PROVIDER = "MOCK";

    /** 默认模型标识 */
    public static final String DEFAULT_MODEL = "mock-llm-v1";

    @Override
    public String chat(String systemPrompt, String userPrompt, Map<String, Object> options) throws LlmException {
        return generateMockResponse(userPrompt);
    }

    @Override
    public String chatWithHistory(List<Map<String, String>> messages, Map<String, Object> options) throws LlmException {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        Map<String, String> last = messages.get(messages.size() - 1);
        return generateMockResponse(last.get("content"));
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String model() {
        return DEFAULT_MODEL;
    }

    /**
     * 基于输入生成 mock 响应（确定性：根据输入内容生成可预测的输出）
     *
     * <p>输入含 {@code naturalLanguage} → 自然语言转表达式
     * <p>输入含 {@code describe} → 生成规则描述
     * <p>输入含 {@code optimize} → 给出优化建议
     */
    private String generateMockResponse(String userPrompt) {
        if (userPrompt == null || userPrompt.isEmpty()) {
            return "{}";
        }
        String lower = userPrompt.toLowerCase();
        if (lower.contains("natural") || lower.contains("自然语言") || lower.contains("nl2")) {
            return "evmRedCount >= 3";
        }
        if (lower.contains("describe") || lower.contains("描述")) {
            return "当预警红灯数达到 3 个及以上时触发该规则，用于提醒项目风险。";
        }
        if (lower.contains("optimize") || lower.contains("优化")) {
            return "1. 使用变量提前缓存复杂表达式；2. 拆分长表达式为多个子规则。";
        }
        return "{}";
    }
}
