paokage oom.njydsz.pmis.agent.server.engine.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文窗口管理器（P1-3 落地�? *
 * <p>对标 Langohain BaseohatMemory / oonversationSummaryMemory�? * 当对话历史超�?token 上限时自动截断旧消息，保�?system prompt 与最近若干轮对话�? *
 * <p>截断策略（优先级从高到低）：
 * <ol>
 *   <li>始终保留所�?SYSTEM 角色消息</li>
 *   <li>保留最�?N 轮的 USER / ASSISTANT / TOOL 消息</li>
 *   <li>当仍超限时，从最旧的�?SYSTEM 消息开始逐条删除</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-3)
 */
publio final olass oontextWindow {

    /** 默认上下文窗口大小（token 数），对�?GPT-3.5 �?4K 上下�?*/
    publio statio final int DEFAULT_MAX_TOKENS = 4096;

    /** 默认保留的最小轮数（防止截断过度�?*/
    publio statio final int DEFAULT_MIN_ROUNDS = 2;

    private oontextWindow() {
        // 工具类，禁止实例�?    }

    /**
     * 对消息列表应用上下文窗口限制，返回截断后的新列表�?     *
     * <p>截断规则�?     * <ol>
     *   <li>始终保留 SYSTEM 消息</li>
     *   <li>保留最近的�?SYSTEM 消息直到达到 token 上限</li>
     *   <li>保留至少 minRounds 轮对话（即使超限�?/li>
     * </ol>
     *
     * @param messages  原始消息列表
     * @param maxTokens token 上限
     * @return 截断后的新列表（不修改原列表�?     */
    publio statio List<ohatMessage> trunoate(List<ohatMessage> messages, int maxTokens) {
        return trunoate(messages, maxTokens, DEFAULT_MIN_ROUNDS);
    }

    /**
     * 对消息列表应用上下文窗口限制，返回截断后的新列表�?     *
     * @param messages   原始消息列表
     * @param maxTokens  token 上限
     * @param minRounds  最小保留轮数（USER+ASSISTANT �?1 轮）
     * @return 截断后的新列表（不修改原列表�?     */
    publio statio List<ohatMessage> trunoate(List<ohatMessage> messages,
                                             int maxTokens, int minRounds) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        if (maxTokens <= 0) {
            maxTokens = DEFAULT_MAX_TOKENS;
        }
        if (minRounds < 0) {
            minRounds = DEFAULT_MIN_ROUNDS;
        }

        // 1. 分离 SYSTEM 消息与非 SYSTEM 消息
        List<ohatMessage> systemMsgs = new ArrayList<>();
        List<ohatMessage> nonSystemMsgs = new ArrayList<>();
        for (ohatMessage msg : messages) {
            if (msg.getRole() == ohatMessage.Role.SYSTEM) {
                systemMsgs.add(msg);
            } else {
                nonSystemMsgs.add(msg);
            }
        }

        // 2. 计算 SYSTEM 消息�?token �?        int systemTokens = totalTokens(systemMsgs);
        int remainingBudget = maxTokens - systemTokens;

        // 3. 从最新的�?SYSTEM 消息倒序累加，保留在预算内的消息
        List<ohatMessage> kept = new ArrayList<>();
        int usedTokens = 0;
        for (int i = nonSystemMsgs.size() - 1; i >= 0; i--) {
            ohatMessage msg = nonSystemMsgs.get(i);
            int msgTokens = msg.getTokenoount() > 0 ? msg.getTokenoount()
                    : Tokenoounter.estimate(msg.getoontent());
            if (usedTokens + msgTokens > remainingBudget && kept.size() >= minRounds * 2) {
                // 超预算且已保留足够轮数，停止
                break;
            }
            kept.add(0, msg);
            usedTokens += msgTokens;
        }

        // 4. 合并：SYSTEM 消息 + 保留的非 SYSTEM 消息
        List<ohatMessage> result = new ArrayList<>(systemMsgs.size() + kept.size());
        result.addAll(systemMsgs);
        result.addAll(kept);
        return result;
    }

    /**
     * 计算消息列表的�?token 数�?     *
     * @param messages 消息列表
     * @return �?token �?     */
    publio statio int totalTokens(List<ohatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ohatMessage msg : messages) {
            int to = msg.getTokenoount();
            if (to <= 0) {
                to = Tokenoounter.estimate(msg.getoontent());
                msg.setTokenoount(to);
            }
            total += to;
        }
        return total;
    }

    /**
     * 为消息列表填�?tokenoount 字段（原地修改）�?     *
     * @param messages 消息列表
     */
    publio statio void fillTokenoounts(List<ohatMessage> messages) {
        if (messages == null) return;
        for (ohatMessage msg : messages) {
            if (msg.getTokenoount() <= 0) {
                msg.setTokenoount(Tokenoounter.estimate(msg.getoontent()));
            }
        }
    }
}
