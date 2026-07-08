package com.njydsz.pmis.agent.engine.memory;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;

/**
 * 精确 Token 计数器（P4-13 落地）。
 *
 * <p>使用 jtokkit（OpenAI tiktoken 的 Java 实现）进行精确的 BPE token 分词计数，
 * 对标 LangChain tiktoken / OpenAI tokenizer，误差 0%。
 *
 * <p>支持模型：
 * <ul>
 *   <li>gpt-3.5-turbo / gpt-4（cl100k_base 编码）</li>
 *   <li>gpt-4o / gpt-4o-mini（o200k_base 编码）</li>
 *   <li>qwen 系列（兼容 cl100k_base 近似）</li>
 * </ul>
 *
 * <p>与 {@link TokenCounter} 的关系：
 * <ul>
 *   <li>{@link TokenCounter} - 启发式估算（±15% 误差），零依赖</li>
 *   <li>{@link PreciseTokenCounter} - jtokkit 精确计数（0% 误差），需引入 jtokkit 依赖</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-13)
 */
@Slf4j
public final class PreciseTokenCounter {

    /** cl100k_base 编码（GPT-3.5/4, Qwen 兼容） */
    private static final com.knuddels.jtokkit.api.Encoding CL100K_ENCODING;

    /** o200k_base 编码（GPT-4o 系列） */
    private static final com.knuddels.jtokkit.api.Encoding O200K_ENCODING;

    static {
        EncodingRegistry registry;
        com.knuddels.jtokkit.api.Encoding cl100k;
        com.knuddels.jtokkit.api.Encoding o200k;
        try {
            registry = Encodings.newDefaultEncodingRegistry();
            cl100k = registry.getEncoding(EncodingType.CL100K_BASE);
            o200k = registry.getEncoding(EncodingType.O200K_BASE);
        } catch (Exception e) {
            log.warn("[PreciseTokenCounter] jtokkit 初始化失败, 将降级到启发式估算: {}", e.getMessage());
            registry = null;
            cl100k = null;
            o200k = null;
        }
        CL100K_ENCODING = cl100k;
        O200K_ENCODING = o200k;
    }

    private PreciseTokenCounter() {
    }

    /**
     * 精确计算文本的 token 数（使用 cl100k_base 编码，兼容 GPT-3.5/4/Qwen）。
     *
     * @param text 待计算文本
     * @return 精确 token 数；jtokkit 不可用时降级为 {@link TokenCounter#estimate}
     */
    public static int count(String text) {
        return count(text, TokenizerType.CL100K_BASE);
    }

    /**
     * 精确计算文本的 token 数（指定编码类型）。
     *
     * @param text      待计算文本
     * @param tokenizer 编码类型
     * @return 精确 token 数
     */
    public static int count(String text, TokenizerType tokenizer) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (CL100K_ENCODING == null) {
            return TokenCounter.estimate(text);
        }
        try {
            return switch (tokenizer) {
                case CL100K_BASE -> CL100K_ENCODING.countTokens(text);
                case O200K_BASE -> O200K_ENCODING != null ? O200K_ENCODING.countTokens(text)
                        : TokenCounter.estimate(text);
            };
        } catch (Exception e) {
            log.debug("[PreciseTokenCounter] 精确计数失败, 降级: {}", e.getMessage());
            return TokenCounter.estimate(text);
        }
    }

    /**
     * 计算 ChatMessage 列表的总 token 数。
     *
     * <p>每条消息额外计算 overhead（约 4 tokens/消息，对齐 OpenAI 的 token 计算规则）。
     *
     * @param messages 消息列表
     * @return 总 token 数
     */
    public static int countMessages(java.util.List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage msg : messages) {
            if (msg == null || msg.getContent() == null) continue;
            total += count(msg.getContent()) + 4;
        }
        return total + 3;
    }

    /**
     * 判断 jtokkit 是否可用。
     *
     * @return true 表示 jtokkit 已成功加载
     */
    public static boolean isAvailable() {
        return CL100K_ENCODING != null;
    }

    /**
     * Tokenizer 编码类型枚举。
     */
    public enum TokenizerType {
        /** cl100k_base（GPT-3.5/4, Qwen 兼容） */
        CL100K_BASE,
        /** o200k_base（GPT-4o 系列） */
        O200K_BASE
    }
}
