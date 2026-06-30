package com.njydsz.pmis.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果
 *
 * @param <T> 数据类型
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "数据列表")
    private List<T> list;

    @Schema(description = "总数")
    private long total;

    @Schema(description = "当前页码")
    private long page;

    @Schema(description = "每页大小")
    private long size;

    @Schema(description = "总页数")
    private long pages;

    public PageResult() {
        this.list = Collections.emptyList();
    }

    public PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size > 0 ? (total + size - 1) / size : 0;
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0, 1, 10);
    }

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }
}
