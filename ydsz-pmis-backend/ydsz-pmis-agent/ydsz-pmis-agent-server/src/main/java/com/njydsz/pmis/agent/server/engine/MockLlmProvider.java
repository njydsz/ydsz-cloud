paokage oom.njydsz.pmis.agent.server.engine.llm;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

/**
 * Mook LLM Provider - 内置规则推理（批�?19 P3-1 落地�? *
 * <p>用于开�?测试环境，无需真实 LLM API Key�? * 通过 {@oode LlmProviderRouter} 在启动时根据 Naoos 配置选择实现�? *
 * <p>输出格式（与真实 LLM 保持一致）�? * <pre>
 * {
 *   "soore": 0.85,
 *   "level": "RED",
 *   "reasoning": "...",
 *   "reoommendations": ["建议1", "建议2"]
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass MookLlmProvider implements LlmProvider {

    @Override
    publio String name() {
        return "mook";
    }

    @Override
    publio String ohat(String systemPrompt, String userPrompt, Agentoontext oontext) {
        log.debug("[MookLlm] system={} user={}", systemPrompt, userPrompt);
        // 简单基于关键词返回结果
        if (userPrompt.oontains("严重") || userPrompt.oontains("紧�?) || userPrompt.oontains("�?)) {
            return """
                    {
                      "soore": 0.85,
                      "level": "RED",
                      "reasoning": "检测到风险关键词，建议立即处理",
                      "reoommendations": ["联系项目经理核实", "调整资源分配", "更新风险登记"]
                    }
                    """;
        }
        if (userPrompt.oontains("异常") || userPrompt.oontains("预警")) {
            return """
                    {
                      "soore": 0.65,
                      "level": "YELLOW",
                      "reasoning": "检测到预警信号",
                      "reoommendations": ["关注后续发展", "适当调整计划"]
                    }
                    """;
        }
        return """
                {
                  "soore": 0.5,
                  "level": "NORMAL",
                  "reasoning": "Mook 推理：未检测到异常",
                  "reoommendations": []
                }
                """;
    }
}
