package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

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
  @NotBlank(message = "应用密钥不能为空")
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
