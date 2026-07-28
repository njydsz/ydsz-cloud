package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 三方审批账号映射实体
 *
 * <p>对应数据库表 {@code ydsz_flow_third_party_account}，P0-2: 三方审批账号映射（钉钉/飞书/企微）。
 * 记录系统用户与三方平台账号的映射关系，并缓存访问/刷新令牌（加密存储），
 * 供三方审批回调时反查系统用户、驱动工作流通过/驳回等操作。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>系统用户 ↔ 三方平台账号绑定（一个用户可在多平台有账号）</li>
 *   <li>缓存 access/refresh token（加密存储），供审批同步使用</li>
 *   <li>三方审批回调时反查系统用户，触发流程推进</li>
 *   <li>支持「本地 → 三方」双向同步（如本地撤销时调用 cancelWebhookUrl）</li>
 * </ul>
 *
 * <p><b>支持平台（{@code platform}）：</b>
 * <ul>
 *   <li>{@code DINGTALK}：钉钉审批</li>
 *   <li>{@code FEISHU}：飞书审批</li>
 *   <li>{@code WECOM}：企业微信审批</li>
 * </ul>
 *
 * <p><b>状态机（{@code status}）：</b>{@code ACTIVE}（生效中）/ {@code INACTIVE}（未激活）/ {@code REVOKED}（已撤销授权）。
 *
 * <p><b>令牌刷新：</b>由 {@code ThirdPartyTokenRefresher} 调度器在 {@code tokenExpireAt} 前 5 分钟触发刷新，
 * 避免调用三方 API 时 token 失效。
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>唯一索引 {@code uk_platform_open}（{@code platform}, {@code openId}）</li>
 *   <li>普通索引 {@code idx_user}（{@code user_id}）：查询用户的绑定</li>
 *   <li>普通索引 {@code idx_token_expire}（{@code token_expire_at}）：token 刷新扫描</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.server.thirdparty.ThirdPartyApprovalClient 三方审批客户端
 * @see com.njydsz.workflow.server.scheduler.ThirdPartyTokenRefresher token 刷新调度器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_third_party_account")
public class FlowThirdPartyAccount extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 系统用户 ID */
    private String userId;

    /** 平台：{@code DINGTALK} / {@code FEISHU} / {@code WECOM} */
    private String platform;

    /** 三方 openId（平台内唯一用户标识） */
    private String openId;

    /** 三方 unionId（跨应用统一用户标识，可空） */
    private String unionId;

    /** 企业 ID（钉钉 corpId / 飞书 tenant_access_token 内字段 / 企微 corpid） */
    private String corpId;

    /** 应用 ID（{@code agentId}，用于审批应用调用） */
    private String agentId;

    /** 访问令牌（加密存储，{@code AES-GCM} + 项目盐） */
    private String accessToken;

    /** 刷新令牌（加密存储） */
    private String refreshToken;

    /** 令牌过期时间 */
    private LocalDateTime tokenExpireAt;

    /** 状态：{@code ACTIVE} / {@code INACTIVE} / {@code REVOKED} */
    private String status;

    /** 本地 → 三方「取消审批单」回调 URL（钉钉/飞书/企微提供，本地撤销时回调） */
    private String cancelWebhookUrl;

}
