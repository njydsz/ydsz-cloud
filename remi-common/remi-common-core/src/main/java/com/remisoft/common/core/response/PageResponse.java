package com.remisoft.common.core.response;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.remisoft.common.json.annotation.JsonInclude;
import com.remisoft.common.json.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分页响应体。
 *
 * <p>新增 {@link #from(IPageResult)} 工厂方法，可直接桥接 domain 层 {@code PageResult} 等分页对象，
 * 消除手写映射的样板代码。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 方式 1：从 IPageResult 桥接
 * PageResult<UserDO> domainPage = userService.pageQuery(query);
 * return PageResponse.from(domainPage);
 *
 * // 方式 2：显式指定泛型（domain PageResult 返回 List<?> 需要强转时）
 * PageResponse<UserVO> resp = PageResponse.from(domainPage, UserVO::from);
 *
 * // 方式 3：传统构造
 * return PageResponse.success(100L, 1L, 20L, userList);
 * }</pre>
 *
 * @param <T> 列表数据类型
 * @author remi-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "timestamp", "total", "pageNum", "pageSize", "pages"})
public class PageResponse<T> extends BaseResponse<T> {

    private static final long serialVersionUID = 1L;

    private Long total;
    private Long pageNum;
    private Long pageSize;
    private Long pages;

    public PageResponse(String code, String msg, Long total, Long pageNum, Long pageSize, Long pages, T data) {
        super(code, msg, data);
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pages;
    }

    // -------------------------------------------------------------------------
    // 静态工厂
    // -------------------------------------------------------------------------

    public static <T> PageResponse<T> success(Long total, Long pageNum, Long pageSize, T data) {
        Long pages = calcPages(total, pageSize);
        return new PageResponse<>(BaseResponse.SUCCESS_CODE, "ok", total, pageNum, pageSize, pages, data);
    }

    public static <T> PageResponse<T> error(String code, String msg) {
        return new PageResponse<>(code, msg, 0L, 0L, 0L, 0L, null);
    }

    /**
     * 从任意实现了 {@link IPageResult} 的对象桥接为 PageResponse
     *
     * <p>典型用法：将 domain 层的 {@code PageResult<T>} 直接转换为 API 层的分页响应。
     * 内部自动执行页码 / total / pages 的归一化计算。
     *
     * <p><b>注意：</b>由于 {@link IPageResult#records()} 返回 {@code List<?>}，
     * 需要在调用处显式强转或借助 {@link #fromIPage(IPageResult, Class)} 获得类型安全。
     *
     * @param result 分页结果数据源（不可为 null）
     * @param <T>    数据类型
     * @return 新的 PageResponse 实例，code 为 SUCCESS_CODE，msg 为 "ok"
     * @throws NullPointerException 如果 result 为 null
     * @since 1.8.0
     */
    @SuppressWarnings("unchecked")
    public static <T> PageResponse<T> from(IPageResult result) {
        Objects.requireNonNull(result, "IPageResult must not be null");
        List<?> rawList = result.records();
        List<T> data = rawList != null ? (List<T>) rawList : Collections.emptyList();
        return success(
            result.total(),
            result.pageNum(),
            result.pageSize(),
            data
        );
    }

    /**
     * 从 IPageResult 桥接，并对 records 做类型安全转换
     *
     * <p>等价于 {@code from(result)}，但增加运行时类型校验。
     *
     * @param result    分页结果数据源
     * @param itemClass 数据项的预期 Class（用于基础类型校验）
     * @param <T>       数据类型
     * @return 新的 PageResponse 实例
     * @throws ClassCastException 如果 records 中存在非 T 类型的元素
     * @since 1.8.0
     */
    @SuppressWarnings("unchecked")
    public static <T> PageResponse<T> fromIPage(IPageResult result, Class<T> itemClass) {
        Objects.requireNonNull(result, "IPageResult must not be null");
        Objects.requireNonNull(itemClass, "itemClass must not be null");
        List<?> rawList = result.records();
        if (rawList == null || rawList.isEmpty()) {
            return success(result.total(), result.pageNum(), result.pageSize(), Collections.emptyList());
        }
        // 快速类型校验（仅检查第一个元素，避免每次转换都全列表扫描）
        if (!itemClass.isInstance(rawList.get(0))) {
            throw new ClassCastException(
                "PageResponse fromIPage type mismatch: expected " + itemClass.getName()
                    + " but found " + rawList.get(0).getClass().getName()
            );
        }
        return from(result);
    }

    // -------------------------------------------------------------------------
    // 内部辅助
    // -------------------------------------------------------------------------

    private static Long calcPages(Long total, Long pageSize) {
        if (total == null || total <= 0 || pageSize == null || pageSize <= 0) {
            return 0L;
        }
        return (total + pageSize - 1) / pageSize;
    }
}
