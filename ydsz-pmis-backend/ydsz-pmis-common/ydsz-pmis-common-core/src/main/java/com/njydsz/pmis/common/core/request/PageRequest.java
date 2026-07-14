package com.njydsz.pmis.common.core.request;

import java.util.regex.Pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.njydsz.pmis.common.core.constant.PageConstants;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分页请求封装类
 *
 * <p>用于封装分页查询请求的参数。
 *
 * <p><b>请求结构：</b>
 * <ul>
 *   <li>pageNum: 当前页码（从1开始，默认为1）</li>
 *   <li>pageSize: 每页记录数（默认为 {@link PageConstants#DEFAULT_PAGE_SIZE}）</li>
 * </ul>
 *
 * <p><b>与 {@code com.njydsz.pmis.common.domain.query.PageQuery} 的区别：</b>
 * <ul>
 *   <li>{@code PageRequest} 位于 core 模块，用于 HTTP API 层，分页字段为 {@code Long} 类型，与 MyBatis-Plus {@code Page<T>} 对齐</li>
 *   <li>{@code PageQuery} 位于 domain 模块，用于 Service/Repository 层，分页字段为 {@code Integer} 类型，并集成搜索/过滤/排序白名单等业务能力</li>
 *   <li>两者共用 {@link PageConstants} 中的默认值与上限，避免出现不一致的分页约束</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 定义查询请求
 * public class UserQueryRequest extends PageRequest {
 *     private String username;
 *     private String realName;
 *     private String orderByColumn;
 *     private String isAsc;
 * }
 *
 * // Controller 使用
 * public PageResponse<List<User>> list(UserQueryRequest request) {
 *     Long pageNum = request.getPageNum();
 *     Long pageSize = request.getPageSize();
 *     // 业务逻辑
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see IRequest
 * @see BaseRequest
 * @see PageConstants
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest extends BaseRequest {

    private static final long serialVersionUID = 2L;

    /**
     * 校验排序字段安全性，防止 SQL 注入
     * <p>允许格式：单字段名 或 多字段名+ASC/DESC 逗号分隔（如 "name ASC,age DESC"）
     */
    private static final Pattern SAFE_SORT_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_.]+(\\s+(ASC|DESC))?(,\\s*[a-zA-Z0-9_.]+(\\s+(ASC|DESC))?)*$",
            Pattern.CASE_INSENSITIVE);


    /**
     * 当前页码
     * <p>从1开始计数，默认为1
     */
    @Min(1)
    @Builder.Default
    private Long pageNum = 1L;

    /**
     * 每页记录数
     * <p>每页返回的记录数量，默认为 PageConstants.DEFAULT_PAGE_SIZE
     * <p>最大值受 {@code ydsz.core.max-page-size} 配置控制（默认 1000）
     */
    @Min(1)
    @Max(PageConstants.MAX_PAGE_SIZE)
    @Builder.Default
    private Long pageSize = (long) PageConstants.DEFAULT_PAGE_SIZE;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 排序方向（ASC/DESC）
     */
    @Builder.Default
    private String orderDir = "ASC";

    /**
     * 获取安全的页码
     *
     * @return 页码，若原始值为 null 或小于 1 则返回 1
     */
    public Long getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    /**
     * 获取安全的每页记录数
     *
     * @return 每页记录数，已限制在 [1, MAX_PAGE_SIZE] 范围内
     */
    public Long getSafePageSize() {
        if (pageSize == null || pageSize < 1) {
            return (long) PageConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, PageConstants.MAX_PAGE_SIZE);
    }

    /**
     * 获取安全的排序表达式
     * <p>防止SQL注入：使用 {@link #SAFE_SORT_PATTERN} 校验，
     * 支持单字段名或多字段名+ASC/DESC 逗号分隔格式（如 "name ASC, age DESC"）。
     * 与 {@link #validateSort()} 使用相同的校验规则，保证行为一致。
     *
     * @return 安全的排序表达式，不合法时返回 null
     */
    public String getSafeOrderBy() {
        if (orderBy == null || orderBy.isEmpty()) {
            return null;
        }
        if (!SAFE_SORT_PATTERN.matcher(orderBy).matches()) {
            return null;
        }
        return orderBy;
    }

    /**
     * 获取安全的排序方向
     *
     * @return ASC 或 DESC
     */
    public String getSafeOrderDir() {
        if ("DESC".equalsIgnoreCase(orderDir)) {
            return "DESC";
        }
        return "ASC";
    }

    /**
     * 带参数校验的工厂方法
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @return 校验后的 PageRequest 实例
     */
    public static PageRequest of(Long pageNum, Long pageSize) {
        PageRequest req = new PageRequest();
        req.pageNum = pageNum != null ? Math.max(pageNum, 1L) : 1L;
        long ps = pageSize != null ? pageSize : (long) PageConstants.DEFAULT_PAGE_SIZE;
        req.pageSize = Math.max(1, Math.min(ps, PageConstants.MAX_PAGE_SIZE));
        return req;
    }

    /**
     * 获取偏移量
     * <p>用于 MyBatis-Plus 分页插件的 offset 计算
     *
     * @return 偏移量
     */
    public Long getOffset() {
        return (getSafePageNum() - 1) * getSafePageSize();
    }

    /**
     * 校验排序字段安全性，防止 SQL 注入
     *
     * @throws IllegalArgumentException 当排序字段不合法时抛出
     */
    public void validateSort() {
        if (orderBy != null && !orderBy.isBlank() && !SAFE_SORT_PATTERN.matcher(orderBy).matches()) {
            throw new IllegalArgumentException("Invalid sort field: " + orderBy);
        }
    }
}