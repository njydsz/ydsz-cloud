package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 社交平台客户端配置视图出参（P1-1 查询返回值）。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Data
public class SocialClientVO {

  /** 客户端配置 ID */
  private String id;

  /** 平台标识 */
  private String platform;

  /** 平台显示名称 */
  private String platformName;

  /** 应用 ID */
  private String appId;

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

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 创建者用户 ID */
  private String createdBy;
}
