package com.njydsz.userinfo.domain.provision;

/**
 * 身份供给运行时异常（P0-1 Identity Provisioning 管道）。
 *
 * <p>封装连接器执行过程中的外部源不可达、数据格式不符、权限不足等异常，
 * 由 {@link IdentityProvisionConnector} 实现类抛出，上层编排器捕获后转换为告警。
 *
 * <p>该异常为 domain 层异常，不依赖 Spring 或任何框架。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public class ProvisionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 连接器类型标识 */
  private final String connectorType;

  /**
   * 构造供给异常。
   *
   * @param connectorType 连接器类型
   * @param message 错误描述
   */
  public ProvisionException(String connectorType, String message) {
    super(message);
    this.connectorType = connectorType;
  }

  /**
   * 构造供给异常（含根因）。
   *
   * @param connectorType 连接器类型
   * @param message 错误描述
   * @param cause 根因异常
   */
  public ProvisionException(String connectorType, String message, Throwable cause) {
    super(message, cause);
    this.connectorType = connectorType;
  }

  /**
   * 获取连接器类型标识。
   *
   * @return 连接器类型字符串
   */
  public String getConnectorType() {
    return connectorType;
  }
}
