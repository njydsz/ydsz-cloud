package com.njydsz.common.audit.sharding;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 审计日志分表策略接口
 * <p>
 * 定义分表规则：根据时间动态计算目标表名，避免单表数据膨胀。
 * 框架内置按月（{@link MonthlyShardingStrategy}）、按天（{@link DailyShardingStrategy}）、
 * 按年（{@link YearlyShardingStrategy}）三种实现，业务方可自定义。
 * </p>
 *
 * <p><b>设计要点：</b></p>
 * <ul>
 *   <li>分表键固定为时间戳，命中规则应稳定可预测（避免跨分表数据倾斜）</li>
 *   <li>跨分表查询走 {@link #getTableNamesInRange} 枚举后 UNION ALL 合并</li>
 *   <li>{@link com.njydsz.common.audit.config.AuditProperties#getShardingType()} 决定具体实现</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0
 */
public interface TableShardingStrategy {

    /**
     * 根据时间计算分表名
     *
     * @param baseTableName 基础表名，如 {@code sys_audit_log}
     * @param time          时间，通常取操作时间或创建时间
     * @return 分表后的完整表名
     */
    String getTableName(String baseTableName, LocalDateTime time);

    /**
     * 获取分表类型
     *
     * @return 分表类型枚举
     */
    ShardType getShardType();

    /**
     * 根据时间范围计算涉及的所有分表名
     *
     * @param baseTableName 基础表名
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @return 时间范围内涉及的分表名集合（去重）
     */
    Set<String> getTableNamesInRange(String baseTableName, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 分表类型枚举
     */
    enum ShardType {
        /** 按月分表：表名后缀 yyyyMM，适中粒度，适合中等量级审计 */
        MONTHLY,
        /** 按天分表：表名后缀 yyyyMMdd，粒度细，适合高 QPS 写入场景 */
        DAILY,
        /** 按年分表：表名后缀 yyyy，粒度粗，适合低频审计 */
        YEARLY
    }
}
