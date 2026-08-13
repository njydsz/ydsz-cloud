package com.njydsz.common.exception.core;
import java.util.List;

/**
 *批量操作异常标记接口。
 *
 * <p>实现此接口的异常表示批量操作中部分成功部分失败的场景，
 * 全局异常处理器将据此返回 HTTP 207 Multi-Status 响应。
 *
 * <p>实现类应提供成功子项和失败子项的列表，供响应序列化使用。
 *
 * @author ydsz-team
 * @since 2.3.0
 */
public interface IBatchException {

    /**
     * 获取成功的子项 ID 列表
     *
     * @return 成功子项列表
     */
    List<Object> getSuccessItems();

    /**
     * 获取失败的子项详情列表
     *
     * @return 失败子项列表
     */
    List<?> getFailureItems();

    /**
     * 判断是否存在失败子项
     *
     * @return 有失败子项返回 true
     */
    boolean hasFailures();

    /**
     * 获取成功数量
     *
     * @return 成功子项数量
     */
    int getSuccessCount();

    /**
     * 获取失败数量
     *
     * @return 失败子项数量
     */
    int getFailureCount();
}
