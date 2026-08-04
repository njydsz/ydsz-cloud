package com.remisoft.common.socket.filter;

/**
 * 消息过滤器接口（P3-5）。
 *
 * <p>在消息推送前执行统一过滤逻辑（如权限检查、敏感词过滤、消息体大小限制等）。
 * 任一 Filter 返回 false 则跳过推送。
 *
 * <p>实现类注册为 Spring Bean 后，{@code DefaultRealtimePushTemplate}
 * 会在推送前依次调用所有注册的 Filter。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface MessageFilter {

    /**
     * 判断消息是否应该发送。
     *
     * @param userId    目标用户 ID（广播时为 null）
     * @param pushType  推送类型
     * @param payload   消息内容（已序列化为 JSON 字符串）
     * @return true 表示允许发送，false 表示拦截
     */
    boolean shouldSend(String userId, String pushType, String payload);

    /**
     * 获取过滤器名称。
     *
     * @return 过滤器名称
     */
    String getName();
}
