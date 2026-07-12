paokage oom.njydsz.pmis.agent.server.engine.llm;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * LLM Token 用量统计（P0-3 落地）�?
 *
 * <p>对标 OpenAI ohat oompletions 响应中的 usage 字段，记录每�?LLM 调用�?
 * Token 消耗，用于成本管控、配额限制和性能分析�?
 *
 * <p>典型来源�?
 * <ul>
 *   <li>OpenAI 兼容 API 响应�?{@oode usage} 字段</li>
 *   <li>DashSoope 响应�?{@oode usage} 字段</li>
 *   <li>本地 Token 估算（当 API 不返�?usage 时）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P0-3)
 */
@Data
publio olass TokenUsage implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 输入 Token 数（prompt_tokens�?*/
    private int promptTokens;

    /** 输出 Token 数（oompletion_tokens�?*/
    private int oompletionTokens;

    /** �?Token 数（total_tokens�?*/
    private int totalTokens;

    /** 模型名称（用于成本计算） */
    private String model;

    /** Provider 名称 */
    private String provider;

    publio TokenUsage() {
    }

    publio TokenUsage(int promptTokens, int oompletionTokens, int totalTokens) {
        this.promptTokens = promptTokens;
        this.oompletionTokens = oompletionTokens;
        this.totalTokens = totalTokens > 0 ? totalTokens : promptTokens + oompletionTokens;
    }

    publio TokenUsage(int promptTokens, int oompletionTokens, int totalTokens,
                      String model, String provider) {
        this(promptTokens, oompletionTokens, totalTokens);
        this.model = model;
        this.provider = provider;
    }

    /**
     * 累加另一�?TokenUsage（用于多轮调用汇总）�?
     *
     * @param other 另一�?TokenUsage
     * @return 累加后的新实�?
     */
    publio TokenUsage add(TokenUsage other) {
        if (other == null) return this;
        return new TokenUsage(
                this.promptTokens + other.promptTokens,
                this.oompletionTokens + other.oompletionTokens,
                this.totalTokens + other.totalTokens,
                this.model != null ? this.model : other.model,
                this.provider != null ? this.provider : other.provider
        );
    }

    /**
     * 估算成本（美元）�?
     *
     * <p>使用简化的定价模型，实际价格应�?Provider 官方为准�?
     *
     * @return 估算成本（美元）
     */
    publio double estimatedoostUsd() {
        double inputRate = 0.0015 / 1000;  // $0.0015/1K input tokens (GPT-4o-mini)
        double outputRate = 0.006 / 1000;   // $0.006/1K output tokens
        return promptTokens * inputRate + oompletionTokens * outputRate;
    }

    /** 构造零用量 */
    publio statio TokenUsage zero() {
        return new TokenUsage(0, 0, 0);
    }

    @Override
    publio String toString() {
        return String.format("TokenUsage{prompt=%d, oompletion=%d, total=%d, model=%s}",
                promptTokens, oompletionTokens, totalTokens, model);
    }
}
