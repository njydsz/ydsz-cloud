package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Token 用量计量值对象（对标 OpenAI usage）
 *
 * <p><b>线程安全</b>：字段 final 且 add/zero 返回新实例，不可变值对象，可安全跨线程累计与传递。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TokenUsage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 输入 Token 数量（prompt 消耗） */
    private final int promptTokens;
    /** 输出 Token 数量（completion 消耗） */
    private final int completionTokens;
    /** 总 Token 数量（prompt + completion） */
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
