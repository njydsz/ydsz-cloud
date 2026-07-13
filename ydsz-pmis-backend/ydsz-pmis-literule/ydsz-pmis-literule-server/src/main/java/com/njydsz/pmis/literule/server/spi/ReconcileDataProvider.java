package com.njydsz.pmis.literule.server.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工时与成本对账数据提供者接口（SPI）
 *
 * <p>由消费方实现，提供 ReconcileHandler 对账检查所需的数据。
 * literule 模块通过此接口反转 Mapper 依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ReconcileDataProvider {

    /**
     * 获取指定日期范围内某项目的工时明细
     *
     * @param projectId 项目 ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 工时记录列表
     */
    List<TimeEntryRecord> listTimeEntries(String projectId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取指定日期范围内某项目的成本分摊明细
     *
     * @param projectId 项目 ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 成本分摊记录列表
     */
    List<CostAllocationRecord> listCostAllocations(String projectId, LocalDate startDate, LocalDate endDate);

    /**
     * 工时记录 DTO
     *
     * @author ydsz-pmis-team
     */
    record TimeEntryRecord(
            String id,
            String projectId,
            String userId,
            LocalDate entryDate,
            BigDecimal hours,
            BigDecimal billableRate,
            String status,
            String approvedBy
    ) {}

    /**
     * 成本分摊记录 DTO
     *
     * @author ydsz-pmis-team
     */
    record CostAllocationRecord(
            String id,
            String projectId,
            LocalDate allocationDate,
            BigDecimal amount,
            String costType,
            String sourceType,
            String approvedBy
    ) {}
}
