package com.remisoft.common.core.constant;

/**
 * 分页参数常量。
 */
public final class PageConstants {

    private PageConstants() {
    }

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 5000;

    /**
     * 归一化页大小（1 ~ MAX_PAGE_SIZE）。
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 归一化页码（<=1 视为第 1 页）。
     */
    public static int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 计算 LIMIT offset。
     */
    public static long calcOffset(Integer pageNum, Integer pageSize) {
        return (long) (normalizePageNum(pageNum) - 1) * normalizePageSize(pageSize);
    }
}
