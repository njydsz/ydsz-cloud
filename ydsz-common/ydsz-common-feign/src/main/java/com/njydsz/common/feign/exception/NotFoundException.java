package com.njydsz.common.feign.exception;

import lombok.Getter;
import lombok.NoArgsConstructor;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * 资源未找到异常。
 *
 * <p>当 Feign 客户端收到 HTTP 404（Not Found）状态码时抛出此异常， 表示请求的资源在下游服务中不存在。
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>根据 ID 查询资源，但资源不存在
 *   <li>请求的接口路径不存在
 *   <li>请求的文件或配置资源不存在
 * </ul>
 *
 * <p>错误消息示例：
 *
 * <pre>
 * Feign 调用失败, method: UserService#getUser, request: GET http://user-service/api/user/999,
 * status: 404, reason: Not Found, body: {"code":"100404","msg":"用户不存在"}
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@NoArgsConstructor
public class NotFoundException extends SysException {

  private static final long serialVersionUID = 1L;

  private static final String DEFAULT_ERROR_CODE = "ERROR";

  /** 错误码。 */
  private String code = "ERROR";

  /** 附加数据。 */
  private transient Object data;

  /**
   * 创建资源未找到异常。
   *
   * @param message 异常消息
   */
  public NotFoundException(String message) {
    super(CoreExceptionCode.NOT_FOUND);
    this.code = DEFAULT_ERROR_CODE;
    setMessage(message);
  }

  /**
   * 创建资源未找到异常。
   *
   * @param message 异常消息
   * @param cause 原因异常
   */
  public NotFoundException(String message, Throwable cause) {
    super(CoreExceptionCode.NOT_FOUND, cause);
    this.code = DEFAULT_ERROR_CODE;
    setMessage(message);
  }

  /**
   * 设置错误码。
   *
   * @param code 错误码
   */
  public void setCode(String code) {
    this.code = code;
  }

  /**
   * 设置附加数据。
   *
   * @param data 附加数据
   */
  public void setData(Object data) {
    this.data = data;
  }
}
