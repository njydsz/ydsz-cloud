package com.njydsz.common.core.response;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.annotation.JsonPropertyOrder;

/**
 * 分页响应结果封装类
 *
 * <p>用于封装分页查询的响应数据，包含分页信息和实际数据。
 * 继承 {@link BaseResponse}，遵循统一的响应结构规范。
 *
 * <p><b>data 语义：</b>分页场景下 {@code data} 字段承载<b>列表数据</b>，
 * 即调用 {@link #success(Long, Long, Long, Object)} 时传入的 data 应为
 * {@code List<T>}（或其分页投影）。分页元信息（total / pageNum / pageSize / pages）
 * 位于响应体顶层，与 data 平级。</p>
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
 *   <li>data: 分页数据（列表）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 构建分页响应（data 为 List<T>）
 * PageResponse<List<User>> result = PageResponse.success(total, pageNum, pageSize, userList);
 *
 * // 判断响应是否成功
 * if (result.isSuccess()) {
 *     List<User> data = result.getData();
 * }
 * }</pre>
 *
 * @param <T> 数据类型（分页场景下通常为 List 或其投影）
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see IResponse
 * @see BaseResponse
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "traceId", "timestamp", "total", "pageNum", "pageSize", "pages"})
public class PageResponse<T> extends BaseResponse<T> {

    private static final long serialVersionUID = 1L;

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
     * 创建成功分页响应，并在响应中标记分页参数是否被归一化。
     *
     * <p>当客户端传入的 {@code rawPageSize} 被 {@link PageConstants} 归一化（截断为上限或替换为默认值）
     * 时，响应 {@code extensions} 中添加 {@code "pageSizeNormalized": true} 标记，
     * 便于前端/调试时识别分页参数被框架调整。</p>
     *
     * @param total         总记录数
     * @param pageNum       当前页码
     * @param pageSize      归一化后的每页记录数
     * @param rawPageSize   客户端传入的原始每页记录数（用于判断是否发生归一化）
     * @param data          分页数据
     * @param <T>           数据类型
     * @return 携带归一化标记的成功分页响应
     * @since 1.7.0
     */
    public static <T> PageResponse<T> successWithNormalization(Long total, Long pageNum, Long pageSize, Integer rawPageSize, T data) {
        PageResponse<T> response = success(total, pageNum, pageSize, data);
        PageConstants.NormalizeResult result = PageConstants.normalizePageSizeWithResult(rawPageSize);
        if (result.isAdjusted()) {
            response.putExtension("pageSizeNormalized", true);
            response.putExtension("requestedPageSize", rawPageSize);
        }
        return response;
    }

    /**
     * 创建成功分页响应（便捷重载，接收基本类型）
     *
     * <p>适用于 MyBatis-Plus {@code IPage} 等返回 {@code long} / {@code int} 基本类型的场景。</p>
     *
     * <p><b>注意：</b>此方法在 core 模块中仅为向后兼容保留。
     * 新代码建议在 web 层定义 {@code PageResponse} 的子类或扩展方法，
     * 以便进一步封装框架特定的分页适配逻辑。</p>
     *
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param data     分页数据
     * @param <T>      数据类型
     * @return 成功分页响应
     * @deprecated 此方法在 v1.7.0 标记为待下沉，建议在 web 层封装适配。
     *             调用 {@link #success(Long, Long, Long, Object)} 替代，显式装箱参数。
     * @since 1.0.0
     */
    @Deprecated
    public static <T> PageResponse<T> success(long total, int pageNum, int pageSize, T data) {
        return success((long) total, (long) pageNum, (long) pageSize, data);
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
        return fail(BaseResultCode.UNKNOWN.getCode(), msg);
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
        return success(0L, 1L, (long) PageConstants.getDefaultPageSize(), null);
    }    /**
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
