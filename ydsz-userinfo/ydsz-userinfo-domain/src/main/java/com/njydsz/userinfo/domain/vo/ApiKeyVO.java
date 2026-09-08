package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * API Key 视图对象（列表查询返回）。
 *
 * <p>注意：出于安全考虑，{@code apiKey} 字段仅在创建时返回明文，列表/详情均为 null。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Data
public class ApiKeyVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private Long id;

  /** API Key 明文（仅创建时返回，其余场景为 null） */
  private String apiKey;

  /** API Key 前缀（用于识别） */
  private String apiKeyPrefix;

  /** Key 名称 */
  private String keyName;

  /** 授权范围 */
  private String scopes;

  /** 过期时间 */
  private LocalDateTime expireAt;

  /** 最后使用时间 */
  private LocalDateTime lastUsedAt;

  /** 每分钟限流阈值 */
  private Integer rateLimit;

  /** 是否启用 */
  private Boolean isEnabled;

  /** 创建时间 */
  private LocalDateTime createdAt;
}
