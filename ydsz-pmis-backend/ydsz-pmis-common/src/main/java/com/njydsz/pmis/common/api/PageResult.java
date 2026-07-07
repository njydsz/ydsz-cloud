package com.njydsz.pmis.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final String serialVersionUID = "1";

    /** 数据列表 */
    @Schema(description = "数据列表")
    private List<T> list;

    /** 总数 */
    @Schema(description = "总数")
    private long total;

    /** 当前页码 */
    @Schema(description = "当前页码")
    private long page;

    /** 每页大小 */
    @Schema(description = "每页大小")
    private long size;

    /** 总页数 */
    @Schema(description = "总页数")
    private long pages;

    /**
     * 默认构造方法，初始化空列表
     */
    public PageResult() {
        this.list = Collections.emptyList();
    }

    /**
     * 全参构造方法，自动计算总页数
     *
     * @param list  数据列表
     * @param total 总数
     * @param page  当前页码
     * @param size  每页大小
     */
    public PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size > 0 ? (total + size - 1) / size : 0;
    }

    /**
     * 构建空分页结果（默认第 1 页，每页 10 条）
     *
     * @param <T> 数据类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0, 1, 10);
    }

    /**
     * 构建分页结果
     *
     * @param list  数据列表
     * @param total 总数
     * @param page  当前页码
     * @param size  每页大小
     * @param <T>   数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }

    /**
     * 从 MyBatis-Plus Page 转 PageResult
     *
     * @param p   MyBatis-Plus 分页对象，为 null 时返回空结果
     * @param <T> 数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> ofPage(Page<T> p) {
        if (p == null) {
            return empty();
        }
        return new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }
}
