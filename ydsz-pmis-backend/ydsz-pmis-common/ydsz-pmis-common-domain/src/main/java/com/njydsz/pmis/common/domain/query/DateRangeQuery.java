package com.njydsz.pmis.common.domain.query;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static lombok.AccessLevel.PROTECTED;

/**
 * 时间范围查询对象
 *
 * <p>专门用于时间范围查询的场景，提供灵活的时间区间配置。
 * 支持日期、时间、日期时间等多种时间类型。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 基础用法
 * DateRangeQuery query = DateRangeQuery.of(
 *     LocalDate.of(2024, 1, 1),
 *     LocalDate.of(2024, 12, 31)
 * );
 *
 * // 指定时间字段
 * DateRangeQuery query = DateRangeQuery.builder()
 *     .dateField("created_at")
 *     .startDate(LocalDate.of(2024, 1, 1))
 *     .endDate(LocalDate.of(2024, 12, 31))
 *     .build();
 *
 * // 使用预定义范围
 * DateRangeQuery query = DateRangeQuery.today();
 * DateRangeQuery query = DateRangeQuery.thisWeek();
 * DateRangeQuery query = DateRangeQuery.thisMonth();
 * }</pre>
 *
 * <p><b>时间字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>dateField</td><td>String</td><td>时间字段名（数据库列名）</td></tr>
 *   <tr><td>startDate</td><td>LocalDate</td><td>开始日期</td></tr>
 *   <tr><td>endDate</td><td>LocalDate</td><td>结束日期</td></tr>
 *   <tr><td>startTime</td><td>LocalDateTime</td><td>开始时间（精确到秒）</td></tr>
 *   <tr><td>endTime</td><td>LocalDateTime</td><td>结束时间（精确到秒）</td></tr>
 *   <tr><td>includeTime</td><td>Boolean</td><td>是否包含时间部分</td></tr>
 * </table>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@SuperBuilder
@NoArgsConstructor(access = PROTECTED)
@EqualsAndHashCode(callSuper = true)
public class DateRangeQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 时间字段名
     *
     * <p>对应数据库的列名，如 created_at、updated_at 等。
     * 用于动态构建 SQL 查询条件。
     */
    private String dateField;

    /**
     * 开始日期
     *
     * <p>查询的起始日期（包含）。
     * 当 includeTime=false 时，自动转换为当天的 00:00:00。
     */
    private LocalDate startDate;

    /**
     * 结束日期
     *
     * <p>查询的结束日期（包含）。
     * 当 includeTime=false 时，自动转换为当天的 23:59:59。
     */
    private LocalDate endDate;

    /**
     * 是否包含时间部分
     *
     * <p>控制时间范围的精度：
     * <ul>
     *   <li>true - 精确到秒，使用 startTime/endTime</li>
     *   <li>false - 精确到天，使用 startDate/endDate（默认）</li>
     * </ul>
     */
    @Builder.Default
    private Boolean includeTime = false;

    /**
     * 创建今天的时间范围查询
     *
     * @return 今天的时间范围查询对象
     */
    public static DateRangeQuery today() {
        LocalDate today = LocalDate.now();
        return DateRangeQuery.builder()
                .startDate(today)
                .endDate(today)
                .includeTime(false)
                .build();
    }

    /**
     * 创建本周的时间范围查询
     *
     * @return 本周的时间范围查询对象
     */
    public static DateRangeQuery thisWeek() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        return DateRangeQuery.builder()
                .startDate(startOfWeek)
                .endDate(endOfWeek)
                .includeTime(false)
                .build();
    }

    /**
     * 创建本月的时间范围查询
     *
     * @return 本月的时间范围查询对象
     */
    public static DateRangeQuery thisMonth() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        return DateRangeQuery.builder()
                .startDate(startOfMonth)
                .endDate(endOfMonth)
                .includeTime(false)
                .build();
    }

    /**
     * 创建本年的时间范围查询
     *
     * @return 本年的时间范围查询对象
     */
    public static DateRangeQuery thisYear() {
        LocalDate today = LocalDate.now();
        LocalDate startOfYear = today.withDayOfYear(1);
        LocalDate endOfYear = today.withDayOfYear(today.lengthOfYear());
        return DateRangeQuery.builder()
                .startDate(startOfYear)
                .endDate(endOfYear)
                .includeTime(false)
                .build();
    }

    /**
     * 创建最近 N 天的时间范围查询
     *
     * @param days 天数
     * @return 最近 N 天的时间范围查询对象
     */
    public static DateRangeQuery lastDays(int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1);
        return DateRangeQuery.builder()
                .startDate(startDate)
                .endDate(today)
                .includeTime(false)
                .build();
    }

    /**
     * 创建指定日期范围的时间范围查询
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 时间范围查询对象
     */
    public static DateRangeQuery of(LocalDate startDate, LocalDate endDate) {
        return DateRangeQuery.builder()
                .startDate(startDate)
                .endDate(endDate)
                .includeTime(false)
                .build();
    }

    /**
     * 创建指定时间范围的时间范围查询
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 时间范围查询对象
     */
    public static DateRangeQuery of(LocalDateTime startTime, LocalDateTime endTime) {
        return DateRangeQuery.builder()
                .startTime(startTime)
                .endTime(endTime)
                .includeTime(true)
                .build();
    }

    /**
     * 获取开始时间（转换为 LocalDateTime）
     *
     * <p>如果 includeTime=true，返回 startTime；
     * 否则将 startDate 转换为当天的 00:00:00。
     *
     * @return 开始时间
     */
    public LocalDateTime getEffectiveStartTime() {
        if (includeTime != null && includeTime) {
            return getStartTime();
        }
        if (startDate != null) {
            return LocalDateTime.of(startDate, LocalTime.MIN);
        }
        return null;
    }

    /**
     * 获取结束时间（转换为 LocalDateTime）
     *
     * <p>如果 includeTime=true，返回 endTime；
     * 否则将 endDate 转换为当天的 23:59:59。
     *
     * @return 结束时间
     */
    public LocalDateTime getEffectiveEndTime() {
        if (includeTime != null && includeTime) {
            return getEndTime();
        }
        if (endDate != null) {
            return LocalDateTime.of(endDate, LocalTime.MAX);
        }
        return null;
    }

    /**
     * 判断是否有时间范围条件
     *
     * @return 有任一时间边界返回 true
     */
    public boolean hasDateRange() {
        return startDate != null || endDate != null || getStartTime() != null || getEndTime() != null;
    }

    /**
     * 生成 SQL WHERE 条件片段
     *
     * <p>根据配置生成时间范围的 SQL 条件，如：
     * <ul>
     *   <li>created_at >= '2024-01-01 00:00:00' AND created_at <= '2024-12-31 23:59:59'</li>
     * </ul>
     *
     * <p><b>注意：</b>此方法仅用于生成 SQL 片段，实际使用时需要配合 MyBatis 参数绑定。
     *
     * @return SQL WHERE 条件片段，如果没有时间范围条件则返回空字符串
     */
    public String toSqlCondition() {
        if (!hasDateRange() || dateField == null || dateField.isBlank()) {
            return "";
        }

        StringBuilder condition = new StringBuilder();
        LocalDateTime start = getEffectiveStartTime();
        LocalDateTime end = getEffectiveEndTime();

        if (start != null && end != null) {
            condition.append(dateField).append(" >= ? AND ").append(dateField).append(" <= ?");
        } else if (start != null) {
            condition.append(dateField).append(" >= ?");
        } else if (end != null) {
            condition.append(dateField).append(" <= ?");
        }

        return condition.toString();
    }
}
