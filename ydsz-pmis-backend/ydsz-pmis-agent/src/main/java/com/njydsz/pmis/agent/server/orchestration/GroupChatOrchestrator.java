package com.njydsz.pmis.agent.server.orchestration;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.server.engine.react.ReActLoop;
import com.njydsz.pmis.agent.server.engine.react.ReActResult;
import com.njydsz.pmis.agent.server.engine.stream.NoOpReActEventListener;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiFunction;

/**
 * 多 Agent 对话式协作编排器（P2-9 落地）。
 *
 * <p>对标 Coze 多 Agent 模式 / AutoGen GroupChat / LangGraph Multi-Agent：
 * <ul>
 *   <li><b>轮转模式</b>：多个 Agent 按顺序轮流发言，各自基于前序发言补充信息</li>
 *   <li><b>主持人模式</b>：一个 LLM 作为主持人，决定下一个发言的 Agent</li>
 *   <li><b>广播模式</b>：所有 Agent 同时处理同一问题，结果由主持人汇总</li>
 * </ul>
 *
 * <p>使用 {@link ChatParticipant} 函数式接口定义参与者，不依赖具体 Agent 实现，
 * 可灵活包装 ReActLoop、自定义 Agent、或外部 API。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0 (P2-9)
 */
@Slf4j
@Component
public class GroupChatOrchestrator {

    /** 默认最大对话轮数 */
    public static final int DEFAULT_MAX_ROUNDS = 6;

    private final LlmProviderRouter llmProviderRouter;
    private final ReActLoop reactLoop;

    public GroupChatOrchestrator(LlmProviderRouter llmProviderRouter, ReActLoop reactLoop) {
        this.llmProviderRouter = llmProviderRouter;
        this.reactLoop = reactLoop;
    }

    /** 主持人系统提示词 */
    private static final String MODERATOR_SYSTEM_PROMPT = """
            你是一个多 Agent 协作的主持人。根据当前讨论内容，决定下一个应该发言的 Agent。

            可用的 Agent 列表：
            %s

            当前讨论历史：
            %s

            用户问题：%s

            请输出下一个应该发言的 Agent 名称。
            如果讨论已经充分，可以回答用户问题，请输出 "FINISH"。
            请只输出 Agent 名称或 "FINISH"，不要输出其他内容。""";

    /**
     * 轮转模式：Agent 按顺序轮流发言。
     *
     * @param participants 参与者列表（按发言顺序）
     * @param userPrompt   用户问题
     * @param ctx          Agent 上下文
     * @return 协作结果（包含完整对话历史）
     */
    public GroupChatResult roundRobin(List<ChatParticipant> participants, String userPrompt,
                                       AgentContext ctx) {
        return roundRobin(participants, userPrompt, ctx, DEFAULT_MAX_ROUNDS);
    }

    /**
     * 轮转模式（指定最大轮数）。
     */
    public GroupChatResult roundRobin(List<ChatParticipant> participants, String userPrompt,
                                       AgentContext ctx, int maxRounds) {
        if (participants == null || participants.isEmpty()) {
            return GroupChatResult.failure("参与 Agent 列表为空");
        }
        int rounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;

        log.info("[GroupChat] 轮转模式开始: participants={}, rounds={}",
                participants.stream().map(ChatParticipant::getName).toList(), rounds);

        StringBuilder conversation = new StringBuilder();
        conversation.append("[用户问题]\n").append(userPrompt).append("\n\n");

        for (int round = 0; round < rounds; round++) {
            ChatParticipant current = participants.get(round % participants.size());
            log.info("[GroupChat] round={}, agent={}", round + 1, current.getName());

            try {
                String response = current.getResponseFn().apply(conversation.toString(), ctx);
                conversation.append("[").append(current.getName()).append(" 发言]\n")
                        .append(response).append("\n\n");
                log.info("[GroupChat] round={} agent={} 完成", round + 1, current.getName());
            } catch (Exception e) {
                log.error("[GroupChat] round={} agent={} 异常: {}",
                        round + 1, current.getName(), e.getMessage(), e);
                conversation.append("[").append(current.getName()).append(" 异常]\n")
                        .append(e.getMessage()).append("\n\n");
            }
        }

        return GroupChatResult.success(conversation.toString());
    }

    /**
     * 主持人模式：LLM 主持人决定下一个发言的 Agent。
     */
    public GroupChatResult moderatedChat(List<ChatParticipant> participants, String userPrompt,
                                          AgentContext ctx) {
        return moderatedChat(participants, userPrompt, ctx, DEFAULT_MAX_ROUNDS);
    }

    /**
     * 主持人模式（指定最大轮数）。
     */
    public GroupChatResult moderatedChat(List<ChatParticipant> participants, String userPrompt,
                                          AgentContext ctx, int maxRounds) {
        if (participants == null || participants.isEmpty()) {
            return GroupChatResult.failure("参与 Agent 列表为空");
        }
        int rounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;

        Map<String, ChatParticipant> participantMap = new LinkedHashMap<>();
        for (ChatParticipant p : participants) {
            participantMap.put(p.getName(), p);
        }

        log.info("[GroupChat] 主持人模式开始: participants={}, rounds={}",
                participantMap.keySet(), rounds);

        StringBuilder conversation = new StringBuilder();
        conversation.append("[用户问题]\n").append(userPrompt).append("\n\n");

        for (int round = 0; round < rounds; round++) {
            String nextAgent = askModerator(participantMap.keySet(), conversation.toString(),
                    userPrompt, ctx);

            if ("FINISH".equalsIgnoreCase(nextAgent)) {
                log.info("[GroupChat] 主持人判断讨论已完成, round={}", round + 1);
                break;
            }

            ChatParticipant participant = participantMap.get(nextAgent);
            if (participant == null) {
                log.warn("[GroupChat] 主持人指定了未知的 Agent: {}, 使用第一个", nextAgent);
                participant = participants.get(0);
            }

            log.info("[GroupChat] round={}, 主持人选择: {}", round + 1, participant.getName());

            try {
                String response = participant.getResponseFn().apply(conversation.toString(), ctx);
                conversation.append("[").append(participant.getName()).append(" 发言]\n")
                        .append(response).append("\n\n");
            } catch (Exception e) {
                log.error("[GroupChat] Agent 异常: {}", e.getMessage(), e);
                conversation.append("[").append(participant.getName()).append(" 异常]\n")
                        .append(e.getMessage()).append("\n\n");
            }
        }

        return GroupChatResult.success(conversation.toString());
    }

    /**
     * 广播模式：所有 Agent 同时处理，结果汇总。
     */
    public GroupChatResult broadcast(List<ChatParticipant> participants, String userPrompt,
                                      AgentContext ctx) {
        if (participants == null || participants.isEmpty()) {
            return GroupChatResult.failure("参与 Agent 列表为空");
        }

        log.info("[GroupChat] 广播模式开始: participants={}", participants.size());

        StringBuilder summary = new StringBuilder();
        summary.append("[多 Agent 广播结果汇总]\n\n");

        for (ChatParticipant participant : participants) {
            try {
                String response = participant.getResponseFn().apply(userPrompt, ctx);
                summary.append("=== ").append(participant.getName()).append(" ===\n")
                        .append(response).append("\n\n");
                log.info("[GroupChat] 广播: agent={} 完成", participant.getName());
            } catch (Exception e) {
                log.error("[GroupChat] 广播: agent={} 异常: {}", participant.getName(), e.getMessage());
                summary.append("=== ").append(participant.getName()).append(" ===\n")
                        .append("[异常] ").append(e.getMessage()).append("\n\n");
            }
        }

        return GroupChatResult.success(summary.toString());
    }

    // ==================== 便捷工厂方法 ====================

    /**
     * 创建基于 ReActLoop 的参与者。
     *
     * @param name           参与者名称
     * @param systemPrompt   系统提示词
     * @return 参与者实例
     */
    public ChatParticipant createReactParticipant(String name, String systemPrompt) {
        return ChatParticipant.builder()
                .name(name)
                .responseFn((prompt, ctx) -> {
                    ReActResult result = reactLoop.runStream(
                            systemPrompt, prompt, ctx, ReActLoop.DEFAULT_MAX_STEPS,
                            NoOpReActEventListener.getInstance());
                    if (result.isSuccess()) {
                        return result.getFinalAnswer();
                    } else {
                        return "[执行失败] " + result.getFailureReason();
                    }
                })
                .build();
    }

    // ==================== 内部方法 ====================

    /**
     * 调用 LLM 主持人决定下一个发言者。
     */
    private String askModerator(Set<String> agentNames, String conversation,
                                 String userPrompt, AgentContext ctx) {
        try {
            LlmProvider llm = llmProviderRouter.active();
            String systemPrompt = String.format(MODERATOR_SYSTEM_PROMPT,
                    String.join(", ", agentNames),
                    conversation,
                    userPrompt);
            String response = llm.chat(systemPrompt,
                    "请输出下一个发言的 Agent 名称或 FINISH。", ctx);
            if (response == null || response.isBlank()) {
                return agentNames.iterator().next();
            }
            return response.strip();
        } catch (Exception e) {
            log.warn("[GroupChat] 主持人决策失败, 使用第一个 Agent: {}", e.getMessage());
            return agentNames.iterator().next();
        }
    }

    // ==================== 内部类 ====================

    /**
     * 对话参与者定义。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatParticipant {
        /** 参与者名称 */
        private String name;
        /** 响应函数：(输入 prompt, 上下文) → 输出文本 */
        private BiFunction<String, AgentContext, String> responseFn;
    }

    /**
     * 多 Agent 协作结果。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupChatResult {
        /** 是否成功 */
        private boolean success;
        /** 完整对话历史 */
        private String conversation;
        /** 错误信息（失败时） */
        private String error;

        public static GroupChatResult success(String conversation) {
            return GroupChatResult.builder()
                    .success(true)
                    .conversation(conversation)
                    .build();
        }

        public static GroupChatResult failure(String error) {
            return GroupChatResult.builder()
                    .success(false)
                    .error(error)
                    .build();
        }
    }
}
