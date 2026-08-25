package com.njydsz.userinfo.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.safe.encrypt.EncryptField;
import com.njydsz.common.safe.encrypt.EncryptTypeHandler;

/**
 * 社交账号绑定实体。
 *
 * <p>对应数据库表 {@code ydsz_social_account}，存储用户与第三方社交平台的绑定关系。
 * 支持微信、钉钉、企业微信、GitHub 等 OAuth2 平台。
 *
 * <p><b>安全敏感字段：</b>
 *
 * <ul>
 *   <li>{@code accessToken}：AES-256-GCM 字段级加密（{@code @EncryptField}），密文存储</li>
 *   <li>{@code refreshToken}：AES-256-GCM 字段级加密（{@code @EncryptField}），密文存储</li>
 * </ul>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code uk_platform_open_id} — 平台+openId 唯一索引（防止同一社交账号重复绑定）</li>
 *   <li>{@code idx_user_id} — 用户 ID 索引（按用户查绑定列表）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_social_account")
public class SocialAccount extends MpBaseEntity<String> {

  /** 关联用户 ID（关联 {@code ydsz_user_account.id}） */
  private String userId;

  /** 平台标识（WECHAT/DINGTALK/ENTERPRISE_WECHAT/GITHUB） */
  private String platform;

  /** 平台用户唯一标识 */
  private String openId;

  /** 平台统一应用标识（可选，微信系平台返回） */
  private String unionId;

  /** 社交昵称（平台侧显示名） */
  private String nickname;

  /** 头像 URL */
  private String avatarUrl;

  /**
   * 访问令牌（AES-256-GCM 加密存储）。
   *
   * <p>使用 common-safe 的 {@link EncryptField} + {@link EncryptTypeHandler} 实现字段级加密，
   * 明文仅在应用内存中出现，数据库存储密文。
   *
   * <p><b>注意：</b>加密字段不可用于 WHERE/LIKE 条件查询，本字段仅用于 SELECT 展示和令牌刷新，不参与条件检索。
   */
  @TableField(typeHandler = EncryptTypeHandler.class)
  @EncryptField
  private String accessToken;

  /**
   * 刷新令牌（AES-256-GCM 加密存储）。
   *
   * <p>与 {@link #accessToken} 相同加密策略。可为 null（部分平台不返回 refresh_token）。
   */
  @TableField(typeHandler = EncryptTypeHandler.class)
  @EncryptField
  private String refreshToken;

  /** 令牌过期时间 */
  private LocalDateTime expiresAt;
}
