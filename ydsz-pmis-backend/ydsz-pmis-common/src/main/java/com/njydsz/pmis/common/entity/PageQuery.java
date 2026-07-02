package com.njydsz.pmis.common.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询参数
 *
 * <p>约定：page 从 1 开始；size 默认 10，最大 200。
 * P2-3 新增 Bean Validation 注解，配合 Controller 层 @Valid 防止恶意大分页。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PageQuery implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 每页最大限制（P2-3 安全防护） */
    public static final long MAX_SIZE = 200;

    /** 当前页（从 1 开始） */
    @Min(value = 1, message = "{validation.common.msg_6d2ed876}")
    private long page = 1;

    /** 每页大小 */
    @Min(value = 1, message = "{validation.common.msg_1888441f}")
    @Max(value = MAX_SIZE, message = "{validation.common.msg_7f3e4739}")
    private long size = 10;

    /** 关键字（模糊搜索） */
    private String keyword;

    /** 排序字段 */
    private String orderBy;

    /** 排序方向 asc/desc */
    private String orderDir = "desc";

    /**
     * 计算 SQL 偏移量（page 从 1 开始，对 page/size 做最小值保护，size 做最大值保护）
     *
     * @return 偏移量
     */
    public long offset() {
        return (Math.max(page, 1) - 1) * Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
