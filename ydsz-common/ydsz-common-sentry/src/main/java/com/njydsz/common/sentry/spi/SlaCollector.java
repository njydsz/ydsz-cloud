package com.njydsz.common.sentry.spi;

import com.njydsz.common.sentry.domain.SlaDefinition;

/**
 * SLA 指标采集器 SPI
 *
 * <p>业务模块实现此接口以接入 SLA 指标采集。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SlaCollector {

  /**
   * 注册 SLA 定义
   *
   * @param definition SLA 定义
   */
  void register(SlaDefinition definition);

  /**
   * 记录 SLA 执行结果
   *
   * @param name SLA 名称
   * @param stepName 步骤名
   * @param tookMillis 耗时（毫秒）
   * @param success 是否成功
   */
  void record(String name, String stepName, long tookMillis, boolean success);

  /**
   * 记录 SLA 整体执行结果
   *
   * @param name SLA 名称
   * @param tookMillis 总耗时（毫秒）
   * @param success 是否成功
   */
  void recordTotal(String name, long tookMillis, boolean success);

  /**
   * 判断采集器是否可用。
   * @return 判断采集器是否可用
   */
  boolean isAvailable();
}
