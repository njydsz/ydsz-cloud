package com.njydsz.message.server.service.config;

import java.util.List;

import com.njydsz.common.core.response.PageResult;
import com.njydsz.message.domain.dto.config.UnsubscribeQueryDTO;
import com.njydsz.message.domain.entity.config.MsgSubscription;
import com.njydsz.message.server.token.UnsubscribeTokenPayload;

/**
 * 退订服务接口。
 * <p>管理用户对模板/渠道/标签的退订关系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


public interface UnsubscribeService {

    /**
     * 生成退订 token（供发送链路在消息正文 / 邮件 footer 中嵌入退订链接）。
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     * @return 签名后的 token 字符串
     */
    String generateToken(String userId, String topicCode, String channel);

    /**
     * 预览 token 内容（不执行退订，用于确认页渲染）。
     *
     * @param token token 字符串
     * @return 载荷
     */
    UnsubscribeTokenPayload previewToken(String token);

    /**
     * 通过 token 一键退订。
     *
     * <p>token 校验通过后调用 {@link SubscriptionService#unsubscribe} 执行退订。
     * 幂等：重复调用不会报错，仅更新退订时间。
     *
     * @param token token 字符串
     * @return 退订后的订阅记录
     */
    MsgSubscription unsubscribeByToken(String token);

    /**
     * 分页查询已退订记录（管理后台）。
     *
     * @param query 查询参数
     * @return 分页结果，仅包含 status=UNSUBSCRIBED 的记录
     */
    PageResult<List<MsgSubscription>> pageUnsubscribed(UnsubscribeQueryDTO query);

    /**
     * 恢复订阅（管理后台 / 用户自助）。
     *
     * <p>将指定 (userId, topicCode, channel) 的订阅状态从 UNSUBSCRIBED 改回 SUBSCRIBED，
     * 并清空退订时间。若记录不存在则按 SUBSCRIBED 新建。
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     */
    void resubscribe(String userId, String topicCode, String channel);
}
