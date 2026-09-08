package com.njydsz.userinfo.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * API Key 实体（P1-2 API Key 授权体系）。
 *
 * <p>对应数据库表 {@code ydsz_auth_apikey}，存储服务间调用或第三方应用的 API Key。
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li>{@code apiKeyHash}：API Key 的 SHA-256 哈希值（不可逆），明文仅在创建时返回一次</li>
 *   <li>{@code apiKeyPrefix}：API Key 前 8 位（明文），用于列表展示识别</li>
 *   <li>{@code scopes}：授权范围（逗号分隔），如 "read,write"</li>
 *   <li>{@code expireAt}：过期时间，为空表示永不过期</li>
 *   <li>{@code rateLimit}：每分钟请求限流阈值（QPS x 60），0 表示不限流</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_auth_apikey")
public class ApiKey extends MpBaseEntity<Long> {

  private static final long serialVersionUID = 1L;

  /** 主键 ID（AUTO 自增，覆盖基类 ASSIGN_ID） */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** API Key SHA-256 哈希值（唯一索引，用于验证） */
  @TableField("api_key_hash")
  private String apiKeyHash;

  /** API Key 前缀（明文，用于列表展示识别） */
  @TableField("api_key_prefix")
  private String apiKeyPrefix;

  /** 所属用户 ID（标识 Key 的持有者） */
  @TableField("user_id")
  private String userId;

  /** Key 名称（用于标识用途） */
  @TableField("key_name")
  private String keyName;

  /** 授权范围（逗号分隔），如 "read,write,admin" */
  private String scopes;

  /** 过期时间（为空表示永不过期） */
  @TableField("expire_at")
  private LocalDateTime expireAt;

  /** 最后使用时间 */
  @TableField("last_used_at")
  private LocalDateTime lastUsedAt;

  /** 每分钟请求限流阈值，0 表示不限流 */
  @TableField("rate_limit")
  private Integer rateLimit;

  /** 是否启用 */
  @TableField("enabled")
  private Boolean isEnabled;
}
