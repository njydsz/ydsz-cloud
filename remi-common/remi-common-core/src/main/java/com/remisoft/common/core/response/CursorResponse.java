package com.remisoft.common.core.response;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.remisoft.common.json.annotation.JsonInclude;
import com.remisoft.common.json.annotation.JsonPropertyOrder;

/**
 * 基于游标的分页响应结果封装类
 *
 * <p>用于替代 offset/limit 分页模式，特别适合大数据量深度分页场景。
 * 游标分页相比 offset 分页的优势：
 * <ul>
 *   <li>避免深 offset 导致的性能问题（MySQL/PostgreSQL 需要扫描跳过 offset 行）</li>
 *   <li>实时性更好，不会出现翻页时数据重复或遗漏的问题</li>
 *   <li>适合无限滚动加载场景（移动端 feed 流、实时数据流）</li>
 * </ul>
 *
 * <p><b>响应结构：</b>
 * <ul>
 *   <li>code: 响应码，A00000 表示成功</li>
 *   <li>msg: 响应消息</li>
 *   <li>data: 分页数据列表</li>
 *   <li>nextCursor: 获取下一页的游标（null 表示已到最后一页）</li>
 *   <li>prevCursor: 获取上一页的游标（可为 null）</li>
 *   <li>hasMore: 是否还有更多数据</li>
 *   <li>pageSize: 当前页返回的记录数</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 首次查询（不传 cursor）
 * CursorResponse<List<Item>> first = CursorResponse.success(items, "page1-token", 20);
 *
 * // 后续查询（使用上一次返回的 nextCursor）
 * CursorResponse<List<Item>> next = CursorResponse.success(items, "page2-token", 20, "page1-token");
 * }</pre>
 *
 * <p><b>游标实现建议：</b>
 * <ul>
 *   <li>对于有序 ID：直接使用上一页最后一项的 ID</li>
 *   <li>对于时间序列：使用上一页最后项的时间戳 + ID</li>
 *   <li>对于复杂排序：使用 Base64 编码的复合游标</li>
 * </ul>
 *
 * @param <T> 数据类型（通常为 List 或其投影）
 *
 * @author remi-team
 * @since 2.0.0
 *
 * @see BaseResponse
 * @see PageResponse
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "msg", "data", "traceId", "timestamp", "nextCursor", "prevCursor", "hasMore", "pageSize"})
public class CursorResponse<T> extends BaseResponse<T> {

    private static final long serialVersionUID = 1L;

    /**
     * 下一页游标令牌。
     *
     * <p>客户端将此值作为下次请求的 {@code cursor} 参数传入，以获取下一页数据。
     * {@code null} 表示已到最后一页，无更多数据。</p>
     */
    private String nextCursor;

    /**
     * 上一页游标令牌（可为 null）。
     *
     * <p>如果支持双向分页，返回上一页的游标值；如果仅支持单向滚动加载，返回 null。</p>
     */
    private String prevCursor;

    /**
     * 是否还有更多数据。
     *
     * <p>等效于 {@code nextCursor != null}，但提供更便捷的语义判断。</p>
     */
    private Boolean hasMore;

    /**
     * 当前页返回的记录数。
     *
     * <p>服务端实际返回的 item 数量，受 {@code pageSize} 限制。</p>
     */
    private Integer pageSize;

    /**
     * 创建成功游标分页响应。
     *
     * @param data       分页数据
     * @param nextCursor 下一页游标（null 表示最后一页）
     * @param pageSize   当前页记录数
     * @param <T>        数据类型
     * @return 成功游标分页响应
     */
    public static <T> CursorResponse<T> success(T data, String nextCursor, int pageSize) {
        return CursorResponse.<T>builder()
                .code(BaseResponse.SUCCESS)
                .msg(MessageResolverHolder.resolveMessage(BaseResponse.MSG_OPERATION_SUCCESS, "操作成功"))
                .data(data)
                .nextCursor(nextCursor)
                .hasMore(nextCursor != null)
                .pageSize(pageSize)
                .build();
    }

    /**
     * 创建成功游标分页响应（含上一页游标）。
     *
     * @param data       分页数据
     * @param nextCursor 下一页游标（null 表示最后一页）
     * @param prevCursor 上一页游标（null 表示没有上一页）
     * @param pageSize   当前页记录数
     * @param <T>        数据类型
     * @return 成功游标分页响应
     */
    public static <T> CursorResponse<T> success(T data, String nextCursor, String prevCursor, int pageSize) {
        return CursorResponse.<T>builder()
                .code(BaseResponse.SUCCESS)
                .msg(MessageResolverHolder.resolveMessage(BaseResponse.MSG_OPERATION_SUCCESS, "操作成功"))
                .data(data)
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasMore(nextCursor != null)
                .pageSize(pageSize)
                .build();
    }

    /**
     * 创建成功游标分页响应（从 nextToken 字符串自动派生）。
     *
     * <p>兼容 Google API Design Guide 风格的 nextPageToken 模式。</p>
     *
     * @param data          分页数据
     * @param nextPageToken 下一页令牌（null 表示最后一页）
     * @param pageSize      当前页记录数
     * @param <T>           数据类型
     * @return 成功游标分页响应
     */
    public static <T> CursorResponse<T> successWithToken(T data, String nextPageToken, int pageSize) {
        return success(data, nextPageToken, pageSize);
    }

    /**
     * 创建失败游标分页响应。
     *
     * @param code 错误码
     * @param msg  错误消息
     * @param <T>  数据类型
     * @return 失败游标分页响应
     */
    public static <T> CursorResponse<T> fail(String code, String msg) {
        return CursorResponse.<T>builder()
                .code(code)
                .msg(msg)
                .data(null)
                .hasMore(false)
                .pageSize(0)
                .build();
    }

    /**
     * 创建失败游标分页响应（使用 BaseResultCode）。
     *
     * @param resultCode 结果码
     * @param <T>        数据类型
     * @return 失败游标分页响应
     */
    public static <T> CursorResponse<T> fail(com.remisoft.common.core.code.ResultCode resultCode) {
        return CursorResponse.<T>builder()
                .code(resultCode.getCode())
                .msg(MessageResolverHolder.resolveMessage(resultCode.getMessageKey(), resultCode.getMsg()))
                .data(null)
                .hasMore(false)
                .pageSize(0)
                .build();
    }

    /**
     * 创建空游标分页响应（表示没有任何数据）。
     *
     * @param <T> 数据类型
     * @return 空数据成功响应
     */
    public static <T> CursorResponse<T> empty() {
        return CursorResponse.<T>builder()
                .code(BaseResponse.SUCCESS)
                .msg(MessageResolverHolder.resolveMessage(BaseResponse.MSG_OPERATION_SUCCESS, "操作成功"))
                .data(null)
                .nextCursor(null)
                .hasMore(false)
                .pageSize(0)
                .build();
    }

    /**
     * 判断是否有下一页数据。
     *
     * @return true 表示有更多数据
     */
    public boolean hasNext() {
        return Boolean.TRUE.equals(hasMore);
    }

    /**
     * 判断是否有上一页数据。
     *
     * @return true 表示有上一页
     */
    public boolean hasPrevious() {
        return prevCursor != null;
    }
}
