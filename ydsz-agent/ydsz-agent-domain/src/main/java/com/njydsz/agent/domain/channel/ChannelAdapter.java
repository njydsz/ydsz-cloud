package com.njydsz.agent.domain.channel;

/**
 * 渠道适配器接口。
 *
 * <p>定义渠道适配器的标准契约，实现类负责：
 * <ul>
 *   <li>接收并解析渠道请求</li>
 *   <li>将请求转换为统一的 Agent 执行调用</li>
 *   <li>将执行结果转换为渠道特定的响应格式</li>
 *   <li>隔离渠道特定的异常，防止影响其他渠道</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的多渠道设计，实现错误隔离和独立恢复。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public interface ChannelAdapter {

    /**
     * 获取渠道类型。
     *
     * @return 渠道类型
     */
    ChannelType getChannelType();

    /**
     * 处理渠道请求。
     *
     * @param request 渠道请求
     * @return 渠道响应
     */
    ChannelResponse handleRequest(ChannelRequest request);

    /**
     * 检查渠道是否健康可用。
     *
     * @return 是否健康
     */
    boolean isHealthy();

    /**
     * 获取渠道状态信息。
     *
     * @return 状态信息
     */
    ChannelStatus getStatus();

    /**
     * 渠道状态信息。
     */
    record ChannelStatus(
            ChannelType channelType,
            boolean healthy,
            long totalRequests,
            long failedRequests,
            double failureRate,
            String lastError) {
    }
}
