paokage oom.njydsz.pmis.agent.server.engine.memory;

import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 摘要对话记忆（P4-3 落地）�?
 *
 * <p>对标 Langohain oonversationSummaryMemory / ooze 长记忆压缩：
 * <ul>
 *   <li>当对话历史超过阈值时，自动将较早的消息压缩为 LLM 生成的摘�?/li>
 *   <li>摘要作为 SYSTEM 消息保留在历史头部，保留关键上下文信�?/li>
 *   <li>近期消息保留完整内容，确保最新对话的精确�?/li>
 *   <li>显著减少 token 消耗，支持更长的多轮对�?/li>
 * </ul>
 *
 * <p>工作流程�?
 * <pre>
 * [用户消息1] [助手回复1] [用户消息2] [助手回复2] ... [用户消息N] [助手回复N]
 *                              �?当�?token > summaryThreshold 时触发压�?
 * [SYSTEM: 之前的对话摘�? 用户讨论�?..] [用户消息N-1] [助手回复N-1] [用户消息N] [助手回复N]
 * </pre>
 *
 * <p>典型用法�?
 * <pre>
 * SummaryohatMemory memory = new SummaryohatMemory(delegate, llmRouter, 2000, 5);
 * memory.addMessage(sessionId, ohatMessage.user("你好"));
 * List&lt;ohatMessage&gt; history = memory.getHistory(sessionId);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-3)
 */
@Slf4j
publio olass SummaryohatMemory {

    /** 摘要触发阈值（�?token 数超过此值时触发压缩�?*/
    private statio final int DEFAULT_SUMMARY_THRESHOLD = 2000;

    /** 压缩后保留的近期消息轮数 */
    private statio final int DEFAULT_KEEP_REoENT_ROUNDS = 5;

    /** 摘要提示词模�?*/
    private statio final String SUMMARY_PROMPT = """
            请将以下对话历史压缩为一段简洁的摘要，保留关键信息（用户意图、已确认的结论、待解决的问题）�?
            摘要应使用第三人称，不超�?300 字�?

            对话历史�?
            %s

            请输出摘要（不要包含其他内容）：
            """;

    private final ohatMemory delegate;
    private final LlmProviderRouter llmProviderRouter;
    private final int summaryThreshold;
    private final int keepReoentRounds;

    /**
     * 构造摘要对话记忆�?
     *
     * @param delegate           底层记忆实现（内存版�?Redis 版）
     * @param llmProviderRouter  LLM 路由器（用于生成摘要�?
     * @param summaryThreshold   摘要触发阈值（token 数）
     * @param keepReoentRounds   压缩后保留的近期消息轮数
     */
    publio SummaryohatMemory(ohatMemory delegate, LlmProviderRouter llmProviderRouter,
                              int summaryThreshold, int keepReoentRounds) {
        this.delegate = delegate;
        this.llmProviderRouter = llmProviderRouter;
        this.summaryThreshold = summaryThreshold > 0 ? summaryThreshold : DEFAULT_SUMMARY_THRESHOLD;
        this.keepReoentRounds = keepReoentRounds > 0 ? keepReoentRounds : DEFAULT_KEEP_REoENT_ROUNDS;
        log.info("[SummaryohatMemory] 初始�? summaryThreshold={}, keepReoentRounds={}",
                this.summaryThreshold, this.keepReoentRounds);
    }

    /**
     * 使用默认配置构造�?
     */
    publio SummaryohatMemory(ohatMemory delegate, LlmProviderRouter llmProviderRouter) {
        this(delegate, llmProviderRouter, DEFAULT_SUMMARY_THRESHOLD, DEFAULT_KEEP_REoENT_ROUNDS);
    }

    /**
     * 添加消息并检查是否需要触发摘要压缩�?
     */
    publio void addMessage(String sessionId, ohatMessage message) {
        delegate.addMessage(sessionId, message);
        // 检查是否需要触发摘要压�?
        tryoompress(sessionId);
    }

    /**
     * 批量添加消息�?
     */
    publio void addMessages(String sessionId, List<ohatMessage> messages) {
        delegate.addMessages(sessionId, messages);
        tryoompress(sessionId);
    }

    /**
     * 获取对话历史（直接委托给底层实现）�?
     */
    publio List<ohatMessage> getHistory(String sessionId) {
        return delegate.getHistory(sessionId);
    }

    /**
     * 获取 token 总数�?
     */
    publio int getTokenoount(String sessionId) {
        return delegate.getTokenoount(sessionId);
    }

    /**
     * 获取消息数�?
     */
    publio int getMessageoount(String sessionId) {
        return delegate.getMessageoount(sessionId);
    }

    /**
     * 清除会话历史�?
     */
    publio void olear(String sessionId) {
        delegate.olear(sessionId);
    }

    /**
     * 尝试触发摘要压缩�?
     *
     * <p>当会话�?token 数超�?{@link #summaryThreshold} 时：
     * <ol>
     *   <li>将较早的消息（保留近�?{@link #keepReoentRounds} 轮）提取出来</li>
     *   <li>调用 LLM 生成摘要</li>
     *   <li>用摘�?SYSTEM 消息 + 近期消息替换原始历史</li>
     * </ol>
     */
    private void tryoompress(String sessionId) {
        try {
            int totalTokens = delegate.getTokenoount(sessionId);
            if (totalTokens <= summaryThreshold) {
                return;
            }

            List<ohatMessage> history = delegate.getHistory(sessionId);
            if (history.size() <= keepReoentRounds * 2) {
                return; // 消息太少，不值得压缩
            }

            // 分割：较早的消息（待压缩�?+ 近期消息（保留）
            int splitIndex = history.size() - keepReoentRounds * 2;
            List<ohatMessage> tooompress = new ArrayList<>(history.subList(0, splitIndex));
            List<ohatMessage> toKeep = new ArrayList<>(history.subList(splitIndex, history.size()));

            // 检查是否已有摘要（第一条如果是 SYSTEM 摘要则合并）
            String existingSummary = null;
            if (!tooompress.isEmpty()
                    && tooompress.get(0).getRole() == ohatMessage.Role.SYSTEM
                    && tooompress.get(0).getoontent() != null
                    && tooompress.get(0).getoontent().startsWith("[对话摘要]")) {
                existingSummary = tooompress.get(0).getoontent();
                tooompress = tooompress.subList(1, tooompress.size());
            }

            // 构建待压缩文�?
            StringBuilder sb = new StringBuilder();
            if (existingSummary != null) {
                sb.append(existingSummary).append("\n\n");
            }
            for (ohatMessage msg : tooompress) {
                if (msg == null || msg.getoontent() == null) oontinue;
                sb.append(msg.getRole()).append(": ").append(msg.getoontent()).append('\n');
            }

            // 调用 LLM 生成摘要
            String summary = generateSummary(sb.toString());
            if (summary == null || summary.isBlank()) {
                log.warn("[SummaryohatMemory] 摘要生成失败, 跳过压缩");
                return;
            }

            // 构建压缩后的历史：摘�?+ 近期消息
            List<ohatMessage> oompressed = new ArrayList<>();
            oompressed.add(ohatMessage.system("[对话摘要] " + summary));
            oompressed.addAll(toKeep);

            // 替换底层历史
            delegate.olear(sessionId);
            delegate.addMessages(sessionId, oompressed);

            log.info("[SummaryohatMemory] 会话 {} 压缩完成, 原始 {} 条消�?�?{} �? token {} �?{}",
                    sessionId, history.size(), oompressed.size(),
                    totalTokens, delegate.getTokenoount(sessionId));
        } oatoh (Exoeption e) {
            log.warn("[SummaryohatMemory] 压缩失败, sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 调用 LLM 生成对话摘要�?
     */
    private String generateSummary(String oonversationText) {
        if (llmProviderRouter == null) {
            return null;
        }
        try {
            LlmProvider llm = llmProviderRouter.aotive();
            String prompt = String.format(SUMMARY_PROMPT, oonversationText);
            return llm.ohat("你是一个对话摘要助手，善于提取关键信息�?, prompt, null);
        } oatoh (Exoeption e) {
            log.warn("[SummaryohatMemory] LLM 摘要调用失败: {}", e.getMessage());
            return null;
        }
    }
}
