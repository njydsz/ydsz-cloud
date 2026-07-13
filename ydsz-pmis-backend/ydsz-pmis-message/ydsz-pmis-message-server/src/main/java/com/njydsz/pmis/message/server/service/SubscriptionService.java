package com.njydsz.pmis.message.server.service.config;

import com.njydsz.pmis.message.domain.dto.config.SubscriptionUpsertDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgSubscriptionDO;

import java.util.List;

/**
 * 订阅关系服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SubscriptionService {

    /**
     * 新增或更新订阅关�?     *
     * @param dto 订阅参数
     * @return 订阅实体
     */
    MsgSubscriptionDO upsert(SubscriptionUpsertDTO dto);

    /**
     * 查询用户所有订�?     *
     * @param userId 用户 ID
     * @return 订阅列表
     */
    List<MsgSubscriptionDO> listByUser(String userId);

    /**
     * 按主�?+ 通道查询订阅列表
     *
     * @param topicCode 主题编码
     * @param channel   通道
     * @return 订阅列表
     */
    List<MsgSubscriptionDO> listByTopic(String topicCode, String channel);

    /**
     * 判断用户是否已订阅指定主�?+ 通道
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     * @return true 表示已订�?     */
    boolean isSubscribed(String userId, String topicCode, String channel);

    /**
     * 判断用户是否已退�?拦截发�?。默认订阅语�?无记录或 SUBSCRIBED 返回 false,
     * 仅当存在 UNSUBSCRIBED 记录时返�?true�?     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     * @return true 表示用户已退�?应拦截发�?     */
    boolean isBlocked(String userId, String topicCode, String channel);

    /**
     * 退订指定主�?+ 通道
     *
     * <p>P1-5: 无订阅记录时新建 UNSUBSCRIBED 记录(修复默认订阅语义下的 latent bug),
     * 并返回退订后的订阅实体�?     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     * @return 退订后的订阅实�?     */
    MsgSubscriptionDO unsubscribe(String userId, String topicCode, String channel);
}
