package com.njydsz.pmis.common.core.response;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页响应结果封装类
 *
 * <p>用于封装分页查询的响应数据，包含分页信息和实际数据。
 * 继承 {@link BaseResponse}，遵循统一的响应结构规范。
 *
 * <p><b>响应结构：</b>
 * <ul>
 *   <li>code: 响应码，A00000表示成功，其他表示失败</li>
 *   <li>msg: 响应消息</li>
 *   <li>timestamp: 响应时间戳</li>
 *   <li>total: 总记录数</li>
 *   <li>pageNum: 当前页码</li>
 *   <li>pageSize: 每页记录数</li>
 *   <li>pages: 总页数</li>
 *   <li>data: 分页数据</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 构建分页响应
 * PageResponse<List<User>> result = PageResponse.success(total, pageNum, pageSize, userList);
 *
 * // 判断响应是否成功
 * if (result.isSuccess()) {
 *     List<User> data = result.getData();
 * }
 * }</pre>
 *
 * @param <T> 数据类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see IResponse
 * @see BaseResponse
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class PageResponse<T> extends BaseResponse<T> {

    private static final long serialVersionUID = 4L;

    /**
     * 总记录数
     * <p>查询条件匹配的总记录数
     */
    private Long total;

    /**
     * 当前页码
     * <p>从1开始计数
     */
    private Long pageNum;

    /**
     * 每页记录数
     * <p>每页返回的记录数量
     */
    private Long pageSize;

    /**
     * 总页数
     * <p>根据total和pageSize计算得出
     */
    private Long pages;

    /**
     * 默认构造函数
     */
    public PageResponse() {
    }

    /**
     * 全参数构造函数
     *
     * @param code     响应码
     * @param msg      响应消息
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param pages    总页数
     * @param data     分页数据
     */
    public PageResponse(String code, String msg, Long total, Long pageNum, Long pageSize, Long pages, T data) {
        super(code, msg, data);
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pages;
    }

    /**
     * 构建分页响应
     *
     * @param code     响应码
     * @param msg      响应消息
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param data     分页数据
     * @param <T>      数据类型
     * @return 分页响应对象
     */
    public static <T> PageResponse<T> of(String code, String msg, Long total, Long pageNum, Long pageSize, T data) {
        Long pages = calcPages(total, pageSize);
        return new PageResponse<>(code, msg, total, pageNum, pageSize, pages, data);
    }

    /**
     * 创建成功分页响应
     *
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param data     分页数据
     * @param <T>      数据类型
     * @return 成功分页响应
     */
    public static <T> PageResponse<T> success(Long total, Long pageNum, Long pageSize, T data) {
        return of(BaseResponse.SUCCESS, resolveMessage(BaseResponse.MSG_OPERATION_SUCCESS, "操作成功"), total, pageNum, pageSize, data);
    }

    /**
     * 创建成功分页响应（无分页信息）
     *
     * @param data 分页数据
     * @param <T>  数据类型
     * @return 成功分页响应
     */
    public static <T> PageResponse<T> success(T data) {
        return success(0L, 1L, 10L, data);
    }

    /**
     * 创建失败分页响应
     *
     * @param code 错误码
     * @param msg  错误消息
     * @param <T>  数据类型
     * @return 失败分页响应
     */
    public static <T> PageResponse<T> fail(String code, String msg) {
        return of(code, msg, 0L, 0L, 0L, null);
    }

    /**
     * 创建失败分页响应
     *
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 失败分页响应
     */
    public static <T> PageResponse<T> fail(String msg) {
        return fail(BaseResponse.ERROR, msg);
    }

    /**
     * 计算总页数
     *
     * @param total    总记录数
     * @param pageSize 每页记录数
     * @return 总页数
     */
    private static Long calcPages(Long total, Long pageSize) {
        if (total == null || total <= 0 || pageSize == null || pageSize <= 0) {
            return 0L;
        }
        return (total + pageSize - 1) / pageSize;
    }

    /**
     * 从 MyBatis-Plus Page 构建分页响应。
     *
     * <p>自动提取 Page 中的 total、current、size 和 records。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  数据类型
     * @return 分页响应对象
     */
    public static <T> PageResponse<T> ofPage(Page<T> page) {
        if (page == null) {
            return success(0L, 1L, 10L, null);
        }
        Long total = page.getTotal();
        Long pageNum = page.getCurrent();
        Long pageSize = page.getSize();
        T data = (T) page.getRecords();
        return success(total, pageNum, pageSize, data);
    }

    // ============================== 向后兼容方法 ==============================

    /**
     * 创建空分页响应
     *
     * <p>返回一个数据为 null 的成功分页响应，用于异常降级或无数据场景。
     *
     * @param <T> 数据类型
     * @return 空分页响应
     */
    public static <T> PageResponse<T> empty() {
        return success(null);
    }

    /**
     * 从列表构建分页响应（向后兼容）
     *
     * <p>旧版 API 中 {@code PageResponse<T>} 的 {@code T} 表示列表元素类型，
     * 此方法接受 {@code List<T>} 并将其作为 data 字段存储，
     * 配合 {@link #getList()} 方法可获取回列表。
     *
     * @param list     数据列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param <T>      元素类型
     * @return 分页响应对象
     */
    @SuppressWarnings("unchecked")
    public static <T> PageResponse<T> of(List<T> list, long total, long pageNum, long pageSize) {
        return success(total, pageNum, pageSize, (T) list);
    }

    /**
     * 获取数据列表（向后兼容）
     *
     * <p>当 data 字段为 List 类型时返回该列表，否则返回空列表。
     * 用于兼容旧版 {@code PageResponse<T>} 中 {@code T} 为元素类型的用法。
     *
     * @return 数据列表
     */
    @SuppressWarnings("unchecked")
    public List<T> getList() {
        Object data = getData();
        return data instanceof List ? (List<T>) data : List.of();
    }

    /**
     * 是否有下一页
     *
     * @return 有下一页返回true，否则返回false
     */
    public boolean hasNext() {
        if (pages == null || pages <= 0) {
            return false;
        }
        return pageNum != null && pageNum < pages;
    }

    /**
     * 是否有上一页
     *
     * @return 有上一页返回true，否则返回false
     */
    public boolean hasPrevious() {
        return pageNum != null && pageNum > 1;
    }
}
