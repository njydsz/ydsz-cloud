package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 三方审批账号映射 DO
 *
 * <p>P0-2: 三方审批账号映射（钉钉/飞书/企微）。
 * <p>记录系统用户与三方平台账号的映射关系，并缓存访问/刷新令牌（加密存储），
 * 供三方审批回调时反查系统用户、驱动工作流通过/驳回等操作。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_third_party_account")
public class FlowThirdPartyAccountDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 系统用户 ID */
    private Long userId;

    /** 平台: DINGTALK/FEISHU/WECOM */
    private String platform;

    /** 三方 openId */
    private String openId;

    /** 三方 unionId */
    private String unionId;

    /** 企业 ID */
    private String corpId;

    /** 应用 ID */
    private String agentId;

    /** 访问令牌(加密存储) */
    private String accessToken;

    /** 刷新令牌(加密存储) */
    private String refreshToken;

    /** 令牌过期时间 */
    private LocalDateTime tokenExpireAt;

    /** 状态: ACTIVE/INACTIVE/REVOKED */
    private String status;

    /** 租户 ID */
    private Long tenantId;
}
