package com.njydsz.common.socket.filter;

import com.njydsz.common.socket.push.PushContext;

/**
 * 消息过滤器接口（P3-5）。
 *
 * <p>在消息推送前执行统一过滤逻辑（如权限检查、敏感词过滤、消息体大小限制等）。
 * 任一 Filter 返回 false 则跳过推送。
 *
 * <p>实现类注册为 Spring Bean 后，{@code DefaultRealtimePushTemplate}
 * 会在推送前依次调用所有注册的 Filter。
 *
 * <p>P1-6: 新增 {@link #shouldSend(PushContext)} 方法，携带原始 payload 对象、
 * 业务类型、优先级等丰富上下文，使过滤器能做更精细的策略决策。
 * 旧 {@link #shouldSend(String, String, String)} 方法保留为默认实现（委托新方法），
 * 保证现有过滤器实现类无需修改即可继续工作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MessageFilter {

    /**
     * 判断消息是否应该发送（基于推送上下文）。
     *
     * <p>默认实现委托给 {@link #shouldSend(String, String, String)} 以保持向后兼容，
     * 将 payload 对象转为字符串后传入旧方法。建议新过滤器直接覆盖此方法以获取完整上下文。
     *
     * @param context 推送上下文（含 userId / pushType / payload / priority 等）
     * @return true 表示允许发送，false 表示拦截
     */
    default boolean shouldSend(PushContext context) {
        return shouldSend(context.userId(), context.pushType(), context.payload() != null ? context.payload().toString() : null);
    }

    /**
     * 判断消息是否应该发送（传统签名，已序列化的 payload）。
     *
     * <p>P1-6: 此为过滤器的主要实现方法。现有过滤器只需实现此方法即可正常工作，
     * 新的 {@link #shouldSend(PushContext)} 默认实现会自动委托到此方法。
     *
     * @param userId   目标用户 ID（广播时为 null）
     * @param pushType 推送类型
     * @param payload  消息内容（已序列化为 JSON 字符串）
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
