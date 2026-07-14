package com.njydsz.pmis.common.exception.code;

/**
 * 统一错误码接口
 *
 * <p>所有业务异常码实现此接口，确保错误码格式统一。
 *
 * <p>错误码格式：[系统码][模块码][错误序号]
 * <ul>
 *   <li>系统码：1-2 位（如 PM=项目管理）</li>
 *   <li>模块码：2 位（如 01=用户, 02=项目, 03=审批）</li>
 *   <li>错误序号：3 位（如 001=参数校验失败, 002=资源不存在）</li>
 * </ul>
 *
 * <p>示例：PM01001 = 项目管理-用户模块-参数校验失败
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
public interface UnifiedErrorCode {

    /**
     * 获取错误码
     *
     * @return 错误码字符串
     */
    String getCode();

    /**
     * 获取国际化消息键
     *
     * @return 消息键
     */
    String getKey();

    /**
     * 获取 HTTP 状态码
     *
     * @return HTTP 状态码
     */
    int getHttpStatus();

    /**
     * 获取错误描述
     *
     * @return 错误描述
     */
    String getDescription();
}
