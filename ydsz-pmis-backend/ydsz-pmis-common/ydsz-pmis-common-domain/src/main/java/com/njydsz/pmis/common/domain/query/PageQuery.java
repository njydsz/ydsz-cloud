package com.njydsz.pmis.common.domain.query;

import static lombok.AccessLevel.PROTECTED;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.njydsz.pmis.common.json.annotation.YdszJsonField;
import com.njydsz.pmis.common.core.constant.PageConstants;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
/**
 * 分页查询参数封装。
 *
 * <p>用于封装分页查询的请求参数，包括页码、页大小、排序信息和通用搜索条件。
 * 支持通过 validation 注解进行参数校验，防止非法参数传入。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>不可变模式：默认配置不可变，防止意外修改</li>
 *   <li>流式API：支持链式调用，提升代码可读。</li>
 *   <li>参数校验：内置参数校验，防止非法参数</li>
 * </ul>
 *
 * <p><b>与 {@link com.njydsz.pmis.common.core.request.PageRequest} 的区别：</b>
 * <ul>
 *   <li>{@code PageRequest} 位于 core 模块，用。HTTP API 层，分页字段为 {@code Long} 类型，与 MyBatis-Plus {@code Page<T>} 对齐</li>
 *   <li>{@code PageQuery} 位于 domain 模块，用。Service/Repository 层，分页字段为 {@code Integer} 类型，并集成搜索/过滤/排序白名单等业务能力</li>
 *   <li>两者共与 {@link PageConstants} 中的默认值与上限，避免出现不一致的分页约束</li>
 * </ul>
 *
 * <p><b>字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>默认。</th><th>说明</th></tr>
 *   <tr><td>pageNum</td><td>Integer</td><td>1</td><td>当前页码，从1开。</td></tr>
 *   <tr><td>pageSize</td><td>Integer</td><td>{@link PageConstants#DEFAULT_PAGE_SIZE}</td><td>每页记录。</td></tr>
 *   <tr><td>orderItems</td><td>List&lt;String&gt;</td><td>[]</td><td>排序项列。</td></tr>
 *   <tr><td>ascending</td><td>Boolean</td><td>true</td><td>是否升序</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 基础用法
 * PageQuery query = PageQuery.of(1, 10);
 *
 * // 链式调用
 * PageQuery query = PageQuery.builder()
 *     .pageNum(1)
 *     .pageSize(10)
 *     .searchKey("admin")
 *     .build();
 *
 * // 添加排序
 * query.addOrder("created_at", true);
 *
 * // 获取偏移。
 * int offset = query.getOffset();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = PROTECTED)
public class PageQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 安全字段名校验正。
     */
    private static final Pattern SAFE_COLUMN_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");

    /**
     * 搜索关键字最大长。
     *
     * <p>防止超长关键字导致性能问题或潜在攻击。
     */
    public static final int MAX_SEARCH_KEY_LENGTH = 200;

    /**
     * 当前页码
     *
     * <p>。开始计数，表示第几页数据。
     * 最小值为1，小。时自动修正为1。
     */
    @NotNull(message = "pageNum当前页不能为空
    @Min(value = 1, message = "pageNum最小值为1")
    @Builder.Default
    private Integer pageNum = 1;

    /**
     * 每页显示条数
     *
     * <p>控制每页返回的记录数量。
     * 建议设置上限，防止查询数据量过大。
     * 最大值为 {@link PageConstants#MAX_PAGE_SIZE}。
     */
    @NotNull(message = "pageSize页大小不能为空
    @Min(value = 1, message = "pageSize最小值为1")
    @Max(value = PageConstants.MAX_PAGE_SIZE, message = "pageSize最大值为" + PageConstants.MAX_PAGE_SIZE)
    @Builder.Default
    private Integer pageSize = PageConstants.DEFAULT_PAGE_SIZE;

    /**
     * 排序字段列表
     *
     * <p>格式: column1 asc, column2 desc
     * 支持多个排序项，按添加顺序依次排序。
     * <p>注意：通过 {@link #addOrder(String, boolean)} 添加的排序项会经过安全校验，
     * 直接通过 setter 设置的排序项与 {@link #getOrderSql()} 时也会进行二次校验。
     *
     * <p>orderBy 。ascending 字段继承与 {@link BaseQuery}。
     * 本类通过覆写 {@link #setOrderBy(String)} 与 {@link #getOrderBy()} 安全校验。
     */
    @Builder.Default
    private transient List<String> orderItems = new ArrayList<>();

    /**
     * 允许排序的字段白名单
     *
     * <p>业务方继。PageQuery 并重写此方法返回允许排序的字段列表，
     * 不在白名单中的字段将被拒绝排序（即使通过正则校验也会被拦截）。
     * 返回 null 或空集合表示不启用白名单校验（仅使用正则表达式校验）。
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * public class UserPageQuery extends PageQuery {
     *     @Override
     *     protected Set<String> allowedOrderByFields() {
     *         return Set.of("id", "username", "created_at", "updated_at");
     *     }
     * }
     * }</pre>
     *
     * @return 允许排序的字段名集合，默认返。null（不启用白名单）
     */
    protected Set<String> allowedOrderByFields() {
        return null;
    }

    /**
     * 校验字段是否在白名单。
     *
     * @param column 字段。
     * @return 通过校验返回 true
     */
    private boolean isColumnAllowed(String column) {
        Set<String> allowed = allowedOrderByFields();
        if (allowed == null || allowed.isEmpty()) {
            return true; // 未启用白名单，放。
        }
        return allowed.contains(column);
    }

    /**
     * 创建分页查询对象
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录。
     * @return 分页查询对象
     */
    public static PageQuery of(Integer pageNum, Integer pageSize) {
        return PageQuery.builder()
                .pageNum(pageNum != null && pageNum >= 1 ? pageNum : 1)
                .pageSize(normalizePageSize(pageSize))
                .build();
    }

    /**
     * 创建分页查询对象（默认页大小。
     *
     * @param pageNum 当前页码
     * @return 分页查询对象
     */
    public static PageQuery of(Integer pageNum) {
        return of(pageNum, PageConstants.DEFAULT_PAGE_SIZE);
    }

    /**
     * 标准化页大小
     *
     * <p>确保页大小在有效范围内：
     * <ul>
     *   <li>小于1修正为默认。</li>
     *   <li>大于 {@link PageConstants#MAX_PAGE_SIZE} 修正为最大。</li>
     * </ul>
     *
     * @param pageSize 原始页大。
     * @return 标准化后的页大小
     */
    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return PageConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, PageConstants.MAX_PAGE_SIZE);
    }

    /**
     * 添加排序。
     *
     * <p>将排序字段和方向添加到排序列表中。
     *
     * @param column 字段。
     * @param isAsc  是否升序，true表示升序，false表示降序
     * @return 当前对象，支持链式调。
     */
    public PageQuery addOrder(String column, boolean isAsc) {
        if (orderItems == null) {
            orderItems = new ArrayList<>();
        }
        if (column != null && !column.isBlank()
                && SAFE_COLUMN_PATTERN.matcher(column).matches()
                && isColumnAllowed(column)) {
            orderItems.add(column + (isAsc ? " ASC" : " DESC"));
        }
        return this;
    }

    /**
     * 添加升序排序。
     *
     * @param column 字段。
     * @return 当前对象，支持链式调。
     */
    public PageQuery addAscOrder(String column) {
        return addOrder(column, true);
    }

    /**
     * 添加降序排序。
     *
     * @param column 字段。
     * @return 当前对象，支持链式调。
     */
    public PageQuery addDescOrder(String column) {
        return addOrder(column, false);
    }

    /**
     * 设置排序项列表（覆盖Lombok生成的setter。
     *
     * <p>对传入的每个排序项进行安全校验，仅保留通过 {@link #SAFE_COLUMN_PATTERN} 匹配的合法项。
     * 防止绕过 {@link #addOrder(String, boolean)} 直接注入恶意排序字段。
     * 如果启用与 {@link #allowedOrderByFields()} 白名单，不在白名单中的字段也会被过滤。
     *
     * @param orderItems 排序项列。
     */
    public void setOrderItems(List<String> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            this.orderItems = new ArrayList<>();
            return;
        }
        List<String> filtered = new ArrayList<>();
        for (String item : orderItems) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String trimmed = item.trim();
            String column = trimmed.replaceAll("\\s+(ASC|DESC)$", "").trim();
            if (SAFE_COLUMN_PATTERN.matcher(column).matches() && isColumnAllowed(column)) {
                filtered.add(trimmed);
            }
        }
        this.orderItems = filtered;
    }

    /**
     * 设置排序字符串（覆盖 Lombok 生成。setter。
     *
     * <p>。orderBy 进行安全校验，仅保留通过 {@link #SAFE_COLUMN_PATTERN} 匹配的合法排序项。
     * 防止直接注入恶意 SQL 排序字段。
     *
     * @param orderBy 原始排序字符串，格式：field1 ASC, field2 DESC
     */
    public void setOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            super.setOrderBy(null);
            return;
        }
        String[] parts = orderBy.split(",");
        List<String> safeParts = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            String column = trimmed.replaceAll("\\s+(ASC|DESC)$", "").trim();
            if (SAFE_COLUMN_PATTERN.matcher(column).matches() && isColumnAllowed(column)) {
                safeParts.add(trimmed);
            }
        }
        super.setOrderBy(safeParts.isEmpty() ? null : String.join(", ", safeParts));
    }

    /**
     * 获取排序字符串（安全版本。
     *
     * <p>返回经过安全校验。orderBy 值，若未通过校验则返。null。
     *
     * @return 安全的排序字符串
     */
    public String getOrderBy() {
        String raw = super.getOrderBy();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 二次校验，确保通过直接赋值绕过的非法内容也被过滤
        String[] parts = raw.split(",");
        List<String> safeParts = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            String column = trimmed.replaceAll("\\s+(ASC|DESC)$", "").trim();
            if (SAFE_COLUMN_PATTERN.matcher(column).matches() && isColumnAllowed(column)) {
                safeParts.add(trimmed);
            }
        }
        return safeParts.isEmpty() ? null : String.join(", ", safeParts);
    }

    /**
     * 设置搜索关键字（覆盖Lombok生成的setter。
     *
     * <p>对搜索关键字进行安全处理。
     * <ul>
     *   <li>截断超长关键字，最大长与 {@link #MAX_SEARCH_KEY_LENGTH}</li>
     *   <li>转义SQL LIKE通配符（%、_、\），防止通配符注。</li>
     * </ul>
     *
     * @param searchKey 原始搜索关键。
     */
    public void setSearchKey(String searchKey) {
        if (searchKey == null) {
            super.setSearchKey(null);
            return;
        }
        String trimmed = searchKey.trim();
        if (trimmed.length() > MAX_SEARCH_KEY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SEARCH_KEY_LENGTH);
        }
        super.setSearchKey(trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_"));
    }

    /**
     * 获取偏移。
     *
     * <p>用于 MyBatis 分页查询。LIMIT offset, size。
     * 计算公式：offset = (pageNum - 1) * pageSize
     *
     * @return 偏移。
     * @throws ArithmeticException 当计算结果超。Integer.MAX_VALUE 时抛。
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
     * 获取偏移量（long 类型，无溢出风险。
     *
     * <p>用于超大分页场景，避。int 溢出问题。
     * 计算公式：offset = (pageNum - 1) * pageSize
     *
     * @return 偏移量（long 类型。
     */
    public long getOffsetLong() {
        if (pageNum == null || pageSize == null) {
            return 0L;
        }
        return (long) (Math.max(pageNum, 1) - 1) * Math.min(pageSize, PageConstants.MAX_PAGE_SIZE);
    }

    /**
     * 获取实际每页大小
     *
     * <p>返回在有效范围内的页大小。
     *
     * @return 实际每页大小
     */
    public int getEffectivePageSize() {
        return normalizePageSize(this.pageSize);
    }

    /**
     * 获取实际页码
     *
     * <p>返回在有效范围内的页码。
     *
     * @return 实际页码
     */
    @YdszJsonField(ignore = true)
    public int getEffectivePageNum() {
        return Math.max(this.pageNum != null ? this.pageNum : 1, 1);
    }

    /**
     * 计算起始行号
     *
     * <p>。开始的行号，用于显示等场景。
     *
     * @return 起始行号
     */
    public int getStartRow() {
        return getOffset() + 1;
    }

    /**
     * 计算结束行号
     *
     * @return 结束行号
     */
    public long getEndRow() {
        return getOffsetLong() + getEffectivePageSize();
    }

    /**
     * 判断是否有上一。
     *
     * @return 有上一页返回true
     */
    public boolean hasPrevious() {
        return getEffectivePageNum() > 1;
    }

    /**
     * 判断是否有下一。
     *
     * <p>此方法需要结合总记录数使用。
     * 通常。PageResponse 中配。total 字段使用。
     *
     * @param total 总记录数
     * @return 有下一页返回true
     */
    public boolean hasNext(long total) {
        return (long) getOffset() + getEffectivePageSize() < total;
    }

    /**
     * 获取排序SQL片段
     *
     * <p>生成可直接使用的 ORDER BY 子句。
     *
     * @return ORDER BY 子句，如 "ORDER BY created_at DESC"
     */
    @YdszJsonField(ignore = true)
    public String getOrderSql() {
        if (orderItems == null || orderItems.isEmpty()) {
            return "";
        }
        List<String> safeItems = new ArrayList<>();
        for (String item : orderItems) {
            String trimmed = item.trim();
            String column = trimmed.replaceAll("\\s+(ASC|DESC)$", "").trim();
            if (SAFE_COLUMN_PATTERN.matcher(column).matches() && isColumnAllowed(column)) {
                safeItems.add(trimmed);
            }
        }
        if (safeItems.isEmpty()) {
            return "";
        }
        return "ORDER BY " + String.join(", ", safeItems);
    }

    /**
     * 清空排序。
     *
     * @return 当前对象，支持链式调。
     */
    public PageQuery clearOrders() {
        if (orderItems != null) {
            orderItems.clear();
        }
        return this;
    }

    /**
     * 获取排序项数。
     *
     * @return 排序项数。
     */
    @YdszJsonField(ignore = true)
    public int getOrderCount() {
        return orderItems != null ? orderItems.size() : 0;
    }

    @Override
    public String toString() {
        return "PageQuery{" +
                "pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", orderItems=" + orderItems +
                ", searchKey='" + getSearchKey() + '\'' +
                ", ascending=" + getAscending() +
                '}';
    }
}
