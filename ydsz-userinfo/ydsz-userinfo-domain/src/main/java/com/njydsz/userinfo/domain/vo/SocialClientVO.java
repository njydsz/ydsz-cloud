package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 社交平台客户端配置视图对象（P1-1 社交认证配置管理）。
 *
 * <p>展示第三方社交平台（企业微信、钉钉、飞书等）的 OAuth2 客户端配置信息，
 * 供管理端配置管理界面展示和编辑。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code platform} — 平台标识（如 ENTERPRISE_WECHAT/DINGTALK/FEISHU）</li>
 *   <li>{@code platformName} — 平台显示名称（如"企业微信"、"钉钉"）</li>
 *   <li>{@code appId} — 平台分配的应用 ID</li>
 *   <li>{@code scope} — OAuth2 授权范围</li>
 *   <li>{@code redirectUri} — 授权回调地址（必须与平台管理后台注册一致）</li>
 *   <li>{@code status} — 状态（ENABLED/DISABLED），禁用后该平台不可登录</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class SocialClientVO {

  /** 客户端配置 ID */
  private String id;

  /** 平台标识 */
  private String platform;

  /** 平台显示名称 */
  private String platformName;

  /** 应用 ID */
  private String appId;

  /** OAuth2 授权范围（scope） */
  private String scope;

  /** 回调地址（redirectUri） */
  private String redirectUri;

  /** 状态：ENABLED / DISABLED */
  private String status;

  /** 排序权重 */
  private Integer sortOrder;

  /** 备注说明 */
  private String remark;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 创建者用户 ID */
  private String createdBy;
}
