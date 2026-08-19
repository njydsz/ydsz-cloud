package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BloomFilter 统计信息视图对象。
 *
 * <p>反映消息去重 BloomFilter 的运行状态，包含预期插入条目数、当前误判率、窗口年龄等运维关键数据，
 * 供运维诊断接口（{@code GET /api/v1/message/ops/bloomfilter/stats}）返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "BloomFilter 统计信息")
public class BloomFilterStatsVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 预期插入条目数（单窗口容量） */
  @Schema(description = "预期插入条目数", example = "1000000")
  private long expectedInsertions;

  /** 当前误判率 */
  @Schema(description = "当前误判率", example = "0.001")
  private double fpp;

  /** 当前窗口是否为主窗口（true=活跃写入窗口） */
  @Schema(description = "当前窗口是否为主窗口", example = "true")
  private boolean primary;

  /** 当前窗口已运行秒数 */
  @Schema(description = "当前窗口已运行秒数", example = "35")
  private long windowAgeSeconds;
}
