package com.njydsz.common.core.constant;

import com.njydsz.common.core.config.CoreProperties;

/**
 * 分页参数常量
 *
 * <p>定义分页请求的参数名和默认值。
 * 默认值与 {@link CoreProperties} 保持一致，
 * 运行时以 CoreProperties 配置为准，此处常量仅作为编译时兜底默认值。
 *
 * <p><b>配置覆盖：</b>
 * <ul>
 *   <li>{@code ydsz.core.max-page-size} 覆盖 {@link #MAX_PAGE_SIZE}</li>
 *   <li>{@code ydsz.core.default-page-size} 覆盖 {@link #DEFAULT_PAGE_SIZE}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see CoreProperties
 */
public final class PageConstants {

    private PageConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 请求参数名：当前页码 */
    public static final String PAGE_NUM = "pageNum";

    /** 请求参数名：每页记录数 */
    public static final String PAGE_SIZE = "pageSize";

    /** 请求参数名：排序字段 */
    public static final String ORDER_BY_COLUMN = "orderByColumn";

    /** 请求参数名：升序排序 */
    public static final String IS_ASC = "isAsc";

    /** 请求参数名：降序排序 */
    public static final String IS_DESC = "isDesc";

    /** 默认当前页码，与 CoreProperties.defaultPageNum 保持一致 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页记录数，与 CoreProperties.defaultPageSize 保持一致 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大每页记录数，与 CoreProperties.maxPageSize 保持一致 */
    public static final int MAX_PAGE_SIZE = 1000;
}
