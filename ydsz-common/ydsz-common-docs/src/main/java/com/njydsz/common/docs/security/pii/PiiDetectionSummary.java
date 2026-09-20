package com.njydsz.common.docs.security.pii;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import com.njydsz.common.docs.enums.PiiType;

/**
 * PII 检测过程汇总指标
 *
 * <p>记录一轮完整 PII 检测的运行态数据：总耗时、各类型命中数、失败检测器数。
 *
 * <p>与 {@link com.njydsz.common.docs.domain.PiiFinding}（单条命中）严格分离：
 * 后者是业务层敏感数据，前者是运行态指标供监控日志与排障使用。
 * 避免在检测器内部混合统计逻辑（{@code PiiDetector.detect} 仅返回命中列表），
 * 让观测职责锚定在本记录类。
 *
 * @param totalDuration 本轮全部检测器的累计耗时
 * @param totalFindings 命中总数（所有类型合计）
 * @param failureDetectorCount 抛出异常的检测器数量（不影响 findings 的 detector 会跳过并记录日志）
 * @param countByType PII 类型 → 命中数；未出现的类型不会出现在 Map 中
 * @author ydsz-team
 * @since 26.09.20
 */
public record PiiDetectionSummary(
    Duration totalDuration,
    int totalFindings,
    int failureDetectorCount,
    Map<PiiType, Integer> countByType) {

  /** 通过 compact constructor 包装 countByType 为不可变视图 */
  public PiiDetectionSummary {
    countByType = countByType == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(countByType);
  }

  /** 是否存在任何检测器异常（任一反之为 {@code true}） */
  public boolean hasFailure() {
    return failureDetectorCount > 0;
  }
}
