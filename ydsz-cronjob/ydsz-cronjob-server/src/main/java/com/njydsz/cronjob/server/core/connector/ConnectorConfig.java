package com.njydsz.cronjob.server.core.connector;

import java.util.Map;

import lombok.Data;

/**
 * 连接器配置（P2-3）。
 *
 * <p>包含连接外部调度系统所需的所有配置信息。
 *
 * @param endpoint 外部系统端点 URL
 * @param authType 认证类型（BASIC / TOKEN / AK_SK / NONE）
 * @param username 用户名（BASIC 认证使用）
 * @param password 密码/Token（BASIC / TOKEN 认证使用）
 * @param accessKey Access Key（AK_SK 认证使用）
 * @param secretKey Secret Key（AK_SK 认证使用）
 * @param extraProps 额外配置属性（连接器实现特定）
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ConnectorConfig {
  /** 外部系统端点 URL */
  private String endpoint;

  /** 认证类型: BASIC / TOKEN / AK_SK / NONE */
  private String authType = "TOKEN";

  /** 用户名 */
  private String username;

  /** 密码/Token */
  private String password;

  /** Access Key */
  private String accessKey;

  /** Secret Key */
  private String secretKey;

  /** 额外配置属性 */
  private Map<String, String> extraProps;

  /** 连接超时（秒） */
  private int connectTimeoutSeconds = 10;

  /** 读取超时（秒） */
  private int readTimeoutSeconds = 30;
}
