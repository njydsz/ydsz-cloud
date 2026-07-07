package com.njydsz.pmis.message.service;

import com.njydsz.pmis.message.dto.SubscriptionUpsertDTO;
import com.njydsz.pmis.message.entity.MsgSubscriptionDO;

import java.util.List;

/**
 * 订阅关系服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SubscriptionService {

    /**
     * 新增或更新订阅关系
     *
     * @param dto 订阅参数
     * @return 订阅实体
     */
    MsgSubscriptionDO upsert(SubscriptionUpsertDTO dto);

    /**
     * 查询用户所有订阅
     *
     * @param userId 用户 ID
     * @return 订阅列表
     */
    List<MsgSubscriptionDO> listByUser(String userId);

    /**
     * 按主题 + 通道查询订阅列表
     *
     * @param topicCode 主题编码
     * @param channel   通道
     * @return 订阅列表
     */
    List<MsgSubscriptionDO> listByTopic(String topicCode, String channel);

    /**
     * 判断用户是否已订阅指定主题 + 通道
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     * @return true 表示已订阅
     */
    boolean isSubscribed(String userId, String topicCode, String channel);

    /**
     * 退订指定主题 + 通道
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     */
    void unsubscribe(String userId, String topicCode, String channel);
}
