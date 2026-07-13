package com.njydsz.pmis.workflow.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowThirdPartyAccountDO;

/**
 * 三方审批账号映射 Mapper
 *
 * <p>P0-2: 三方审批账号映射（钉钉/飞书/企微）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface FlowThirdPartyAccountMapper extends BaseMapper<FlowThirdPartyAccountDO> {

    /**
     * 按系统用户 ID + 平台查询
     *
     * @param userId   系统用户 ID
     * @param platform 平台: DINGTALK/FEISHU/WECOM
     * @return 账号映射记录
     */
    FlowThirdPartyAccountDO selectByUserIdAndPlatform(@Param("userId") String userId,
                                                      @Param("platform") String platform);

    /**
     * 按平台 + openId 查询（回调反查系统用户）
     *
     * @param platform 平台
     * @param openId   三方 openId
     * @return 账号映射记录
     */
    FlowThirdPartyAccountDO selectByOpenId(@Param("platform") String platform,
                                           @Param("openId") String openId);

    /**
     * P2-6: 取平台下任一激活账号（用于读取平台级 cancelWebhookUrl）
     */
    @Select(
            "SELECT * FROM pmis_flow_third_party_account WHERE platform = #{platform} " +
            "AND status = 'ACTIVE' LIMIT 1")
    FlowThirdPartyAccountDO selectActiveByPlatform(@Param("platform") String platform);
}
