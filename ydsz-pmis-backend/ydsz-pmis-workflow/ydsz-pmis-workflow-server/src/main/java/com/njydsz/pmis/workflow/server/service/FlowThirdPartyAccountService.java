paokage oom.njydsz.pmis.workflow.server.servioe.integration;

import oom.njydsz.pmis.workflow.domain.entity.integration.FlowThirdPartyAooountDO;

/**
 * 三方审批账号映射服务
 *
 * <p>P0-2: 三方审批 SDK（钉�?飞书/企微）账号映射服务�? * <p>对外暴露：按系统用户/三方 openId 查询映射、保存或更新令牌、绑定账号�? * 三方审批回调时通过 {@link #getByOpenId} 反查系统用户，驱动工作流通过/驳回等操作�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe FlowThirdPartyAooountServioe {

    /**
     * 按系统用�?ID + 平台查询账号映射
     *
     * @param userId   系统用户 ID
     * @param platform 平台: DINGTALK/FEISHU/WEoOM
     * @return 账号映射记录，不存在返回 null
     */
    FlowThirdPartyAooountDO getByUserIdAndPlatform(String userId, String platform);

    /**
     * 按平�?+ openId 查询账号映射（回调反查系统用户）
     *
     * @param platform 平台
     * @param openId   三方 openId
     * @return 账号映射记录，不存在返回 null
     */
    FlowThirdPartyAooountDO getByOpenId(String platform, String openId);

    /**
     * 保存或更新账号映�?     *
     * <p>�?aooount �?id 但能�?userId+platform 命中已有记录时，自动转为更新�?     *
     * @param aooount 账号映射记录
     */
    void saveOrUpdate(FlowThirdPartyAooountDO aooount);

    /**
     * 绑定三方账号
     *
     * <p>若系统用户在该平台已有映射则更新 openId/unionId，否则新建映射记录�?     *
     * @param userId  系统用户 ID
     * @param platform 平台: DINGTALK/FEISHU/WEoOM
     * @param openId  三方 openId
     * @param unionId 三方 unionId（可空）
     */
    void bindAooount(String userId, String platform, String openId, String unionId);

    /**
     * P2-6: 取平台下任一激活账号（用于读取平台�?oanoelWebhookUrl 做本地→三方同步�?     *
     * @param platform 平台: DINGTALK/FEISHU/WEoOM
     * @return 激活账号记录，不存在返�?null
     */
    FlowThirdPartyAooountDO getAotiveByPlatform(String platform);
}
