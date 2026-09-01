package com.njydsz.agent.infra.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.conversation.Conversation;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.memory.MemoryConsolidationService;
import com.njydsz.agent.domain.memory.MemoryExtractedFact;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;

/**
 * 基于 LLM 的记忆整合服务实现。
 *
 * <p>使用 LLM 从对话中提取有价值的记忆事实，包括：</p>
 * <ul>
 *   <li>用户偏好 — 用户表达的工作习惯、工具偏好</li>
 *   <li>关键决策 — 对话中做出的重要决定</li>
 *   <li>项目上下文 — 项目名称、进度、关键节点</li>
 *   <li>关系信息 — 人物、角色、汇报关系</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的记忆生命周期设计：对话后提取、定时整合、Dreaming 工作流。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Slf4j
@Component
public class LlmMemoryConsolidationService implements MemoryConsolidationService {

    private static final int MIN_CONVERSATION_MESSAGES = 4;
    private static final int MAX_FACTS_PER_EXTRACTION = 10;
    private static final double DEFAULT_IMPORTANCE = 0.5;
    private static final double HIGH_IMPORTANCE = 0.8;

    /** 日志中事实内容的截断长度 */
    private static final int LOG_CONTENT_TRUNCATE_LENGTH = 50;

    /** 正则匹配组：重要度字段索引 */
    private static final int IMPORTANCE_GROUP_INDEX = 3;

    private static final Pattern FACT_PATTERN = Pattern.compile(
            "\\{\\s*\"category\"\\s*:\\s*\"([^\"]+)\"\\s*,"
                    + "\\s*\"content\"\\s*:\\s*\"([^\"]+)\"\\s*,"
                    + "\\s*\"importance\"\\s*:\\s*([0-9.]+)\\s*\\}");

    private static final String EXTRACTION_PROMPT = """
            你是一个专业的记忆分析助手。请从以下对话中提取有价值的记忆事实。

            提取规则：
            1. 只提取对后续对话有复用价值的信息（用户偏好、关键决策、项目上下文、关系信息）
            2. 不要提取临时性、一次性的信息
            3. 每条事实控制在 50 字以内
            4. importance 取值 0.0-1.0，越高表示越重要
            5. 类别从以下选择：preference(偏好)、decision(决策)、project(项目)、relationship(关系)、knowledge(知识)

            输出格式（JSON 数组）：
            [
              {"category": "类别", "content": "事实内容", "importance": 重要度},
              ...
            ]

            如果对话中没有值得记忆的事实，输出：[]

            对话内容：
            """;

    private final LlmClient llmClient;

    public LlmMemoryConsolidationService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public List<MemoryExtractedFact> extractFacts(Conversation conversation, String tenantId) {
        if (conversation == null || !isWorthExtracting(conversation)) {
            return Collections.emptyList();
        }

        try {
            String conversationText = buildConversationText(conversation);
            String prompt = EXTRACTION_PROMPT + "\n" + conversationText;

            ChatMessage systemMsg = ChatMessage.builder()
                    .role(MessageRole.SYSTEM)
                    .content("你是专业的记忆分析助手。严格按照 JSON 格式输出。")
                    .build();
            ChatMessage userMsg = ChatMessage.builder()
                    .role(MessageRole.USER)
                    .content(prompt)
                    .build();

            List<ChatMessage> messages = List.of(systemMsg, userMsg);
            String response = llmClient.chat(messages);

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parseExtractedFacts(response, tenantId, conversation);
        } catch (Exception e) {
            log.warn("记忆提取失败: conversationId={}, error={}",
                    conversation.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isWorthExtracting(Conversation conversation) {
        if (conversation == null || conversation.getMessages() == null) {
            return false;
        }
        return conversation.getMessages().size() >= MIN_CONVERSATION_MESSAGES;
    }

    @Override
    public int persistFacts(List<MemoryExtractedFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return 0;
        }
        // 当前实现仅记录日志，实际持久化需要对接知识库或专用记忆存储
        // TODO: 对接 factStore (MemoryExtractedFactRepository)
        for (MemoryExtractedFact fact : facts) {
            log.info("记忆事实已提取: tenantId={}, category={}, importance={}, content={}",
                    fact.getTenantId(), fact.getCategory(),
                    fact.getImportance(),
                    fact.getContent().length() > LOG_CONTENT_TRUNCATE_LENGTH
                            ? fact.getContent().substring(0, LOG_CONTENT_TRUNCATE_LENGTH) + "..."
                            : fact.getContent());
        }
        return facts.size();
    }

    /**
     * 将对话对象格式化为 LLM 可分析的文本。
     *
     * @param conversation 对话聚合
     * @return 格式化的对话文本
     */
    private String buildConversationText(Conversation conversation) {
        if (conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        conversation.getMessages().forEach(msg -> {
            String role = msg.getRole() != null ? msg.getRole().name() : "unknown";
            String content = msg.getContent() != null ? msg.getContent() : "";
            sb.append(role).append(": ").append(content).append("\n");
        });
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的记忆事实 JSON。
     *
     * @param response LLM 原始响应
     * @param tenantId 租户 ID
     * @param conversation 对话对象
     * @return 解析后的记忆事实列表
     */
    private List<MemoryExtractedFact> parseExtractedFacts(String response, String tenantId,
                                                          Conversation conversation) {
        List<MemoryExtractedFact> facts = new ArrayList<>();
        Matcher matcher = FACT_PATTERN.matcher(response);

        int count = 0;
        while (matcher.find() && count < MAX_FACTS_PER_EXTRACTION) {
            try {
                String category = matcher.group(1);
                String content = matcher.group(2);
                double importance = Double.parseDouble(matcher.group(IMPORTANCE_GROUP_INDEX));

                MemoryExtractedFact fact = MemoryExtractedFact.builder()
                        .factId(UUID.randomUUID().toString())
                        .tenantId(tenantId)
                        .userId(extractUserId(conversation))
                        .conversationId(conversation.getId())
                        .category(category)
                        .content(content)
                        .sourceSummary(buildSourceSummary(conversation))
                        .importance(Math.min(Math.max(importance, 0.0), HIGH_IMPORTANCE))
                        .extractedAt(LocalDateTime.now())
                        .build();

                facts.add(fact);
                count++;
            } catch (NumberFormatException e) {
                log.debug("跳过格式错误的事实解析: {}", matcher.group());
            }
        }

        if (!facts.isEmpty()) {
            log.info("从对话中提取 {} 条记忆事实: conversationId={}",
                    facts.size(), conversation.getId());
        }
        return facts;
    }

    /**
     * 提取对话中的用户 ID。
     */
    private String extractUserId(Conversation conversation) {
        if (conversation.getUserId() != null) {
            return conversation.getUserId();
        }
        if (conversation.getMessages() != null) {
            for (ChatMessage msg : conversation.getMessages()) {
                if (msg.getUserId() != null) {
                    return msg.getUserId();
                }
            }
        }
        return "unknown";
    }

    /**
     * 构建对话摘要作为来源信息。
     */
    private String buildSourceSummary(Conversation conversation) {
        if (conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            return "";
        }
        int size = conversation.getMessages().size();
        return String.format("对话 %s, %d 条消息", conversation.getId(), size);
    }
}
