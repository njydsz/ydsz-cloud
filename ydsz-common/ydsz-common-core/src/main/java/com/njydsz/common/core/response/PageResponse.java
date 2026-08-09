package com.njydsz.common.core.response;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.code.ResultCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页响应信封（{@link BaseResponse} 的子类型）。
 *
 * <p>将分页元信息（total / pageNum / pageSize）收口到专用的分页响应类型中，
 * 使 {@link BaseResponse} 不再承担分页职责，同时提供类型明确的返回对象，
 * 便于 Controller 声明 {@code PageResponse<UserVO>} 或 {@code BaseResponse<PageResponse<UserVO>>}。</p>
 *
 * <p>领域层分页结果载体请使用 {@code com.njydsz.common.domain.query.PageResponse}，
 * 本类是 API 响应信封。两者可组合使用，例如：
 * {@code PageResponse.success(total, pageNum, pageSize, domainPage.getRecords())}。</p>
 *
 * <p><b>迁移提示：</b>{@link BaseResponse} 上的分页字段与 {@code successPage()/emptyPage()}
 * 方法已于 v1.9.3 移除。新代码请直接返回 {@code PageResponse<T>}。</p>
 *
 * @param <T> 数据元素的类型
 * @author ydsz-team
 * @since 1.9.1
 */
@EqualsAndHashCode(callSuper = true)
public class PageResponse<T> extends BaseResponse<T> {

    /** 总记录数。 */
    @Getter @Setter
    private Long total;

    /** 当前页码（从 1 开始）。 */
    @Getter @Setter
    private Long pageNum;

    /** 每页记录数。 */
    @Getter @Setter
    private Long pageSize;

    /** 由工厂方法构造。 */
    public PageResponse() {
        super();
    }

    /**
     * 返回分页成功响应。
     *
     * @param total    总记录数
     * @param pageNum  当前页码（从 1 开始）
     * @param pageSize 每页记录数
     * @param data     分页数据
     * @param <T>      数据类型
     * @return 分页成功响应
     */
    public static <T> PageResponse<T> success(Long total, Long pageNum, Long pageSize, T data) {
        PageResponse<T> response = new PageResponse<>();
        response.setCode(BaseResultCode.SUCCESS.getCode());
        response.setMsg(resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"));
        response.setData(data);
        response.setTotal(total);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        return response;
    }

    /**
     * 返回空分页响应（total = 0）。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param <T>      数据类型
     * @return 空分页响应
     */
    public static <T> PageResponse<T> empty(Long pageNum, Long pageSize) {
        return success(0L, pageNum, pageSize, null);
    }

    /**
     * 返回分页失败响应。
     *
     * @param resultCode 结果码（自动走 i18n 链路）
     * @param <T>        数据类型
     * @return 分页失败响应
     */
    public static <T> PageResponse<T> error(ResultCode resultCode) {
        PageResponse<T> response = new PageResponse<>();
        response.setCode(resultCode.getCode());
        response.setMsg(resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()));
        return response;
    }

    /**
     * 计算总页数（基于 {@code total} 与 {@code pageSize}）。
     *
     * @return 总页数；total / pageSize 任一缺失或 pageSize ≤ 0 时返回 0
     */
    public long getPages() {
        Long total = getTotal();
        Long size = getPageSize();
        if (total == null || size == null || size <= 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }
}
