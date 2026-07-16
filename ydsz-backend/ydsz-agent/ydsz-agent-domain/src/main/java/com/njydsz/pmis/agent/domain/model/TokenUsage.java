package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Token 用量计量值对象（对标 OpenAI usage）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TokenUsage implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public TokenUsage(int promptTokens, int completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
    }

    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }

    public TokenUsage add(TokenUsage other) {
        Objects.requireNonNull(other, "other 不能为 null");
        return new TokenUsage(
                this.promptTokens + other.promptTokens,
                this.completionTokens + other.completionTokens);
    }

    public static TokenUsage zero() {
        return new TokenUsage(0, 0);
    }

    @Override
    public String toString() {
        return "TokenUsage{prompt=" + promptTokens + ", completion=" + completionTokens + ", total=" + totalTokens + "}";
    }
}
