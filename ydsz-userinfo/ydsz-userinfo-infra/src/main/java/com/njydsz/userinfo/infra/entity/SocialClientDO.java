package com.njydsz.userinfo.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 社交平台客户端配置实体（P1-1 热更新）。
 *
 * <p>对应数据库表 {@code ydsz_social_client}，存储社交平台 OAuth2 应用的客户端配置。
 * 配置优先从数据库加载（热更新），数据库未配置时回落到 {@code application.yml} 的 {@code ydsz.userinfo.social.providers}。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code uk_platform} — 平台标识唯一索引（每个平台仅一条配置）</li>
 * </ul>
 *
 * <p><b>状态管理：</b>
 *
 * <ul>
 *   <li>{@code ENABLED} — 启用（登录和授权可用）</li>
 *   <li>{@code DISABLED} — 禁用（前端隐藏，后端拒绝回调）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_social_client")
public class SocialClientDO extends MpBaseEntity<String> {

  /** 平台标识（如 GITHUB/DINGTALK/ENTERPRISE_WECHAT/FEISHU） */
  private String platform;

  /** 平台显示名称（用于前端展示，如 "GitHub"、"钉钉"） */
  private String platformName;

  /** 应用 ID（平台分配的 appId / clientId） */
  private String appId;

  /** 应用密钥（平台分配的 appSecret / clientSecret，BCrypt 加密存储） */
  private String appSecret;

  /** OAuth2 授权范围（scope，格式依平台而定） */
  private String scope;

  /**
   * 平台回调地址（redirectUri）。
   *
   * <p>授权完成后平台重定向回应用的完整 URL。可为 null（使用全局默认回调路径）。
   */
  private String redirectUri;

  /** 状态：ENABLED / DISABLED */
  private String status;

  /** 排序权重（越小越靠前，用于前端展示排序） */
  private Integer sortOrder;

  /** 备注说明 */
  private String remark;
}
