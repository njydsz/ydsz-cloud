package com.njydsz.common.notify.security;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.njydsz.common.config.cli.ConfigCliTool;
import com.njydsz.common.notify.config.NotifyProperties;

/**
 * 通知模块密码解析器（P0-1：敏感信息加密）
 *
 * <p>当 EmailConfig.security.passwordEncrypted=true 时，自动通过 Jasypt 解密 SMTP 密码/授权码。 密文格式为 {@code
 * ENC(密文)}，解密密钥从 {@code security.jasyptKey} 或环境变量 {@code JASYPT_ENCRYPTOR_PASSWORD} 获取。
 *
 * <p><b>安全建议：</b>生产环境通过环境变量或 KMS 注入 jasyptKey，不要硬编码在配置文件中。
 *
 * <p><b>P0-1 重构说明：</b>加密器创建统一委托给 {@link ConfigCliTool#createEncryptor(String)}， 消除与
 * ydsz-common-config 模块的重复 Jasypt 参数配置，确保 CLI 与运行时的加密行为一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NotifyPasswordResolver {

  private static final Logger log = LoggerFactory.getLogger(NotifyPasswordResolver.class);

  private static final String ENC_PREFIX = "ENC(";
  private static final String ENC_SUFFIX = ")";

  private final NotifyProperties properties;
  private volatile PooledPBEStringEncryptor encryptor;
  private volatile boolean initialized = false;

  public NotifyPasswordResolver(NotifyProperties properties) {
    this.properties = properties;
  }

  /**
   * 解析密码：如果是加密密文则自动解密，否则原样返回
   *
   * @param rawPassword 原始密码（可能是明文或 ENC(密文) 格式）
   * @return 解密后的明文密码
   */
  public String resolvePassword(String rawPassword) {
    if (rawPassword == null || rawPassword.isEmpty()) {
      return rawPassword;
    }
    if (!isEncrypted(rawPassword)) {
      return rawPassword;
    }
    return decrypt(rawPassword);
  }

  /**
   * 判断密码是否为加密格式
   *
   * @param password 密码字符串
   * @return true 表示是 ENC(xxx) 加密格式
   */
  public static boolean isEncrypted(String password) {
    return password != null && password.startsWith(ENC_PREFIX) && password.endsWith(ENC_SUFFIX);
  }

  /**
   * 解密密码
   *
   * @param encryptedPassword 加密密码（ENC(xxx) 格式）
   * @return 解密后的明文密码
   */
  private String decrypt(String encryptedPassword) {
    String cipherText =
        encryptedPassword.substring(
            ENC_PREFIX.length(), encryptedPassword.length() - ENC_SUFFIX.length());
    try {
      PooledPBEStringEncryptor enc = getEncryptor();
      if (enc == null) {
        log.warn("[NotifyPasswordResolver] Jasypt 未配置，无法解密密码，返回原始密文");
        return encryptedPassword;
      }
      return enc.decrypt(cipherText);
    } catch (Exception e) {
      log.error("[NotifyPasswordResolver] 密码解密失败: {}", e.getMessage());
      throw new IllegalStateException("SMTP 密码解密失败，请检查 jasyptKey 配置", e);
    }
  }

  /**
   * 懒加载初始化 Jasypt 加密器
   *
   * <p>统一委托给 {@link ConfigCliTool#createEncryptor(String)} 创建加密器， 算法参数（PBEWithHMACSHA512AndAES_256
   * / 迭代次数 1000 / 池大小 4）由 common-config 统一管理， 此处不再重复配置。
   *
   * @return PooledPBEStringEncryptor 实例，未配置密钥时返回 null
   */
  private PooledPBEStringEncryptor getEncryptor() {
    if (initialized) {
      return encryptor;
    }
    synchronized (this) {
      if (initialized) {
        return encryptor;
      }
      NotifyProperties.EmailConfig email = properties.getEmail();
      if (email == null || email.getSecurity() == null) {
        initialized = true;
        return null;
      }
      String key = email.getSecurity().getJasyptKey();
      if (!StringUtils.hasText(key)) {
        key = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
      }
      if (!StringUtils.hasText(key)) {
        log.warn(
            "[NotifyPasswordResolver] Jasypt 密钥未配置（jasyptKey 或 JASYPT_ENCRYPTOR_PASSWORD），跳过解密");
        initialized = true;
        return null;
      }
      // P0-1: 委托 ConfigCliTool 创建加密器，消除重复 Jasypt 参数配置
      encryptor = ConfigCliTool.createEncryptor(key);
      initialized = true;
      log.info("[NotifyPasswordResolver] Jasypt 加密器初始化完成（委托 ConfigCliTool）");
      return encryptor;
    }
  }
}
