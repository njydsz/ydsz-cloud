package com.njydsz.agent.domain.memory;

/**
 * 记忆整合事件常量定义。
 *
 * <p>定义记忆整合相关的领域事件类型，用于事件总线通信。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public final class MemoryConsolidationEvent {

    private MemoryConsolidationEvent() {
    }

    /** 对话结束事件 — 触发实时记忆提取 */
    public static final String CONVERSATION_COMPLETED = "agent memory conversation_completed";

    /** 记忆提取完成事件 — 提取任务结束 */
    public static final String FACTS_EXTRACTED = "agent memory facts_extracted";

    /** 定时 Dreaming 任务启动事件 */
    public static final String DREAMING_STARTED = "agent memory dreaming_started";

    /** 定时 Dreaming 任务完成事件 */
    public static final String DREAMING_COMPLETED = "agent memory dreaming_completed";
}
