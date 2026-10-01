package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 社交平台客户端配置统一 DTO（P1-1 CUD 入参）。
 *
 * <p>同时用于创建和更新场景：创建时 {@code platform} 必填，更新时 {@code id} 必填。
 *
 * <p><b>安全注意：</b>appSecret 明文仅在创建/更新请求中传输（由 HTTPS 保护），服务端接收后
 * 通过 BCrypt 加密存储。更新时如果 appSecret 为空则保留原值（不修改密钥）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class SocialClientDTO {

  /** 平台 ID（更新时必填） */
  @Xss(message = "{userinfo.param.xss}")
  private String id;

  /** 平台标识（如 GITHUB/DINGTALK/ENTERPRISE_WECHAT/FEISHU） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 32, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String platform;

  /** 平台显示名称 */
  @Size(max = 128, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String platformName;

  /** 应用 ID */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 128, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String appId;

  /** 应用明文密钥（BCrypt 加密后存储） */
  @Size(max = 255, message = "{userinfo.error.max_length}")
  private String appSecret;

  /** OAuth2 授权范围（scope） */
  @Size(max = 255, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String scope;

  /** 回调地址（redirectUri），可为 null */
  @Size(max = 512, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String redirectUri;

  /** 状态：ENABLED / DISABLED */
  @Size(max = 20, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String status;

  /** 排序权重 */
  private Integer sort;

  /** 备注说明 */
  @Size(max = 500, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String remark;
}
