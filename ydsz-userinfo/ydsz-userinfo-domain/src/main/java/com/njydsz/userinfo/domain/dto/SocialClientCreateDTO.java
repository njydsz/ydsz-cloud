package com.njydsz.userinfo.domain.dto;

import lombok.Data;

/**
 * 社交平台客户端配置创建 DTO（P1-1 CUD 入参）。
 *
 * <p>用于新增社交平台客户端配置，前端或 OAuth2 应用注册接口传入。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
public class SocialClientCreateDTO {

  /** 平台标识（如 GITHUB/DINGTALK/ENTERPRISE_WECHAT/FEISHU） */
  private String platform;

  /** 平台显示名称 */
  private String platformName;

  /** 应用 ID */
  private String appId;

  /** 应用明文密钥（BCrypt 加密后存储） */
  private String appSecret;

  /** OAuth2 授权范围（scope） */
  private String scope;

  /** 回调地址（redirectUri），可为 null */
  private String redirectUri;

  /** 状态：ENABLED / DISABLED */
  private String status;

  /** 排序权重 */
  private Integer sortOrder;

  /** 备注说明 */
  private String remark;
}
