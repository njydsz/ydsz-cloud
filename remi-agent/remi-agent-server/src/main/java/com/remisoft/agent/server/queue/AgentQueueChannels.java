package com.remisoft.agent.server.queue;

/**
 * Agent 消息队列通道常量
 *
 * <p>定义 agent 模块使用的所有消息队列通道名称，用于异步任务执行、
 * 工具调用结果回传、Human-in-the-Loop 审批请求等场景。
 * 通过 common-queue 的 {@code IMessagePublisher} 发布事件，
 * 其他服务可订阅这些通道实现跨服务异步通信。
 *
 * <p><b>通道说明：</b>
 * <ul>
 *   <li>{@link #AGENT_TASK_RESULT} - Agent 任务执行结果通道（生产方：agent 模块）</li>
 *   <li>{@link #AGENT_APPROVAL_REQUEST} - Human-in-the-Loop 审批请求通道（生产方：agent 模块）</li>
 *   <li>{@link #AGENT_KNOWLEDGE_UPDATE} - 知识库更新事件通道（生产方：agent 模块）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class AgentQueueChannels {

    private AgentQueueChannels() {
    }

    /**
     * Agent 任务执行结果通道
     *
     * <p>Agent 执行完成后将结果发布到此通道，供其他服务消费。
     * 消息体格式：{@code {"agentId":"...", "conversationId":"...", "success":true, "duration":1234}}
     */
    public static final String AGENT_TASK_RESULT = "remi:agent:task-result";

    /**
     * Human-in-the-Loop 审批请求通道
     *
     * <p>Agent 执行过程中需要人工审批时，将审批请求发布到此通道。
     * 消息体格式：{@code {"approvalId":"...", "agentId":"...", "step":"...", "reason":"..."}}
     */
    public static final String AGENT_APPROVAL_REQUEST = "remi:agent:approval-request";

    /**
     * 知识库更新事件通道
     *
     * <p>当 RAG 知识库内容更新（文件上传、文档摄入）时发布到此通道，
     * 供监控、搜索索引等服务消费。
     * 消息体格式：{@code {"fileId":"...", "source":"nextwiki", "action":"INDEX"}}
     */
    public static final String AGENT_KNOWLEDGE_UPDATE = "remi:agent:knowledge-update";
}
