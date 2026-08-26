package com.njydsz.userinfo.infra.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * WebAuthn 凭证持久化实体
 *
 * <p>对应数据库表 ydsz_auth_credential，存储用户注册的公钥凭证。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@TableName("ydsz_auth_credential")
public class WebAuthnCredential implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键 ID */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 凭证 ID（Base64URL 编码，唯一索引） */
  @TableField("credential_id")
  private String credentialId;

  /** 用户 ID */
  @TableField("user_id")
  private String userId;

  /** 公钥（COSE 密钥格式，Base64URL 编码） */
  @TableField("public_key")
  private String publicKey;

  /** 签名计数器（防克隆检测） */
  @TableField("sign_count")
  private Long signCount;

  /** 凭证类型（如 "public-key"） */
  @TableField("credential_type")
  private String credentialType;

  /** AAGUID（认证器唯一标识） */
  @TableField("aaguid")
  private String aaguid;

  /** 凭证友好名称 */
  @TableField("display_name")
  private String displayName;

  /** 注册时间 */
  @TableField("registered_at")
  private LocalDateTime registeredAt;

  /** 最后使用时间 */
  @TableField("last_used_at")
  private LocalDateTime lastUsedAt;

  /** 创建时间 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;

  /** 删除标记（软删除） */
  @TableField("deleted")
  private Boolean deleted;
}
