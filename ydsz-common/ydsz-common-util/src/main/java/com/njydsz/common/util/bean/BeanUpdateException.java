package com.njydsz.common.util.bean;

/**
 * Bean 更新操作异常（unchecked）。
 *
 * <p>当 {@link BeanUpdateUtil} 的复制、内省操作失败时抛出。Bean 内省异常（{@link
 * java.beans.IntrospectionException}）和反射调用异常均包装为此类型。
 *
 * @author ydsz-team
 * @since 26.10.03
 */
public class BeanUpdateException extends RuntimeException {

  /** 序列化版本号。 */
  private static final long serialVersionUID = 1L;

  /**
   * 构造 Bean 更新异常。
   *
   * @param message 异常描述信息
   * @param cause 根因异常
   */
  public BeanUpdateException(String message, Throwable cause) {
    super(message, cause);
  }
}
