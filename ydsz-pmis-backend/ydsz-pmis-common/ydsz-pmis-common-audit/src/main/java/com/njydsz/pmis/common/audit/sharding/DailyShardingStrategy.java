package com.njydsz.pmis.common.audit.sharding;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 按天分表策略
 * <p>
 * 命名规则：{@code baseTableName_yyyyMMdd}，例如 {@code sys_audit_log → sys_audit_log_20260101}。
 * 适用于高写入量级（日均百万级以上）的审计场景。
 * </p>
 *
 * <p><b>权衡：</b></p>
 * <ul>
 *   <li>粒度细，单表容量小，索引效率高</li>
 *   <li>单日写入性能更稳定（避免热点月份表）</li>
 *   <li>跨天/跨周查询会涉及多张表，UNION ALL 成本相对高</li>
 *   <li>分表数量多，DBA 维护成本上升</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class DailyShardingStrategy implements TableShardingStrategy {

    /** 按天格式化器，格式为 yyyyMMdd */
    private static final DateTimeFormatter DAILY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 根据时间计算分表名
     *
     * @param baseTableName 基础表名
     * @param time          时间
     * @return 形如 {@code sys_audit_log_20260101} 的分表名
     */
    @Override
    public String getTableName(String baseTableName, LocalDateTime time) {
        return baseTableName + "_" + time.format(DAILY_FORMATTER);
    }

    /**
     * 获取分表类型
     *
     * @return 固定返回 {@link ShardType#DAILY}
     */
    @Override
    public ShardType getShardType() {
        return ShardType.DAILY;
    }

    /**
     * 根据时间范围枚举涉及的日期分表
     * <p>按天推进，确保起始日期被正确包含。
     *
     * @param baseTableName 基础表名
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 涉及的日期分表名集合，按时间正序
     */
    @Override
    public Set<String> getTableNamesInRange(String baseTableName, LocalDateTime startTime, LocalDateTime endTime) {
        Set<String> tables = new LinkedHashSet<>();
        if (startTime == null || endTime == null) {
            tables.add(baseTableName + "_" + LocalDateTime.now().format(DAILY_FORMATTER));
            return tables;
        }
        LocalDateTime current = startTime.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = endTime;
        while (!current.isAfter(end)) {
            tables.add(getTableName(baseTableName, current));
            current = current.plusDays(1);
        }
        return tables;
    }
}
