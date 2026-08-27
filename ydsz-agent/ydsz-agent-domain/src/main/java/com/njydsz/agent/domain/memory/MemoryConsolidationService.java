package com.njydsz.agent.domain.memory;

import com.njydsz.agent.domain.conversation.Conversation;
import java.util.List;

/**
 * 记忆整合服务网关接口。
 *
 * <p>定义从对话中提取有价值信息的操作，由基础设施层实现。
 * 支持两种整合模式：</p>
 * <ul>
 *   <li>实时整合 — 对话结束后立即提取（Post-Consolidation）</li>
 *   <li>定时整合 — 夜间/低谷期批量处理（Dreaming）</li>
 * </ul>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public interface MemoryConsolidationService {

    /**
     * 从对话中提取有价值的记忆事实。
     *
     * @param conversation 对话聚合对象
     * @param tenantId     租户 ID
     * @return 提取到的记忆事实列表
     */
    List<MemoryExtractedFact> extractFacts(Conversation conversation, String tenantId);

    /**
     * 判断是否值得从该对话中提取记忆。
     *
     * <p>用于过滤过于短暂或无实质内容的对话，避免浪费 LLM 调用。</p>
     *
     * @param conversation 对话聚合对象
     * @return true 如果对话值得提取记忆
     */
    boolean isWorthExtracting(Conversation conversation);

    /**
     * 将已确认的记忆事实持久化到长期存储。
     *
     * @param facts 待保存的记忆事实列表
     * @return 成功保存的数量
     */
    int persistFacts(List<MemoryExtractedFact> facts);
}
