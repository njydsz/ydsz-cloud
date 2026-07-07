package com.njydsz.pmis.common.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 游标分页查询参数（P2-8 深翻优化）
 *
 * <p>传统 OFFSET 分页在深翻（如第 1000 页）时需扫描并丢弃前 N 条记录，
 * 性能随页码线性下降。游标分页（keyset pagination）通过 {@code WHERE (sort_col, id) < (cursor_val, cursor_id)}
 * 直接定位起始位置，复杂度 O(1) 与页码无关。
 *
 * <p>使用方式：
 * <ol>
 *   <li>首次请求不传 cursor，返回第一页数据 + nextCursor</li>
 *   <li>后续请求传入上一次返回的 nextCursor，获取下一页</li>
 *   <li>nextCursor 为 null 时表示已到最后一页</li>
 * </ol>
 *
 * <p>cursor 为 Base64 编码的 opaque 字符串，前端不应解析其内容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class CursorPageQuery implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 每页最大限制 */
    public static final long MAX_SIZE = 200;

    /** 每页大小（默认 20，最大 200） */
    @Min(value = 1, message = "{validation.common.msg_1888441f}")
    @Max(value = MAX_SIZE, message = "{validation.common.msg_7f3e4739}")
    private long size = 20;

    /** 游标（上一页最后一条记录的 token，首次请求不传） */
    private String cursor;

    /**
     * 获取受限的 size（防止恶意大分页）
     *
     * @return clamped size
     */
    public long safeSize() {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
