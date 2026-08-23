package com.njydsz.literule.server.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工时与成本对账数据提供者接口（SPI）
 *
 * <p>由消费方实现，提供 ReconcileHandler 对账检查所需的数据。 literule 模块通过此接口反转 Mapper 依赖。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface ReconcileDataProvider {

  /**
   * 获取指定日期范围内某项目的工时明细
   *
   * @param projectId 项目 ID
   * @param startDate 开始日期
   * @param endDate 结束日期
   * @return 工时记录列表
   */
  List<TimeEntryRecord> listTimeEntries(String projectId, LocalDate startDate, LocalDate endDate);

  /**
   * 获取指定日期范围内某项目的成本分摊明细
   *
   * @param projectId 项目 ID
   * @param startDate 开始日期
   * @param endDate 结束日期
   * @return 成本分摊记录列表
   */
  List<CostAllocationRecord> listCostAllocations(
      String projectId, LocalDate startDate, LocalDate endDate);

  /**
   * 工时记录 DTO。
   *
   * @param id 记录 ID
   * @param projectId 项目 ID
   * @param userId 用户 ID
   * @param entryDate 工时日期
   * @param hours 工时数
   * @param billableRate 计费费率
   * @param status 状态
   * @param approvedBy 审批人
   */
  record TimeEntryRecord(
      String id,
      String projectId,
      String userId,
      LocalDate entryDate,
      BigDecimal hours,
      BigDecimal billableRate,
      String status,
      String approvedBy) {}

  /**
   * 成本分摊记录 DTO。
   *
   * @param id 记录 ID
   * @param projectId 项目 ID
   * @param allocationDate 分摊日期
   * @param amount 分摊金额
   * @param costType 成本类型
   * @param sourceType 来源类型
   * @param approvedBy 审批人
   */
  record CostAllocationRecord(
      String id,
      String projectId,
      LocalDate allocationDate,
      BigDecimal amount,
      String costType,
      String sourceType,
      String approvedBy) {}
}
