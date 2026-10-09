package com.njydsz.system.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.json.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 分布式锁统计视图对象（ydsz-system-domain）。
 *
 * <p>展示锁统计快照：活跃数/超时数/持有者分布。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Data
public class LockStatsVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 活跃锁数量 */
  @JsonProperty("activeLockCount")
  private Integer activeLockCount;

  /** 已超时锁数量 */
  @JsonProperty("expiredCount")
  private Integer expiredCount;

  /** 持有者分布（类别 → 锁数量） */
  @JsonProperty("ownerDistribution")
  private Map<String, Integer> ownerDistribution;
}
