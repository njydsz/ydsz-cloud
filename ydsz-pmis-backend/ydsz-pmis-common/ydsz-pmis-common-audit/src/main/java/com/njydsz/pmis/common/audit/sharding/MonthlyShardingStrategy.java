package com.njydsz.pmis.common.audit.sharding;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 按月分表策略
 * <p>
 * 命名规则：{@code baseTableName_yyyyMM}，例如 {@code sys_audit_log → sys_audit_log_202601}。
 * 适用于中等写入量级（每月百万级以下）的审计场景，是默认推荐的分表粒度。
 * </p>
 *
 * <p><b>权衡：</b></p>
 * <ul>
 *   <li>粒度适中，单表数据量可控，DBA 维护成本低</li>
 *   <li>按月归档/清理方便（直接 DROP 老月份表）</li>
 *   <li>不适合高 QPS 大数据量场景（建议改用 {@link DailyShardingStrategy}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public class MonthlyShardingStrategy implements TableShardingStrategy {

    /** 按月格式化器，格式为 yyyyMM */
    private static final DateTimeFormatter MONTHLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 根据时间计算分表名
     *
     * @param baseTableName 基础表名
     * @param time          时间
     * @return 形如 {@code sys_audit_log_202601} 的分表名
     */
    @Override
    public String getTableName(String baseTableName, LocalDateTime time) {
        return baseTableName + "_" + time.format(MONTHLY_FORMATTER);
    }

    /**
     * 获取分表类型
     *
     * @return 固定返回 {@link ShardType#MONTHLY}
     */
    @Override
    public ShardType getShardType() {
        return ShardType.MONTHLY;
    }

    /**
     * 根据时间范围枚举涉及的月份分表
     * <p>按月推进，确保起始月份被正确包含（含 startTime 所在月）。
     *
     * @param baseTableName 基础表名
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 涉及的月份分表名集合，按时间正序
     */
    @Override
    public Set<String> getTableNamesInRange(String baseTableName, LocalDateTime startTime, LocalDateTime endTime) {
        Set<String> tables = new LinkedHashSet<>();
        if (startTime == null || endTime == null) {
            tables.add(baseTableName + "_" + LocalDateTime.now().format(MONTHLY_FORMATTER));
            return tables;
        }
        LocalDateTime current = startTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = endTime;
        while (!current.isAfter(end)) {
            tables.add(getTableName(baseTableName, current));
            current = current.plusMonths(1).withDayOfMonth(1);
        }
        return tables;
    }
}
