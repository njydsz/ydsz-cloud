package com.remisoft.common.core.response;

import com.remisoft.common.json.annotation.JsonInclude;
import com.remisoft.common.json.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 分页响应体。
 *
 * @param <T> 列表数据类型
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

    public static <T> PageResponse<T> success(Long total, Long pageNum, Long pageSize, T data) {
        Long pages = calcPages(total, pageSize);
        return new PageResponse<>(BaseResponse.SUCCESS_CODE, "ok", total, pageNum, pageSize, pages, data);
    }

    public static <T> PageResponse<T> error(String code, String msg) {
        return new PageResponse<>(code, msg, 0L, 0L, 0L, 0L, null);
    }

    private static Long calcPages(Long total, Long pageSize) {
        if (total == null || total <= 0 || pageSize == null || pageSize <= 0) {
            return 0L;
        }
        return (total + pageSize - 1) / pageSize;
    }
}
