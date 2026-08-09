package com.njydsz.common.domain.query;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

/**
 * 查询对象基类型
 *
 * <p>所有查询参数对象的顶层基类，提供序列化支持和通用查询字段。
 * 子类可通过 {@link SuperBuilder} 继承 Builder 能力，实现链式构建查询参数。
 *
 * <p><b>继承体系：</b>
 * <pre>
 * BaseQuery (基类)
 *     └── PageQuery (分页查询)
 * </pre>
 *
 * <p><b>通用字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>searchKey</td><td>String</td><td>模糊搜索关键字</td></tr>
 *   <tr><td>status</td><td>String</td><td>状态过滤</td></tr>
 *   <tr><td>startDateTime</td><td>LocalDateTime</td><td>开始时间</td></tr>
 *   <tr><td>endDateTime</td><td>LocalDateTime</td><td>结束时间</td></tr>
 *   <tr><td>tenantId</td><td>String</td><td>租户ID</td></tr>
 * </table>
 *
 * <p><b>v1.8.0 变更：</b>移除 {@code ascending} 字段，排序方向统一使用
 * {@link OrderItem} 结构化表达（{@link PageQuery#addOrder(String, boolean)}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.8.0 移除 ascending 字段，统一使用 OrderItem
 *
 */
@Data
@SuperBuilder
@NoArgsConstructor(access = PROTECTED)
public class BaseQuery implements Serializable {

    /**
     * 序列化版本
     */
    private static final long serialVersionUID = 1L;

    /**
     * 模糊搜索关键字
     *
     * <p>用于对多个字段进行模糊匹配搜索。
     * 具体搜索字段由业务子类或 MyBatis XML 定义。
     */
    private String searchKey;

    /**
     * 状态过滤
     *
     * <p>用于过滤数据状态，子类可按需覆盖为具体业务状态枚举值。
     * 默认值为空，由各子类根据业务语义自行定义。
     */
    private String status;

    /**
     * 开始时间
     *
     * <p>用于时间范围查询的起始时间，使用 {@link LocalDateTime} 类型。
     */
    private LocalDateTime startDateTime;

    /**
     * 结束时间
     *
     * <p>用于时间范围查询的结束时间，使用 {@link LocalDateTime} 类型。
     */
    private LocalDateTime endDateTime;

    /**
     * 租户ID
     *
     * <p>用于多租户场景下的数据隔离。
     * 通常在 SaaS 应用中使用，确保不同租户的数据互不干扰。
     */
    private String tenantId;

    /**
     * 判断是否有时间范围条件
     *
     * @return 有任一时间边界返回 true
     */
    public boolean hasTimeRange() {
        return startDateTime != null || endDateTime != null;
    }

    /**
     * 判断时间范围是否合法
     *
     * <p>当 startDateTime 和 endDateTime 均不为 null 时，
     * 检查 startDateTime 不晚于 endDateTime。任一为 null 时视为合法。
     *
     * @return 时间范围合法返回 true
     * @since 1.2.0
     */
    public boolean isValidTimeRange() {
        return startDateTime == null || endDateTime == null
                || !startDateTime.isAfter(endDateTime);
    }

    /**
     * 校验时间范围，非法时抛出异常
     *
     * @throws IllegalArgumentException 当 startDateTime 晚于 endDateTime 时
     * @since 1.2.0
     */
    public void validateTimeRange() {
        if (!isValidTimeRange()) {
            throw new IllegalArgumentException(
                "startDateTime must not be after endDateTime: start=" + startDateTime
                + ", end=" + endDateTime);
        }
    }

    /**
     * 判断是否有搜索关键字
     *
     * @return 有搜索关键字返回 true
     */
    public boolean hasSearchKey() {
        return searchKey != null && !searchKey.isBlank();
    }

    /**
     * 判断是否有状态过滤
     *
     * @return 有状态过滤返回 true
     */
    public boolean hasStatus() {
        return status != null;
    }

    /**
     * 从枚举设置状态过滤
     *
     * <p>将枚举名称设置为 status 字段值。
     *
     * @param statusEnum 状态枚举
     * @since 1.2.0
     */
    public void statusEnum(Enum<?> statusEnum) {
        this.status = statusEnum != null ? statusEnum.name() : null;
    }
}
