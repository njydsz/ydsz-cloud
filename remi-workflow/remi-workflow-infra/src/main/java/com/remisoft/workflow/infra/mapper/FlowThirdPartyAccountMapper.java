package com.remisoft.workflow.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.workflow.domain.entity.FlowThirdPartyAccount;

/**
 * 三方审批账号映射 Mapper
 *
 * <p>对应数据表 <code>remi_flow_third_party_account</code>（P0-2），存储 remi 用户与三方平台账号的映射。</p>
 * <p>用于审批消息推送/回调（钉钉/飞书/企微），按三方平台类型 + 三方用户 ID 唯一，反向通过 remi 用户 ID 查找。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_platform_user — (platform+thirdUserId) 唯一索引</li>
 *   <li>idx_user_id — remi 用户 ID 索引（反向查询）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.workflow.domain.entity.FlowThirdPartyAccount 三方账号映射实体
 * @see com.remisoft.workflow.server.service.FlowThirdPartyService 三方 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowThirdPartyAccountMapper extends BaseMapper<FlowThirdPartyAccount> {

    /**
     * 按系统用户 ID + 平台查询
     *
     * @param userId   系统用户 ID
     * @param platform 平台: DINGTALK/FEISHU/WECOM
     * @return 账号映射记录
     */
    FlowThirdPartyAccount selectByUserIdAndPlatform(@Param("userId") String userId,
                                                      @Param("platform") String platform);

    /**
     * 按平台 + openId 查询（回调反查系统用户）
     *
     * @param platform 平台
     * @param openId   三方 openId
     * @return 账号映射记录
     */
    FlowThirdPartyAccount selectByOpenId(@Param("platform") String platform,
                                           @Param("openId") String openId);

    /**
     * P2-6: 取平台下任一激活账号（用于读取平台级 cancelWebhookUrl）
     */
    @Select(
            "SELECT * FROM remi_flow_third_party_account WHERE platform = #{platform} " +
            "AND status = 'ACTIVE' LIMIT 1")
    FlowThirdPartyAccount selectActiveByPlatform(@Param("platform") String platform);
}
