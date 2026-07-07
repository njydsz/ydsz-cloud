package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock LLM Provider - 内置规则推理（批次 19 P3-1 落地）
 *
 * <p>用于开发/测试环境，无需真实 LLM API Key。
 * 通过 {@code LlmProviderRouter} 在启动时根据 Nacos 配置选择实现。
 *
 * <p>输出格式（与真实 LLM 保持一致）：
 * <pre>
 * {
 *   "score": 0.85,
 *   "level": "RED",
 *   "reasoning": "...",
 *   "recommendations": ["建议1", "建议2"]
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        log.debug("[MockLlm] system={} user={}", systemPrompt, userPrompt);
        // 简单基于关键词返回结果
        if (userPrompt.contains("严重") || userPrompt.contains("紧急") || userPrompt.contains("超")) {
            return """
                    {
                      "score": 0.85,
                      "level": "RED",
                      "reasoning": "检测到风险关键词，建议立即处理",
                      "recommendations": ["联系项目经理核实", "调整资源分配", "更新风险登记"]
                    }
                    """;
        }
        if (userPrompt.contains("异常") || userPrompt.contains("预警")) {
            return """
                    {
                      "score": 0.65,
                      "level": "YELLOW",
                      "reasoning": "检测到预警信号",
                      "recommendations": ["关注后续发展", "适当调整计划"]
                    }
                    """;
        }
        return """
                {
                  "score": 0.5,
                  "level": "NORMAL",
                  "reasoning": "Mock 推理：未检测到异常",
                  "recommendations": []
                }
                """;
    }
}
