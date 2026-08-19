package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 单日登录成功率统计。
 *
 * <p>用于登录成功率趋势图，展示指定日期范围内的成功/失败分布。
 *
 * @param date 统计日期
 * @param successCount 登录成功次数
 * @param failCount 登录失败次数
 * @param successRate 成功率（0.0-1.0）
 * @author ydsz-team
 * @since 1.6.0
 */
public record LoginSuccessRateVO(
    LocalDate date,
    long successCount,
    long failCount,
    double successRate) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;
}
