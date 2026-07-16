package com.njydsz.common.core.response;

/**
 * 统一响应接口
 *
 * <p>定义了系统 API 统一响应的标准规范。
 * 所有响应类都应实现此接口，以获得一致的响应结构。
 *
 * <p><b>响应结构：</b>
 * <ul>
 *   <li>code: 响应码，A00000表示成功，其他表示失败</li>
 *   <li>msg: 响应消息，用于描述响应结果</li>
 *   <li>data: 响应数据，泛型类型</li>
 * </ul>
 *
 * <p><b>实现类：</b>
 * <ul>
 *   <li>{@link BaseResponse} - 通用响应实现</li>
 *   <li>{@link PageResponse} - 分页响应实现</li>
 * </ul>
 *
 * @param <T> 数据类型
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see BaseResponse
 * @see PageResponse
 */
public interface IResponse<T> {

    /**
     * 获取响应码
     *
     * <p>A00000表示成功，其他表示失败。
     * 失败码的具体含义由业务系统定义。
     *
     * @return 响应码
     */
    String getCode();

    /**
     * 获取响应消息
     *
     * <p>用于描述响应结果或错误信息。
     * 成功时通常返回"操作成功"，失败时返回具体错误描述。
     *
     * @return 响应消息
     */
    String getMsg();

    /**
     * 获取响应数据
     *
     * <p>返回具体的业务数据。
     * 可能为 null，表示无数据返回。
     *
     * @return 响应数据
     */
    T getData();

    /**
     * 判断响应是否成功
     *
     * @return 成功返回true，否则返回false
     */
    boolean isSuccess();
}