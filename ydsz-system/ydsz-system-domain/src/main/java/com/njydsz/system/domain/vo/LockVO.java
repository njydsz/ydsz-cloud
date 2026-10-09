package com.njydsz.system.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.json.annotation.JsonProperty;
import lombok.Data;

/**
 * 分布式锁视图对象（ydsz-system-domain）。
 *
 * <p>展示锁的运维关键信息，用于前端列表渲染。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Data
public class LockVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 锁 Key（不含 "lock:" 前缀） */
  @JsonProperty("lockKey")
  private String lockKey;

  /** 持有者类别（基于 key 前缀自动分类） */
  @JsonProperty("owner")
  private String owner;

  /** 获取时间（由前端基于当前时间推算显示） */
  @JsonProperty("acquiredAt")
  private String acquiredAt;

  /** 过期时间（由前端基于 TTL 推算显示） */
  @JsonProperty("expiresAt")
  private String expiresAt;

  /** 剩余 TTL（毫秒） */
  @JsonProperty("remainingTtlMs")
  private Long remainingTtlMs;

  /** 可重入次数（当前版本默认 1，预留扩展） */
  @JsonProperty("reentrantCount")
  private Integer reentrantCount = 1;
}
