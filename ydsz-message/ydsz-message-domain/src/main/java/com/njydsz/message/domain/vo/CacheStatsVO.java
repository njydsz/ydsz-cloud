package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存统计信息视图对象。
 *
 * <p>反映模板引擎 AST 缓存（YdszCache）的运行时统计指标，包含缓存条目数、命中率、淘汰次数等运维关键数据，
 * 供运维诊断接口（{@code GET /api/v1/message/ops/template-cache/stats}）返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "缓存统计信息")
public class CacheStatsVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 当前缓存条目数 */
  @Schema(description = "当前缓存条目数", example = "42")
  private long size;

  /** 缓存命中次数 */
  @Schema(description = "缓存命中次数", example = "1000")
  private long hitCount;

  /** 缓存未命中次数 */
  @Schema(description = "缓存未命中次数", example = "50")
  private long missCount;

  /** 缓存命中率（0.0 ~ 1.0） */
  @Schema(description = "缓存命中率", example = "0.952")
  private double hitRate;

  /** 缓存淘汰次数 */
  @Schema(description = "缓存淘汰次数", example = "10")
  private long evictionCount;
}
