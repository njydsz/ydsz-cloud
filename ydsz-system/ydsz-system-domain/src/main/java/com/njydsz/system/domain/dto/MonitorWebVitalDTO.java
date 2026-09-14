package com.njydsz.system.domain.dto;

import lombok.Data;

/**
 * Web Vitals 指标条目 DTO
 *
 * <p>对应 ydsz-micro 中 {@code @ydsz/monitor} 的 WebVitalReport 结构。前端基于
 * PerformanceObserver 采集 Core Web Vitals，批量上报至 {@code POST /monitor/web-vitals}。
 *
 * <p><strong>单位约定：</strong>{@code LCP} / {@code FID} / {@code INP} / {@code FCP} /
 * {@code TTFB} / {@code LT}（长任务）为毫秒；{@code CLS} 为无量纲累计得分；
 * {@code RT}（资源耗时）为毫秒。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Data
public class MonitorWebVitalDTO {

  /** 指标名：LCP / FID / CLS / INP / FCP / TTFB / LT / RT */
  private String name;

  /** 指标值（单位见类注释：毫秒或无量纲得分） */
  private Double value;

  /** 评级：good / needs-improvement / poor（阈值遵循 Google Web Vitals 标准） */
  private String rating;

  /** 相对上次上报的增量（CLS 为累计增量，其余通常等于 value） */
  private Double delta;

  /** 指标唯一 ID（前端生成，用于去重） */
  private String id;

  /** 采集页面路径 */
  private String page;

  /** 采集时间戳（epoch 毫秒） */
  private Long timestamp;
}
