package com.njydsz.pmis.agent.server.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.model.ChatChunk;
import com.njydsz.pmis.agent.domain.model.ChatMessage;
import com.njydsz.pmis.agent.domain.model.ChatRequest;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.domain.model.TokenUsage;
import com.njydsz.pmis.agent.server.config.AgentProperties;

/**
 * 对话服务
 *
 * <p>提供同步和流式两种对话模式：
 * <ul>
 *   <li>{@link #chat} — 同步调用，返回完整响应</li>
 *   <li>{@link #stream} — 流式调用，逐 token 回调</li>
 * </ul>
 *
 * <p>对话流程：
 * <ol>
 *   <li>加载历史消息（滑动窗口）</li>
 *   <li>拼接 System Prompt + 历史消息 + 用户消息</li>
 *   <li>调用 LLM 获取响应</li>
 *   <li>保存用户消息和助手响应到记忆</li>
 *   <li>返回响应</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmClient llmClient;
    private final ConversationMemory memory;
    private final AgentProperties properties;

    public ChatService(LlmClient llmClient, ConversationMemory memory, AgentProperties properties) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.properties = properties;
    }

    /**
     * 同步对话
     *
     * @param conversationId 对话 ID（null 则新建）
     * @param userMessage    用户消息
     * @param systemPrompt   系统提示词（null 则使用默认）
     * @return 助手回复
     */
    public ChatResponse chat(String conversationId, String userMessage, String systemPrompt) {
        String convId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        log.info("[Chat] 同步对话: convId={}, messageLen={}", convId, userMessage.length());

        List<ChatMessage> messages = buildMessages(convId, userMessage, systemPrompt);
        ChatRequest request = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(messages)
                .temperature(properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens())
                .build();

        ChatResponse response = llmClient.chat(request);

        memory.save(convId, ChatMessage.user(userMessage, convId));
        ChatMessage assistantMsg = ChatMessage.assistant(
                response.getContent(), convId, response.getUsage());
        memory.save(convId, assistantMsg);

        log.info("[Chat] 对话完成: convId={}, tokens={}", convId,
                response.getUsage() != null ? response.getUsage().getTotalTokens() : 0);
        return response;
    }

    /**
     * 流式对话
     *
     * @param conversationId 对话 ID（null 则新建）
     * @param userMessage    用户消息
     * @param systemPrompt   系统提示词（null 则使用默认）
     * @param chunkConsumer  流式片段消费者
     */
    public void stream(String conversationId, String userMessage, String systemPrompt,
                       Consumer<ChatChunk> chunkConsumer) {
        String convId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        log.info("[Chat] 流式对话: convId={}, messageLen={}", convId, userMessage.length());

        List<ChatMessage> messages = buildMessages(convId, userMessage, systemPrompt);
        ChatRequest request = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(messages)
                .temperature(properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens())
                .stream(true)
                .build();

        StringBuilder contentBuilder = new StringBuilder();
        final TokenUsage[] usage = {TokenUsage.zero()};

        llmClient.stream(request, chunk -> {
            if (chunk.hasContent()) {
                contentBuilder.append(chunk.getDeltaContent());
            }
            if (chunk.isFinished() && chunk.getUsage() != null) {
                usage[0] = chunk.getUsage();
            }
            chunkConsumer.accept(chunk);
        });

        memory.save(convId, ChatMessage.user(userMessage, convId));
        ChatMessage assistantMsg = ChatMessage.assistant(
                contentBuilder.toString(), convId, usage[0]);
        memory.save(convId, assistantMsg);

        log.info("[Chat] 流式对话完成: convId={}, tokens={}", convId, usage[0].getTotalTokens());
    }

    /**
     * 获取对话历史
     */
    public List<ChatMessage> getHistory(String conversationId) {
        return memory.load(conversationId, properties.getMemory().getMaxMessages());
    }

    /**
     * 清除对话历史
     */
    public void clearHistory(String conversationId) {
        memory.clear(conversationId);
    }

    private List<ChatMessage> buildMessages(String conversationId, String userMessage,
                                             String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        String prompt = systemPrompt != null ? systemPrompt : getDefaultSystemPrompt();
        messages.add(ChatMessage.system(prompt));
        List<ChatMessage> history = memory.load(conversationId,
                properties.getMemory().getMaxMessages());
        messages.addAll(history);
        messages.add(ChatMessage.user(userMessage, conversationId));
        return messages;
    }

    private String getDefaultSystemPrompt() {
        return "你是 PMIS 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、"
                + "分析项目进度、发起审批流程、发送消息通知等。请用中文回答。";
    }
}
