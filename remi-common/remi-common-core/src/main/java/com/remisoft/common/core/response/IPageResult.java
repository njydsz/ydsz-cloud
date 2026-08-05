package com.remisoft.common.core.response;

import java.util.List;

/**
 * 分页结果抽象（跨层桥接接口）
 *
 * <p>允许 {@link PageResponse} 直接桥接来自任何实现了此接口的分页数据源，
 * 无需强制依赖 domain 层的 {@code PageResult}。
 *
 * <p>Domain 层的 {@code PageResult}、{@code Page<T>} 等分页对象应实现本接口，
 * 从而通过 {@link PageResponse#from(IPageResult)} 一行代码完成转换。
 *
 * <p>设计考量：
 * <ul>
 *   <li>core 模块不反向依赖 domain 层（避免循环依赖）</li>
 *   <li>返回原始 List<?> 而非泛型 List\<T\>，便于 domain 层灵活适配</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.8.0
 * @see PageResponse#from(IPageResult)
 */
public interface IPageResult {

    /**
     * 当前页数据列表
     *
     * @return 数据列表，不会为 null（空页返回空 List）
     */
    List<?> records();

    /**
     * 总记录数
     *
     * @return 总记录数，>= 0
     */
    long total();

    /**
     * 当前页码（从 1 开始）
     *
     * @return 当前页码，>= 1
     */
    long pageNum();

    /**
     * 每页记录数
     *
     * @return 每页记录数，>= 1
     */
    long pageSize();
}
