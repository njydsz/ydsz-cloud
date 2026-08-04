package com.remisoft.workflow.server.service;

import com.remisoft.workflow.domain.entity.FlowThirdPartyAccount;

/**
 * 第三方审批账号服务。
 * <p>IM 账号与本系统用户映射。
 *
 * @author remi-team
 * @since 1.0.0
 */


public interface FlowThirdPartyAccountService {

    /**
     * 按系统用户 ID + 平台查询账号映射
     *
     * @param userId   系统用户 ID
     * @param platform 平台: DINGTALK/FEISHU/WECOM
     * @return 账号映射记录，不存在返回 null
     */
    FlowThirdPartyAccount getByUserIdAndPlatform(String userId, String platform);

    /**
     * 按平台 + openId 查询账号映射（回调反查系统用户）
     *
     * @param platform 平台
     * @param openId   三方 openId
     * @return 账号映射记录，不存在返回 null
     */
    FlowThirdPartyAccount getByOpenId(String platform, String openId);

    /**
     * 保存或更新账号映射
     *
     * <p>当 account 无 id 但能按 userId+platform 命中已有记录时，自动转为更新。
     *
     * @param account 账号映射记录
     */
    void saveOrUpdate(FlowThirdPartyAccount account);

    /**
     * 绑定三方账号
     *
     * <p>若系统用户在该平台已有映射则更新 openId/unionId，否则新建映射记录。
     *
     * @param userId  系统用户 ID
     * @param platform 平台: DINGTALK/FEISHU/WECOM
     * @param openId  三方 openId
     * @param unionId 三方 unionId（可空）
     */
    void bindAccount(String userId, String platform, String openId, String unionId);

    /**
     * P2-6: 取平台下任一激活账号（用于读取平台级 cancelWebhookUrl 做本地→三方同步）
     *
     * @param platform 平台: DINGTALK/FEISHU/WECOM
     * @return 激活账号记录，不存在返回 null
     */
    FlowThirdPartyAccount getActiveByPlatform(String platform);
}
