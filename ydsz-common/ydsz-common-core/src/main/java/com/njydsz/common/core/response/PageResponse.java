package com.njydsz.common.core.response;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.core.code.YdszResultCode;

/**
 * 分页响应信封（{@link YdszResponse} 的子类型）。
 *
 * <p>将分页元信息（total / pageNum / pageSize）收口到专用的分页响应类型中， 使 {@link YdszResponse}
 * 不再承担分页职责，同时提供类型明确的返回对象， 便于 Controller 声明 {@code PageResponse<UserVO>} 或 {@code
 * YdszResponse<PageResponse<UserVO>>}。
 *
 * <p>本类是 API 响应信封，用于 Controller 层返回分页数据。 新代码请直接返回 {@code PageResponse<T>}。
 *
 * <p><b>迁移提示：</b>{@link YdszResponse} 上的分页字段与 {@code successPage()/emptyPage()} 方法已于 1.0.0
 * 移除。新代码请直接返回 {@code PageResponse<T>}。
 *
 * @param <T> 数据元素的类型
 * @author ydsz-team
 * @since 1.0.0
 */
@EqualsAndHashCode(callSuper = true)
public class PageResponse<T> extends YdszResponse<T> {

  /** 总记录数。 */
  @Getter @Setter private Long total;

  /** 当前页码（从 1 开始）。 */
  @Getter @Setter private Long pageNum;

  /** 每页记录数。 */
  @Getter @Setter private Long pageSize;

  /**
   * 下一页游标（null 表示已无更多数据）。
   *
   * <p>仅游标分页模式有效，偏移量模式下为 null。
   */
  @Getter @Setter private String nextCursor;

  /**
   * 是否有下一页。
   *
   * <p>仅游标分页模式有效，偏移量模式下参考 {@link #getPages()} 与当前 pageNum 判断。
   */
  @Getter @Setter private Boolean hasMore;

  /** 由工厂方法构造。 */
  public PageResponse() {
    super();
  }

  /**
   * 返回分页成功响应。
   *
   * @param total 总记录数
   * @param pageNum 当前页码（从 1 开始）
   * @param pageSize 每页记录数
   * @param data 分页数据
   * @param <T> 数据类型
   * @return 分页成功响应
   */
  public static <T> PageResponse<T> success(Long total, Long pageNum, Long pageSize, T data) {
    PageResponse<T> response = new PageResponse<>();
    response.setCode(YdszResultCode.SUCCESS.getCode());
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
   * @param pageNum 当前页码
   * @param pageSize 每页记录数
   * @param <T> 数据类型
   * @return 空分页响应
   */
  public static <T> PageResponse<T> empty(Long pageNum, Long pageSize) {
    return success(0L, pageNum, pageSize, null);
  }

  /**
   * 返回分页失败响应。
   *
   * <p>走 i18n 链路：使用 {@link ResultCode#getKey()} 作为国际化 key 解析消息， 解析失败时回退到 {@link
   * ResultCode#getMsg()}。
   *
   * @param resultCode 结果码
   * @param <T> 数据类型
   * @return 分页失败响应
   */
  public static <T> PageResponse<T> error(ResultCode resultCode) {
    PageResponse<T> response = new PageResponse<>();
    response.setCode(resultCode.getCode());
    response.setMsg(resolveMessage(resultCode.getKey(), resultCode.getMsg()));
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

  // ======================== 游标分页工厂方法 ========================

  /**
   * 返回游标分页成功响应。
   *
   * <p>使用游标分页模式时调用本方法，返回的数据直接在 {@link #getData()} 中， 无需 total/pageNum/pageSize 等偏移量字段。
   *
   * @param records 数据列表
   * @param nextCursor 下一页游标（null 表示已无更多数据）
   * @param <T> 数据类型
   * @return 游标分页成功响应
   */
  public static <T> PageResponse<T> ofCursor(List<T> records, String nextCursor) {
    PageResponse<T> response = new PageResponse<>();
    response.setCode(YdszResultCode.SUCCESS.getCode());
    response.setMsg(resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"));
    response.setData((T) records);
    response.setNextCursor(nextCursor);
    response.setHasMore(nextCursor != null);
    return response;
  }

  /**
   * 返回游标分页空响应（无更多数据）。
   *
   * @param <T> 数据类型
   * @return 空游标分页响应
   */
  public static <T> PageResponse<T> emptyCursor() {
    PageResponse<T> response = new PageResponse<>();
    response.setCode(YdszResultCode.SUCCESS.getCode());
    response.setMsg(resolveMessage(MSG_OPERATION_SUCCESS, "操作成功"));
    response.setData(null);
    response.setNextCursor(null);
    response.setHasMore(false);
    return response;
  }

  /**
   * 判断当前是否为游标分页模式。
   *
   * <p>判断依据：{@link #nextCursor} 非空 或 {@link #hasMore} 非 null。
   *
   * @return true 表示游标分页模式，false 表示偏移量分页模式
   */
  public boolean isCursorMode() {
    return nextCursor != null || hasMore != null;
  }
}
