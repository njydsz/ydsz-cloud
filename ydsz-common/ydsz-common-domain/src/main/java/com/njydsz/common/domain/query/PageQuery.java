package com.njydsz.common.domain.query;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.json.annotation.JsonIgnore;

import static lombok.AccessLevel.PROTECTED;

/**
 * 分页查询参数封装类（v1.7.0 职责精简版）。
 *
 * <p>用于封装分页查询的请求参数，包括页码、页大小、排序信息和通用搜索条件。
 *
 * <p><b>v1.7.0 职责变更：</b>参考互联网大厂规范（Spring Data Pageable、Axon Framework），
 * 将 SQL 安全相关职责下沉至 {@code SafeQueryInnerInterceptor}：
 * <ul>
 *   <li>LIKE 转义 → 业务层/ResultMapper 处理</li>
 *   <li>ORDER BY SQL 拼接 → {@code SafeQueryInnerInterceptor}</li>
 *   <li>深度分页评估 → {@code SafeQueryInnerInterceptor}</li>
 * </ul>
 *
 * <p>此类现在仅负责<b>承载查询参数</b>，遵循单一职责原则（SRP）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 直接构造（推荐，交由 MyBatis 参数绑定 / 子类继承）
 * PageQuery query = PageQuery.builder()
 *         .pageNum(1).pageSize(10)
 *         .build();
 *
 * // 添加排序（安全校验由 SafeQueryInnerInterceptor 统一处理）
 * query.addOrder("created_at", true);
 * query.addDescOrder("updated_at");
 *
 * // 深度分页风险评估（阈值来自 DomainProperties，默认 10000 / 50000）
 * DeepPaginationRisk risk = query.assessPaginationRisk();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.7.0 职责精简：移除 SQL 安全处理逻辑，下沉至拦截器层
 * @since 1.8.0 移除失效的 PageQueryFactory 运行时注入，深度分页评估改为 assessPaginationRisk()
 * @see DeepPaginationRisk
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = PROTECTED)
public class PageQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 搜索关键字最大长度（仅做截断，不做转义）。
     *
     * <p>v1.7.0 变更：移除 LIKE 转义逻辑，转义由业务层或 MyBatis ResultMapper 处理。
     */
    public static final int MAX_SEARCH_KEY_LENGTH = 200;

    /**
     * 当前页码（从1开始）。
     */
    @NotNull(message = "pageNum当前页不能为空")
    @Min(value = 1, message = "pageNum最小值为1")
    @Builder.Default
    private Integer pageNum = 1;

    /**
     * 每页显示条数。
     */
    @NotNull(message = "pageSize页大小不能为空")
    @Min(value = 1, message = "pageSize最小值为1")
    @Max(value = PageConstants.MAX_PAGE_SIZE, message = "pageSize最大值为" + PageConstants.MAX_PAGE_SIZE)
    @Builder.Default
    private Integer pageSize = PageConstants.DEFAULT_PAGE_SIZE;

    /**
     * 排序项列表（结构化 OrderItem）。
     */
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * 游标分页游标值（可选，启用 seek 模式）。
     *
     * <p>与 offset 分页互斥：当 cursor 非空时，业务层应按
     * {@code WHERE id > :cursor} 模式查询，跳过 offset 扫描。
     */
    private String cursor;

    // ======================== 排序操作 ========================

    /**
     * 添加排序项（不再做内联校验，安全由 SafeQueryInnerInterceptor 统一处理）。
     *
     * @param column 字段名
     * @param isAsc  是否升序
     * @return 当前对象，支持链式调用
     * @since 1.7.0 移除内联校验逻辑
     */
    public PageQuery addOrder(String column, boolean isAsc) {
        if (column != null && !column.isBlank()) {
            ensureOrderItems();
            orderItems.add(OrderItem.of(column, isAsc));
        }
        return this;
    }

    /**
     * 添加升序排序项。
     *
     * @param column 字段名
     * @return 当前对象，支持链式调用
     */
    public PageQuery addAscOrder(String column) {
        return addOrder(column, true);
    }

    /**
     * 添加降序排序项。
     *
     * @param column 字段名
     * @return 当前对象，支持链式调用
     */
    public PageQuery addDescOrder(String column) {
        return addOrder(column, false);
    }

    /**
     * 批量添加排序项。
     *
     * @param items 排序项数组
     * @return 当前对象，支持链式调用
     */
    public PageQuery addOrders(OrderItem... items) {
        if (items != null) {
            ensureOrderItems();
            for (OrderItem item : items) {
                if (item != null && item.getColumn() != null) {
                    this.orderItems.add(OrderItem.of(item.getColumn(),
                            item.getDirection() == OrderItem.Direction.ASC));
                }
            }
        }
        return this;
    }

    /**
     * 设置排序项列表（过滤 null 与空列名项）。
     *
     * @param orderItems 排序项列表（可为 null）
     * @return 当前对象，支持链式调用
     */
    public PageQuery setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = new ArrayList<>();
        if (orderItems != null) {
            for (OrderItem item : orderItems) {
                if (item != null && item.getColumn() != null) {
                    this.orderItems.add(OrderItem.of(item.getColumn(),
                            item.getDirection() == OrderItem.Direction.ASC));
                }
            }
        }
        return this;
    }

    /**
     * 清空排序项。
     *
     * @return 当前对象，支持链式调用
     */
    public PageQuery clearOrders() {
        if (orderItems != null) {
            orderItems.clear();
        }
        return this;
    }

    /**
     * 获取排序项数量。
     *
     * @return 排序项数量（无排序项时返回 0）
     */
    @JsonIgnore
    public int getOrderCount() {
        return orderItems != null ? orderItems.size() : 0;
    }

    /**
     * 惰性初始化排序列表。
     */
    private void ensureOrderItems() {
        if (orderItems == null) {
            orderItems = new ArrayList<>();
        }
    }

    // ======================== 分页计算 ========================

    /**
     * 获取偏移量（int 类型）。
     *
     * @return 分页偏移量（LIMIT 的 offset）
     * @throws ArithmeticException 当计算结果超过 Integer.MAX_VALUE 时抛出
     */
    public int getOffset() {
        long offset = getOffsetLong();
        if (offset > Integer.MAX_VALUE) {
            throw new ArithmeticException(
                "Page offset overflow: pageNum=" + pageNum + ", pageSize=" + pageSize +
                ", calculated offset=" + offset + " exceeds Integer.MAX_VALUE");
        }
        return (int) offset;
    }

    /**
     * 获取偏移量（long 类型，无溢出风险）。
     *
     * @return 分页偏移量（long）；pageNum/pageSize 为 null 时返回 0
     */
    public long getOffsetLong() {
        if (pageNum == null || pageSize == null) {
            return 0L;
        }
        return PageConstants.calcOffset(pageNum, pageSize);
    }

    /**
     * 获取实际每页大小。
     *
     * @return 归一化后的页大小（null/<1 取默认值，超上限截断）
     */
    public int getEffectivePageSize() {
        return PageConstants.normalizePageSize(this.pageSize);
    }

    /**
     * 获取实际页码（委托 {@link PageConstants#normalizePageNum} 统一归一化）。
     *
     * <p>归一化规则：null 或小于 1 → 返回 1（{@link PageConstants#DEFAULT_PAGE_NUM}）。</p>
     *
     * @return 归一化后的页码（从 1 开始）
     */
    @JsonIgnore
    public int getEffectivePageNum() {
        return PageConstants.normalizePageNum(this.pageNum);
    }

    /**
     * 计算起始行号（从1开始）。
     *
     * @return 起始行号
     */
    public int getStartRow() {
        return getOffset() + 1;
    }

    /**
     * 计算结束行号。
     *
     * @return 结束行号（含当前页最后一行）
     */
    public long getEndRow() {
        return getOffsetLong() + getEffectivePageSize();
    }

    /**
     * 判断是否有上一页。
     *
     * @return 当前页大于 1 时返回 true
     */
    public boolean hasPrevious() {
        return getEffectivePageNum() > 1;
    }

    /**
     * 判断是否有下一页。
     *
     * @param total 总记录数
     * @return 当前页未达末页时返回 true
     */
    public boolean hasNext(long total) {
        return (long) getOffset() + getEffectivePageSize() < total;
    }

    // ======================== 深度分页风险评估 ========================

    /**
     * 评估当前分页查询的深度分页风险（使用默认阈值 10000 / 50000）。
     *
     * <p>基于 {@link #getOffsetLong()} 计算 offset，再委托 {@link DeepPaginationRisk#assess(long)} 判定。
     * 业务层 / 拦截层可据此发出告警或拒绝执行，防止慢查询拖垮数据库。
     *
     * <p><b>纯函数设计：</b>评估不产生副作用、不做结果缓存。offset 计算与阈值判定开销极小，
     * 缓存反而引入可变状态（污染 equals/hashCode、与"阈值 0 表示关闭"语义冲突），故 v1.9.0 起移除缓存。
     *
     * @return 风险等级（SAFE / WARN / REJECT）
     * @since 1.8.0
     * @since 1.9.0 移除结果缓存，退化为纯函数
     */
    public DeepPaginationRisk assessPaginationRisk() {
        return DeepPaginationRisk.assess(getOffsetLong());
    }

    /**
     * 评估当前分页查询的深度分页风险（使用指定阈值）。
     *
     * <p>阈值契约：{@code rejectThreshold >= warnThreshold >= 0}，非法阈值由
     * {@link DeepPaginationRisk#assess(long, long, long)} 校验。典型调用：
     * <pre>{@code
     * DomainProperties.Page page = domainProperties.getPage();
     * DeepPaginationRisk risk = query.assessPaginationRisk(
     *         page.getCursorWarningThreshold(), page.getCursorRejectThreshold());
     * }</pre>
     *
     * @param warnThreshold   警告阈值
     * @param rejectThreshold 拒绝阈值
     * @return 风险等级（SAFE / WARN / REJECT）
     * @throws IllegalArgumentException 当阈值非法（负数或 reject < warn）时
     * @since 1.8.0
     * @since 1.9.0 移除结果缓存，退化为纯函数
     */
    public DeepPaginationRisk assessPaginationRisk(long warnThreshold, long rejectThreshold) {
        return DeepPaginationRisk.assess(getOffsetLong(), warnThreshold, rejectThreshold);
    }

    /**
     * 判断是否启用游标分页模式。
     *
     * @return cursor 非空且非空白时返回 true
     */
    @JsonIgnore
    public boolean isCursorBased() {
        return cursor != null && !cursor.isBlank();
    }

    @Override
    public String toString() {
        return "PageQuery{" +
                "pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", orderItems=" + orderItems +
                ", cursor='" + cursor + '\'' +
                ", searchKey='" + getSearchKey() + '\'' +
                '}';
    }
}
