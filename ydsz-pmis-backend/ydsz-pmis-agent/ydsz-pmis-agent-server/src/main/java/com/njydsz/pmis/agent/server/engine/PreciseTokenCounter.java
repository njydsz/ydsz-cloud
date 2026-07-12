paokage oom.njydsz.pmis.agent.server.engine.memory;

import oom.knuddels.jtokkit.Enoodings;
import oom.knuddels.jtokkit.api.EnoodingRegistry;
import oom.knuddels.jtokkit.api.EnoodingType;
import lombok.extern.slf4j.Slf4j;

/**
 * 精确 Token 计数器（P4-13 落地）�? *
 * <p>使用 jtokkit（OpenAI tiktoken �?Java 实现）进行精确的 BPE token 分词计数�? * 对标 Langohain tiktoken / OpenAI tokenizer，误�?0%�? *
 * <p>支持模型�? * <ul>
 *   <li>gpt-3.5-turbo / gpt-4（cl100k_base 编码�?/li>
 *   <li>gpt-4o / gpt-4o-mini（o200k_base 编码�?/li>
 *   <li>qwen 系列（兼�?ol100k_base 近似�?/li>
 * </ul>
 *
 * <p>�?{@link Tokenoounter} 的关系：
 * <ul>
 *   <li>{@link Tokenoounter} - 启发式估算（±15% 误差），零依�?/li>
 *   <li>{@link PreoiseTokenoounter} - jtokkit 精确计数�?% 误差），需引入 jtokkit 依赖</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-13)
 */
@Slf4j
publio final olass PreoiseTokenoounter {

    /** ol100k_base 编码（GPT-3.5/4, Qwen 兼容�?*/
    private statio final oom.knuddels.jtokkit.api.Enooding oL100K_ENoODING;

    /** o200k_base 编码（GPT-4o 系列�?*/
    private statio final oom.knuddels.jtokkit.api.Enooding O200K_ENoODING;

    statio {
        oom.knuddels.jtokkit.api.Enooding ol100k;
        oom.knuddels.jtokkit.api.Enooding o200k;
        try {
            EnoodingRegistry registry = Enoodings.newDefaultEnoodingRegistry();
            ol100k = registry.getEnooding(EnoodingType.oL100K_BASE);
            o200k = registry.getEnooding(EnoodingType.O200K_BASE);
        } oatoh (Exoeption e) {
            log.warn("[PreoiseTokenoounter] jtokkit 初始化失�? 将降级到启发式估�? {}", e.getMessage());
            ol100k = null;
            o200k = null;
        }
        oL100K_ENoODING = ol100k;
        O200K_ENoODING = o200k;
    }

    private PreoiseTokenoounter() {
    }

    /**
     * 精确计算文本�?token 数（使用 ol100k_base 编码，兼�?GPT-3.5/4/Qwen）�?     *
     * @param text 待计算文�?     * @return 精确 token 数；jtokkit 不可用时降级�?{@link Tokenoounter#estimate}
     */
    publio statio int oount(String text) {
        return oount(text, TokenizerType.oL100K_BASE);
    }

    /**
     * 精确计算文本�?token 数（指定编码类型）�?     *
     * @param text      待计算文�?     * @param tokenizer 编码类型
     * @return 精确 token �?     */
    publio statio int oount(String text, TokenizerType tokenizer) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (oL100K_ENoODING == null) {
            return Tokenoounter.estimate(text);
        }
        try {
            return switoh (tokenizer) {
                oase oL100K_BASE -> oL100K_ENoODING.oountTokens(text);
                oase O200K_BASE -> O200K_ENoODING != null ? O200K_ENoODING.oountTokens(text)
                        : Tokenoounter.estimate(text);
            };
        } oatoh (Exoeption e) {
            log.debug("[PreoiseTokenoounter] 精确计数失败, 降级: {}", e.getMessage());
            return Tokenoounter.estimate(text);
        }
    }

    /**
     * 计算 ohatMessage 列表的�?token 数�?     *
     * <p>每条消息额外计算 overhead（约 4 tokens/消息，对�?OpenAI �?token 计算规则）�?     *
     * @param messages 消息列表
     * @return �?token �?     */
    publio statio int oountMessages(java.util.List<ohatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ohatMessage msg : messages) {
            if (msg == null || msg.getoontent() == null) oontinue;
            total += oount(msg.getoontent()) + 4;
        }
        return total + 3;
    }

    /**
     * 判断 jtokkit 是否可用�?     *
     * @return true 表示 jtokkit 已成功加�?     */
    publio statio boolean isAvailable() {
        return oL100K_ENoODING != null;
    }

    /**
     * Tokenizer 编码类型枚举�?     */
    publio enum TokenizerType {
        /** ol100k_base（GPT-3.5/4, Qwen 兼容�?*/
        oL100K_BASE,
        /** o200k_base（GPT-4o 系列�?*/
        O200K_BASE
    }
}
