package com.njydsz.userinfo.domain.entity;

import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * OAuth2 应用实体。
 *
 * <p>对应数据库表 {@code ydsz_idp_oauth2_application}，存储 OAuth2 客户端应用注册信息。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code uk_client_id} — clientId 唯一索引</li>
 *   <li>{@code idx_status} — 状态索引</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ydsz_idp_oauth2_application", autoResultMap = true)
public class OAuth2Application extends MpBaseEntity<String> {

  /** 客户端 ID（唯一标识） */
  private String clientId;

  /** 应用名称 */
  private String clientName;

  /** 客户端密钥（BCrypt 加密存储） */
  private String clientSecret;

  /** 客户端类型（CONFIDENTIAL/PUBLIC） */
  private String clientType;

  /** 授权回调地址白名单（JSON 数组） */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private List<String> redirectUris;

  /** 允许申请的权限范围（JSON 数组） */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Set<String> allowedScopes;

  /** 允许的受众（资源服务，JSON 数组） */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Set<String> allowedAudiences;

  /** 应用状态（ENABLED/DISABLED） */
  private String status;

  /** 应用描述 */
  private String description;

  /** 应用图标 URL */
  private String iconUrl;

  /** 创建者用户 ID */
  private String createdBy;
}
