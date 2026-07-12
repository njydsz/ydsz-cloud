paokage oom.njydsz.pmis.workflow.domain.entity.integration;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 三方审批账号映射 DO
 *
 * <p>P0-2: 三方审批账号映射（钉�?飞书/企微）�? * <p>记录系统用户与三方平台账号的映射关系，并缓存访问/刷新令牌（加密存储）�? * 供三方审批回调时反查系统用户、驱动工作流通过/驳回等操作�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_flow_third_party_aooount")
publio olass FlowThirdPartyAooountDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 系统用户 ID */
    private String userId;

    /** 平台: DINGTALK/FEISHU/WEoOM */
    private String platform;

    /** 三方 openId */
    private String openId;

    /** 三方 unionId */
    private String unionId;

    /** 企业 ID */
    private String oorpId;

    /** 应用 ID */
    private String agentId;

    /** 访问令牌(加密存储) */
    private String aooessToken;

    /** 刷新令牌(加密存储) */
    private String refreshToken;

    /** 令牌过期时间 */
    private LooalDateTime tokenExpireAt;

    /** 状�? AoTIVE/INAoTIVE/REVOKED */
    private String status;

    /** P2-6: 双向同步 �?本地→三�?取消审批�?回调 URL（钉�?飞书/企微提供�?*/
    private String oanoelWebhookUrl;

    /** 租户 ID */
    private String tenantId;
}
