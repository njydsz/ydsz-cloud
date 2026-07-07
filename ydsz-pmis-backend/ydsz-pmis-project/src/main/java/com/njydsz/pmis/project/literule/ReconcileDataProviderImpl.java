package com.njydsz.pmis.project.literule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.project.entity.CostAllocationDO;
import com.njydsz.pmis.project.entity.TimeEntryDO;
import com.njydsz.pmis.project.mapper.CostAllocationMapper;
import com.njydsz.pmis.project.mapper.TimeEntryMapper;
import com.njydsz.pmis.literule.spi.ReconcileDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工时与成本对账数据提供者实现（execution 模块）
 *
 * <p>实现 literule 模块的 {@link ReconcileDataProvider} SPI 接口，
 * 通过 {@link TimeEntryMapper} 和 {@link CostAllocationMapper} 查询对账所需数据，
 * 转换为 SPI 定义的 record DTO。
 *
 * <p>说明：
 * <ul>
 *   <li>接口中 projectId 为 String 类型，内部转换为 String initiationId 使用</li>
 *   <li>{@link TimeEntryRecord#billableRate()} 暂为 null（待接入费率卡查询）</li>
 *   <li>{@link CostAllocationRecord#approvedBy()} 暂为 null（CostAllocationDO 无审批人字段）</li>
 *   <li>{@link CostAllocationRecord#allocationDate()} 由 period(YYYY-MM) 解析为当月第一天</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileDataProviderImpl implements ReconcileDataProvider {

    private final TimeEntryMapper timeEntryMapper;
    private final CostAllocationMapper costAllocationMapper;

    /**
     * 获取指定日期范围内某项目的工时明细
     *
     * <p>通过 {@link TimeEntryMapper#selectByInitiationAndDateRange(String, LocalDate, LocalDate)}
     * 查询工时记录，转换为 {@link TimeEntryRecord} 列表。
     *
     * @param projectId 项目 ID（对应立项 initiationId 的字符串形式）
     * @param startDate 开始日期（含）；null 表示不限
     * @param endDate   结束日期（含）；null 表示不限
     * @return 工时记录列表；无数据返回空列表
     */
    @Override
    public List<TimeEntryRecord> listTimeEntries(String projectId, LocalDate startDate, LocalDate endDate) {
        String initiationId = parseInitiationId(projectId);
        if (initiationId == null) {
            return Collections.emptyList();
        }
        List<TimeEntryDO> entries = timeEntryMapper.selectByInitiationAndDateRange(initiationId, startDate, endDate);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        return entries.stream()
                .map(this::toTimeEntryRecord)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定日期范围内某项目的成本分摊明细
     *
     * <p>通过 MyBatis-Plus {@link LambdaQueryWrapper} 按 initiationId 查询成本归集记录，
     * 再按 period(YYYY-MM) 与日期范围进行月级过滤，转换为 {@link CostAllocationRecord} 列表。
     *
     * @param projectId 项目 ID（对应立项 initiationId 的字符串形式）
     * @param startDate 开始日期（含）；null 表示不限
     * @param endDate   结束日期（含）；null 表示不限
     * @return 成本分摊记录列表；无数据返回空列表
     */
    @Override
    public List<CostAllocationRecord> listCostAllocations(String projectId, LocalDate startDate, LocalDate endDate) {
        String initiationId = parseInitiationId(projectId);
        if (initiationId == null) {
            return Collections.emptyList();
        }
        List<CostAllocationDO> costs = costAllocationMapper.selectList(
                new LambdaQueryWrapper<CostAllocationDO>()
                        .eq(CostAllocationDO::getInitiationId, initiationId));
        if (costs == null || costs.isEmpty()) {
            return Collections.emptyList();
        }
        return costs.stream()
                .filter(c -> withinPeriodRange(c, startDate, endDate))
                .map(this::toCostAllocationRecord)
                .collect(Collectors.toList());
    }

    // -------------------- DO -> Record 转换 --------------------

    /**
     * TimeEntryDO 转换为 TimeEntryRecord
     *
     * @param e 工时实体
     * @return 工时记录 DTO
     */
    private TimeEntryRecord toTimeEntryRecord(TimeEntryDO e) {
        return new TimeEntryRecord(
                e.getId(),
                e.getInitiationId() == null ? null : String.valueOf(e.getInitiationId()),
                e.getEmployeeId() == null ? null : String.valueOf(e.getEmployeeId()),
                e.getEntryDate(),
                e.getHours(),
                null, // P2 待接入：需注入 RateCardMapper 查询员工对应费率，填充 billableRate
                e.getStatus(),
                e.getApproverName()
        );
    }

    /**
     * CostAllocationDO 转换为 CostAllocationRecord
     *
     * @param c 成本归集实体
     * @return 成本分摊记录 DTO
     */
    private CostAllocationRecord toCostAllocationRecord(CostAllocationDO c) {
        return new CostAllocationRecord(
                c.getId(),
                c.getInitiationId() == null ? null : String.valueOf(c.getInitiationId()),
                parsePeriodToDate(c.getPeriod()),
                c.getAmount(),
                c.getCostType(),
                c.getSourceType(),
                null // 数据模型限制：CostAllocationDO 无审批人字段，如需审批人信息需先扩展表结构
        );
    }

    // -------------------- 内部工具方法 --------------------

    /**
     * 将 projectId（String）解析为 initiationId（String）
     *
     * <p>当前实现仅做空值与空白校验，原样返回 trim 后的字符串。
     *
     * @param projectId 项目 ID 字符串
     * @return 立项 ID；为空时返回 null
     */
    private String parseInitiationId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return projectId.trim();
    }

    /**
     * 将 period（YYYY-MM）解析为 LocalDate（当月第一天）
     *
     * @param period 期间字符串，如 "2025-06"
     * @return 当月第一天的日期；解析失败返回 null
     */
    private LocalDate parsePeriodToDate(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(period.trim()).atDay(1);
        } catch (Exception e) {
            log.warn("[ReconcileDataProvider] period={} 无法解析为日期", period);
            return null;
        }
    }

    /**
     * 判断成本归集记录的期间是否在指定日期范围内（按月粒度比较）
     *
     * <p>CostAllocationDO 没有精确到天的日期字段，仅有 period(YYYY-MM)，
     * 因此按 YearMonth 粒度与日期范围进行比较。
     *
     * @param c         成本归集实体
     * @param startDate 起始日期（含）；null 表示不限下界
     * @param endDate   结束日期（含）；null 表示不限上界
     * @return 在范围内返回 true；否则返回 false
     */
    private boolean withinPeriodRange(CostAllocationDO c, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return true;
        }
        if (c.getPeriod() == null || c.getPeriod().isBlank()) {
            // 无期间信息时不过滤，保留该记录
            return true;
        }
        try {
            YearMonth periodYM = YearMonth.parse(c.getPeriod().trim());
            if (startDate != null) {
                YearMonth startYM = YearMonth.from(startDate);
                if (periodYM.isBefore(startYM)) {
                    return false;
                }
            }
            if (endDate != null) {
                YearMonth endYM = YearMonth.from(endDate);
                if (periodYM.isAfter(endYM)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("[ReconcileDataProvider] period={} 无法解析，跳过日期过滤", c.getPeriod());
            return true;
        }
    }
}
