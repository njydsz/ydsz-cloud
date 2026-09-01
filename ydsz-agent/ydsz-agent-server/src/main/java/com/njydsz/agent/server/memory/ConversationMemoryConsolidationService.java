package com.njydsz.agent.server.memory;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.conversation.Conversation;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.memory.MemoryConsolidationService;
import com.njydsz.agent.domain.memory.MemoryExtractedFact;
import com.njydsz.agent.domain.model.ChatMessage;

/**
 * 对话记忆整合应用服务。
 *
 * <p>编排对话结束后的记忆提取流程，是记忆整合的用例控制器。
 * 负责：</p>
 * <ul>
 *   <li>从对话历史加载完整对话内容</li>
 *   <li>调用记忆提取服务获取事实</li>
 *   <li>持久化提取结果</li>
 *   <li>发布整合完成事件</li>
 * </ul>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
@Service
public class ConversationMemoryConsolidationService {

    private final ConversationMemory conversationMemory;
    private final MemoryConsolidationService memoryConsolidationService;

    public ConversationMemoryConsolidationService(ConversationMemory conversationMemory,
                                                  MemoryConsolidationService memoryConsolidationService) {
        this.conversationMemory = conversationMemory;
        this.memoryConsolidationService = memoryConsolidationService;
    }

    /**
     * 对指定对话执行记忆整合。
     *
     * <p>在对话结束后调用，从对话历史中提取有价值的记忆事实。
     * 如果对话不值得提取或提取失败，静默返回 0。</p>
     *
     * @param conversationId 对话 ID
     * @param tenantId       租户 ID
     * @return 提取到的记忆事实数量
     */
    public int consolidateConversation(String conversationId, String tenantId) {
        try {
            List<ChatMessage> messages = conversationMemory.load(conversationId, 100);

            if (messages == null || messages.isEmpty()) {
                log.debug("对话无消息，跳过记忆整合: conversationId={}", conversationId);
                return 0;
            }

            Conversation conversation = new Conversation(
                    conversationId, null, null, "记忆提取", null, null, messages, null);

            if (!memoryConsolidationService.isWorthExtracting(conversation)) {
                log.debug("对话不值得提取记忆: conversationId={}, msgCount={}",
                        conversationId, messages.size());
                return 0;
            }

            List<MemoryExtractedFact> facts =
                    memoryConsolidationService.extractFacts(conversation, tenantId);

            if (facts.isEmpty()) {
                log.debug("未提取到有效记忆: conversationId={}", conversationId);
                return 0;
            }

            int saved = memoryConsolidationService.persistFacts(facts);
            log.info("记忆整合完成: conversationId={}, extracted={}, saved={}",
                    conversationId, facts.size(), saved);
            return saved;
        } catch (Exception e) {
            log.warn("记忆整合异常: conversationId={}, error={}", conversationId, e.getMessage());
            return 0;
        }
    }

    /**
     * 批量整合多个对话的记忆（用于定时 Dreaming 任务）。
     *
     * @param conversationIds 对话 ID 列表
     * @param tenantId        租户 ID
     * @return 总共提取到的记忆事实数量
     */
    public int batchConsolidate(List<String> conversationIds, String tenantId) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return 0;
        }

        int totalFacts = 0;
        int processed = 0;

        for (String conversationId : conversationIds) {
            try {
                int count = consolidateConversation(conversationId, tenantId);
                totalFacts += count;
                processed++;
            } catch (Exception e) {
                log.warn("批量记忆整合单条失败: conversationId={}, error={}",
                        conversationId, e.getMessage());
            }
        }

        log.info("批量记忆整合完成: total={}, processed={}, facts={}",
                conversationIds.size(), processed, totalFacts);
        return totalFacts;
    }
}
