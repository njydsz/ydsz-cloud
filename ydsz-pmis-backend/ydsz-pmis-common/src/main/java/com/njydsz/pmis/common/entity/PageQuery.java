package com.njydsz.pmis.common.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询参数
 *
 * <p>约定：page 从 1 开始；size 默认 10，最大 200。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页（从 1 开始） */
    private long page = 1;

    /** 每页大小 */
    private long size = 10;

    /** 关键字（模糊搜索） */
    private String keyword;

    /** 排序字段 */
    private String orderBy;

    /** 排序方向 asc/desc */
    private String orderDir = "desc";

    public long offset() {
        return (Math.max(page, 1) - 1) * Math.max(size, 1);
    }
}
