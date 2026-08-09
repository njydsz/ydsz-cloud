package com.njydsz.common.core.response;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.code.ResultCode;

/**
 * 分页响应信封（{@link BaseResponse} 的子类型）。
 *
 * <p>将分页元信息（total / pageNum / pageSize）收口到专用的分页响应类型中，
 * 使通用 {@link BaseResponse} 不再承担分页职责，同时提供类型明确的返回对象，
 * 便于 Controller 声明 {@code PageResult<UserVO>} 或 {@code BaseResponse<PageResult<UserVO>>}。</p>
 *
 * <p>本类与 {@code com.njydsz.common.domain.query.PageResult}（领域层分页结果载体）职责不同：
 * 后者是领域 / 数据层分页对象，本类是 API 响应信封。两者可组合使用，例如
 * {@code PageResult.success(total, pageNum, pageSize, domainPage.getRecords())}。</p>
 *
 * <p>{@link BaseResponse#successPage(Long, Long, Long, Object)} 已标记 {@code @Deprecated}，
 * 新代码请直接返回 {@code PageResult<T>} 以获更强的类型表达与 {@link #getPages()} 等便捷方法。</p>
 *
 * @param <T> 数据元素的类型
 * @author ydsz-team
 * @since 1.9.1
 */
public class PageResult<T> extends BaseResponse<T> {

    /** 由工厂方法构造。 */
    public PageResult() {
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
    public static <T> PageResult<T> success(Long total, Long pageNum, Long pageSize, T data) {
        PageResult<T> response = new PageResult<>();
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
    public static <T> PageResult<T> empty(Long pageNum, Long pageSize) {
        return success(0L, pageNum, pageSize, null);
    }

    /**
     * 返回分页失败响应。
     *
     * @param resultCode 结果码（自动走 i18n 链路）
     * @param <T>        数据类型
     * @return 分页失败响应
     */
    public static <T> PageResult<T> error(ResultCode resultCode) {
        PageResult<T> response = new PageResult<>();
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
