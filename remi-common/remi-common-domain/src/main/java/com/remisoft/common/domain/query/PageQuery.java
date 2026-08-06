package com.remisoft.common.domain.query;

import static lombok.AccessLevel.PROTECTED;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.remisoft.common.json.annotation.JsonIgnore;
import com.remisoft.common.core.constant.PageConstants;
import com.remisoft.common.domain.config.DomainProperties;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
 * // 通过 Factory 创建（推荐）
 * @Autowired
 * private PageQueryFactory pageQueryFactory;
 * PageQuery query = pageQueryFactory.create(1, 10);
 *
 * // 添加排序（正则校验由拦截器统一处理）
 * query.addOrder("created_at", true);
 * query.addDescOrder("updated_at");
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @since 1.7.0 职责精简：移除 SQL 安全处理逻辑，下沉至拦截器层
 * @see PageQueryFactory
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
     * 运行时配置引用（实例级，由 PageQueryFactory 注入）。
     *
     * @since 1.7.0
     */
    @JsonIgnore
    private DomainProperties runtimeProperties;

    /**
     * 注入运行时配置（实例级）。
     *
     * @param properties 领域配置实例
     * @since 1.7.0
     */
    public void setRuntimeProperties(DomainProperties properties) {
        this.runtimeProperties = properties;
    }

    /**
     * 获取运行时配置。
     *
     * @return DomainProperties 配置实例，可能为 null
     * @since 1.7.0
     */
    public DomainProperties getRuntimeProperties() {
        return runtimeProperties;
    }

    /**
     * 获取运行时分页配置（供拦截器使用）。
     *
     * @return DomainProperties 配置实例，可能为 null
     * @since 1.7.0
     */
    DomainProperties getProperties() {
        return runtimeProperties;
    }

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
     */
    private String cursor;

    /**
     * 游标方向（默认 NEXT）。
     */
    @Builder.Default
    private CursorDirection cursorDirection = CursorDirection.NEXT;

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
     */
    public PageQuery addAscOrder(String column) {
        return addOrder(column, true);
    }

    /**
     * 添加降序排序项。
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
     * 设置排序项列表。
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
     */
    public PageQuery clearOrders() {
        if (orderItems != null) {
            orderItems.clear();
        }
        return this;
    }

    /**
     * 获取排序项数量。
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
     */
    public long getOffsetLong() {
        if (pageNum == null || pageSize == null) {
            return 0L;
        }
        return PageConstants.calcOffset(pageNum, pageSize);
    }

    /**
     * 获取实际每页大小。
     */
    public int getEffectivePageSize() {
        return PageConstants.normalizePageSize(this.pageSize);
    }

    /**
     * 获取实际页码。
     */
    @JsonIgnore
    public int getEffectivePageNum() {
        return Math.max(this.pageNum != null ? this.pageNum : 1, 1);
    }

    /**
     * 计算起始行号（从1开始）。
     */
    public int getStartRow() {
        return getOffset() + 1;
    }

    /**
     * 计算结束行号。
     */
    public long getEndRow() {
        return getOffsetLong() + getEffectivePageSize();
    }

    /**
     * 判断是否有上一页。
     */
    public boolean hasPrevious() {
        return getEffectivePageNum() > 1;
    }

    /**
     * 判断是否有下一页。
     */
    public boolean hasNext(long total) {
        return (long) getOffset() + getEffectivePageSize() < total;
    }

    /**
     * 判断是否启用游标分页模式。
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
                ", cursorDirection=" + cursorDirection +
                ", searchKey='" + getSearchKey() + '\'' +
                '}';
    }
}
