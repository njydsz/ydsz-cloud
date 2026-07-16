package com.njydsz.common.audit.sharding;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 按年分表策略
 * <p>
 * 命名规则：{@code baseTableName_yyyy}，例如 {@code sys_audit_log → sys_audit_log_2026}。
 * 适用于写入量小、查询以年度为单位的审计场景。
 * </p>
 *
 * <p><b>权衡：</b></p>
 * <ul>
 *   <li>分表数量少，维护成本最低</li>
 *   <li>单表数据量可能较大，索引效率下降</li>
 *   <li>单表归档/清理粒度粗，删除可能引起长时间锁表</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0
 */
public class YearlyShardingStrategy implements TableShardingStrategy {

    /** 按年格式化器，格式为 yyyy */
    private static final DateTimeFormatter YEARLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy");

    /**
     * 根据时间计算分表名
     *
     * @param baseTableName 基础表名
     * @param time          时间
     * @return 形如 {@code sys_audit_log_2026} 的分表名
     */
    @Override
    public String getTableName(String baseTableName, LocalDateTime time) {
        return baseTableName + "_" + time.format(YEARLY_FORMATTER);
    }

    /**
     * 获取分表类型
     *
     * @return 固定返回 {@link ShardType#YEARLY}
     */
    @Override
    public ShardType getShardType() {
        return ShardType.YEARLY;
    }

    /**
     * 根据时间范围枚举涉及的年份分表
     *
     * @param baseTableName 基础表名
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 涉及的年份分表名集合，按时间正序
     */
    @Override
    public Set<String> getTableNamesInRange(String baseTableName, LocalDateTime startTime, LocalDateTime endTime) {
        Set<String> tables = new LinkedHashSet<>();
        if (startTime == null || endTime == null) {
            tables.add(baseTableName + "_" + LocalDateTime.now().format(YEARLY_FORMATTER));
            return tables;
        }
        LocalDateTime current = startTime.withMonth(1).withDayOfYear(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = endTime;
        while (!current.isAfter(end)) {
            tables.add(getTableName(baseTableName, current));
            current = current.plusYears(1).withDayOfYear(1);
        }
        return tables;
    }
}
