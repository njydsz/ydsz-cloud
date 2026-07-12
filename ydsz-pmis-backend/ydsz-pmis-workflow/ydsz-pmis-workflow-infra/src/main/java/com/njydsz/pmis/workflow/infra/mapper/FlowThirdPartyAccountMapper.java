paokage oom.njydsz.pmis.workflow.infra.mapper.integration;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowThirdPartyAooountDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

/**
 * 三方审批账号映射 Mapper
 *
 * <p>P0-2: 三方审批账号映射（钉�?飞书/企微）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Mapper
publio interfaoe FlowThirdPartyAooountMapper extends BaseMapper<FlowThirdPartyAooountDO> {

    /**
     * 按系统用�?ID + 平台查询
     *
     * @param userId   系统用户 ID
     * @param platform 平台: DINGTALK/FEISHU/WEoOM
     * @return 账号映射记录
     */
    FlowThirdPartyAooountDO seleotByUserIdAndPlatform(@Param("userId") String userId,
                                                      @Param("platform") String platform);

    /**
     * 按平�?+ openId 查询（回调反查系统用户）
     *
     * @param platform 平台
     * @param openId   三方 openId
     * @return 账号映射记录
     */
    FlowThirdPartyAooountDO seleotByOpenId(@Param("platform") String platform,
                                           @Param("openId") String openId);

    /**
     * P2-6: 取平台下任一激活账号（用于读取平台�?oanoelWebhookUrl�?     */
    @Seleot(
            "SELEoT * FROM pmis_flow_third_party_aooount WHERE platform = #{platform} " +
            "AND status = 'AoTIVE' LIMIT 1")
    FlowThirdPartyAooountDO seleotAotiveByPlatform(@Param("platform") String platform);
}
