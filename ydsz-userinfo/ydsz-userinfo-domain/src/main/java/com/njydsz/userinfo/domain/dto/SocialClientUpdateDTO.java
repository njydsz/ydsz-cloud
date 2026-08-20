package com.njydsz.userinfo.domain.dto;

import lombok.Data;

/**
 * 社交平台客户端配置更新 DTO（P1-1 CUD 入参）。
 *
 * <p>用于修改已有社交平台客户端配置，所有字段均可选（null 表示不修改）。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
public class SocialClientUpdateDTO {

  /** 平台显示名称 */
  private String platformName;

  /** 应用 ID */
  private String appId;

  /** 应用明文密钥（BCrypt 加密后存储），为空则不修改 */
  private String appSecret;

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
}
