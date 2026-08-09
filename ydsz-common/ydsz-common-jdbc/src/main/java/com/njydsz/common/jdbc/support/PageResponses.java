package com.njydsz.common.jdbc.support;

import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.core.response.BaseResponse;

/**
 * MyBatis-Plus 分页结果转统一分页响应的转换工具。
 *
 * <p>将 MyBatis-Plus 的 {@link IPage} 分页查询结果转换为
 * {@link BaseResponse} 统一响应体，避免各业务模块重复编写
 * {@code total / pageNum / pageSize} 的样板转换代码。
 *
 * <p><b>返回值语义：</b>响应体包含 {@code total / pageNum / pageSize} 三个分页字段，
 * data 字段携带当前页记录列表。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * IPage<UserVO> page = userMapper.selectPage(query, pageParam);
 * return PageResponses.success(page);
 * }</pre>
 *
 * <p><b>与 core 模块的关系：</b>core 模块保持零依赖原则（不引入 MyBatis-Plus），
 * 因此本工具位于 jdbc 模块（MyBatis-Plus 依赖的宿主模块）。
 * 业务模块同时依赖 core + jdbc 时即可使用。</p>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see BaseResponse
 * @see IPage
 */
public final class PageResponses {

    private PageResponses() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将 MyBatis-Plus 分页结果转换为成功分页响应。
     *
     * <p>自动提取 total / pageNum / pageSize，并携带记录列表。</p>
     *
     * @param page MyBatis-Plus 分页结果（含记录、总数、页码信息）
     * @param <T>  记录类型
     * @return 成功分页响应
     */
    public static <T> BaseResponse<List<T>> success(IPage<T> page) {
        if (page == null) {
            return BaseResponse.emptyPage(1L, 0L);
        }
        long total = page.getTotal();
        long pageNum = page.getCurrent();
        long pageSize = page.getSize();
        List<T> records = page.getRecords() != null ? page.getRecords() : Collections.emptyList();
        return BaseResponse.successPage(total, pageNum, pageSize, records);
    }

    /**
     * 将 MyBatis-Plus 分页结果转换为成功分页响应（记录经映射函数转换）。
     *
     * <p>适用于分页查询结果需要 DO → VO 转换的场景，
     * 避免先取出 records 再单独 map 的样板代码。</p>
     *
     * @param page     MyBatis-Plus 分页结果
     * @param mapper   记录映射函数（DO → VO）
     * @param <S>      源记录类型（如 DO）
     * @param <T>      目标记录类型（如 VO）
     * @return 成功分页响应（records 为转换后的结果）
     */
    public static <S, T> BaseResponse<List<T>> success(IPage<S> page, java.util.function.Function<S, T> mapper) {
        if (page == null) {
            return BaseResponse.emptyPage(1L, 0L);
        }
        List<S> records = page.getRecords() != null ? page.getRecords() : Collections.emptyList();
        List<T> mapped = records.stream().map(mapper).toList();
        return BaseResponse.successPage(page.getTotal(), page.getCurrent(), page.getSize(), mapped);
    }
}
