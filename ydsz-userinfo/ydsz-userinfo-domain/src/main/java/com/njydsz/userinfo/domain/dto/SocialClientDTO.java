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
 * @since 26.09.01
 */
@Data
public class SocialClientDTO {

  /** 平台 ID（更新时必填） */
  @Xss(message = "平台 ID 包含非法内容")
  private String id;

  /** 平台标识（如 GITHUB/DINGTALK/ENTERPRISE_WECHAT/FEISHU） */
  @NotBlank(message = "平台标识不能为空")
  @Size(max = 32, message = "平台标识长度不能超过 32 个字符")
  @Xss(message = "平台标识包含非法内容")
  private String platform;

  /** 平台显示名称 */
  @Size(max = 128, message = "平台显示名称长度不能超过 128 个字符")
  @Xss(message = "平台显示名称包含非法内容")
  private String platformName;

  /** 应用 ID */
  @NotBlank(message = "应用 ID 不能为空")
  @Size(max = 128, message = "应用 ID 长度不能超过 128 个字符")
  @Xss(message = "应用 ID 包含非法内容")
  private String appId;

  /** 应用明文密钥（BCrypt 加密后存储） */
  @Size(max = 255, message = "应用密钥长度不能超过 255 个字符")
  private String appSecret;

  /** OAuth2 授权范围（scope） */
  @Size(max = 255, message = "授权范围长度不能超过 255 个字符")
  @Xss(message = "授权范围包含非法内容")
  private String scope;

  /** 回调地址（redirectUri），可为 null */
  @Size(max = 512, message = "回调地址长度不能超过 512 个字符")
  @Xss(message = "回调地址包含非法内容")
  private String redirectUri;

  /** 状态：ENABLED / DISABLED */
  @Size(max = 20, message = "状态长度不能超过 20 个字符")
  @Xss(message = "状态包含非法内容")
  private String status;

  /** 排序权重 */
  private Integer sortOrder;

  /** 备注说明 */
  @Size(max = 500, message = "备注长度不能超过 500 个字符")
  @Xss(message = "备注包含非法内容")
  private String remark;
}
