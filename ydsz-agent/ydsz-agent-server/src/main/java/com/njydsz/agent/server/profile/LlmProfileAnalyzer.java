package com.njydsz.agent.server.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.memory.MemoryExtractedFact;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * 基于 LLM 的用户画像分析器。
 *
 * <p>使用大语言模型从对话消息和记忆事实中分析用户的查询风格、偏好领域和意图模式。
 * 通过配置开关 {@code ydsz.agent.profile.llm-analysis-enabled} 控制是否使用 LLM 分析，
 * 未开启时使用基于规则的简化分析。</p>
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Slf4j
public class LlmProfileAnalyzer {

    /** 集合初始容量 */
    private static final int COLLECTION_CAPACITY = 16;

    /** 最大分析消息数（超出截断，避免 Token 超限） */
    private static final int MAX_ANALYZE_MESSAGES = 20;

    /** 分析规则提取的领域关键词 JSON 匹配模式 */
    private static final Pattern DOMAINS_PATTERN = Pattern.compile(
            "\"domains\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);

    /** 分析规则提取的风格匹配模式 */
    private static final Pattern STYLE_PATTERN = Pattern.compile(
            "\"queryStyle\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);

    private static final String STYLE_ANALYSIS_PROMPT = """
            你是一个用户行为分析助手。请根据以下对话记录，分析用户的查询风格。
            
            风格分类：
            - 简洁：偏好简短精炼的回答
            - 详细：偏好完整详尽的解释
            - 分析型：关注数据分析和逻辑推演
            - 探索型：喜欢发散思维和多角度探讨
            - 实用型：关注具体操作和实施步骤

            输出 JSON 格式：{"queryStyle": "风格描述", "reason": "简要理由"}

            对话记录：
            """;

    private static final String FACT_ANALYSIS_PROMPT = """
            你是一个用户行为分析助手。请从以下记忆事实中识别用户最常关注的领域。
            
            输出 JSON 格式：
            {"domains": ["领域1", "领域2", ...], "summary": "简要总结"}

            事实列表：
            """;

    private final LlmClient llmClient;
    private final String modelName;

    public LlmProfileAnalyzer(LlmClient llmClient, String modelName) {
        this.llmClient = llmClient;
        this.modelName = modelName;
    }

    /**
     * 从近期对话消息中分析用户的查询风格。
     *
     * @param recentMessages 近期对话消息列表
     * @return 用户查询风格描述
     */
    public String analyzeStyle(List<String> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }

        try {
            List<String> truncated = recentMessages.size() > MAX_ANALYZE_MESSAGES
                    ? recentMessages.subList(0, MAX_ANALYZE_MESSAGES)
                    : recentMessages;
            String conversationText = String.join("\n", truncated);
            String prompt = STYLE_ANALYSIS_PROMPT + "\n" + conversationText;

            ChatMessage systemMsg = ChatMessage.system("你是专业的用户行为分析助手。严格按照 JSON 格式输出。");
            ChatMessage userMsg = ChatMessage.user(prompt, null);

            ChatRequest request = ChatRequest.builder()
                    .model(modelName)
                    .messages(List.of(systemMsg, userMsg))
                    .build();
            ChatResponse response = llmClient.chat(request);
            String content = response.getContent();

            if (content == null || content.isBlank()) {
                return null;
            }

            Matcher matcher = STYLE_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        } catch (Exception e) {
            log.warn("LLM 风格分析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从记忆事实中提取用户最常关注的领域。
     *
     * @param facts 记忆事实列表
     * @return 关注领域列表
     */
    public List<String> extractDomains(List<MemoryExtractedFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<String> contents = new ArrayList<>(Math.min(facts.size(), MAX_ANALYZE_MESSAGES));
            for (int i = 0; i < facts.size() && i < MAX_ANALYZE_MESSAGES; i++) {
                contents.add(facts.get(i).getCategory() + ": " + facts.get(i).getContent());
            }
            String factsText = String.join("\n", contents);
            String prompt = FACT_ANALYSIS_PROMPT + "\n" + factsText;

            ChatMessage systemMsg = ChatMessage.system("你是专业的用户行为分析助手。严格按照 JSON 格式输出。");
            ChatMessage userMsg = ChatMessage.user(prompt, null);

            ChatRequest request = ChatRequest.builder()
                    .model(modelName)
                    .messages(List.of(systemMsg, userMsg))
                    .build();
            ChatResponse response = llmClient.chat(request);
            String content = response.getContent();

            if (content == null || content.isBlank()) {
                return Collections.emptyList();
            }

            Matcher matcher = DOMAINS_PATTERN.matcher(content);
            if (matcher.find()) {
                String arrayContent = matcher.group(1);
                return parseStringArray(arrayContent);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("LLM 领域提取失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 JSON 数组中的字符串元素。
     *
     * @param arrayContent JSON 数组内容（不含中括号）
     * @return 字符串列表
     */
    private List<String> parseStringArray(String arrayContent) {
        if (arrayContent == null || arrayContent.isBlank()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(COLLECTION_CAPACITY);
        Pattern p = Pattern.compile("\"([^\"]+)\"");
        Matcher m = p.matcher(arrayContent);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

}
