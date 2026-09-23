package com.njydsz.common.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 搜索一致性巡检配置属性。
 *
 * <p>通过 {@code ydsz.search.consistency.*} 配置项控制一致性巡检的启用、间隔与自动修复行为。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@ConfigurationProperties(prefix = "ydsz.search.consistency")
public class SearchConsistencyProperties {

  /** 是否启用索引一致性巡检（默认 true，可通过配置关闭） */
  private boolean enabled = true;

  /** 巡检间隔毫秒数（默认 3600000ms = 1 小时） */
  private long intervalMs = 3_600_000L;

  /** 是否启用自动修复（默认 true）：自动重新索引丢失文档、删除冗余索引 */
  private boolean autoRepair = true;

  /** 单次巡检各类型最大加载文档数（默认 10000），超出后截断避免内存溢出 */
  private int maxLoadSize = 10_000;
}
