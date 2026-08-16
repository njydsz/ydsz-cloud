package com.njydsz.common.safe.encrypt;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 字段加密配置属性
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   safe:
 *     field-encryption:
 *       enabled: true
 *       default-key-version: 2
 *       failure-strategy: THROW
 *       masked-value: "****"
 *       keys:
 *         1: "base64-encoded-32-byte-key..."
 *         2: "base64-encoded-32-byte-key..."
 * </pre>
 *
 * @author ydsz-team
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.field-encryption")
public class EncryptFieldProperties {

  /** 是否启用字段加密（默认 true） */
  private boolean enabled = true;

  /** 默认密钥版本 */
  private int defaultKeyVersion = 1;

  /**
   * 解密失败时的处理策略（默认 THROW，fail-safe）
   *
   * <p>当数据库中存储的密文无法被正确解密时（GCM 认证失败 / 密钥版本缺失 / 密文被篡改 / Base64 解码失败）， 按此策略处理：
   *
   * <ul>
   *   <li>{@link DecryptFailureStrategy#THROW}（默认）— 直接抛异常，阻止读取链路继续；
   *   <li>{@link DecryptFailureStrategy#RETURN_MASKED} — 返回 {@link #maskedValue}（默认 {@code ****}）；
   *   <li>{@link DecryptFailureStrategy#RETURN_ORIGINAL} — 返回原值（不推荐，仅用于历史明文兼容）。
   * </ul>
   *
   * <p><b>注意：</b>非密文格式的历史明文数据会自动识别并原样返回，不触发此策略。 此策略仅用于「值看起来像密文但解密失败」的场景。
   */
  private DecryptFailureStrategy failureStrategy = DecryptFailureStrategy.THROW;

  /**
   * 当 {@link #failureStrategy} = {@link DecryptFailureStrategy#RETURN_MASKED} 时返回的脱敏值
   *
   * <p>默认 {@code ****}，可根据业务需要自定义（如 {@code ***}、{@code [DECRYPT_FAILED]} 等）。
   */
  private String maskedValue = "****";

  /**
   * 密钥映射（版本号 -> Base64 编码的 32 字节密钥）
   *
   * <p>AES-256 要求密钥长度为 32 字节（256 位）。 生成密钥示例：
   *
   * <pre>{@code
   * KeyGenerator keyGen = KeyGenerator.getInstance("AES");
   * keyGen.init(256);
   * SecretKey key = keyGen.generateKey();
   * String base64Key = Base64.getEncoder().encodeToString(key.getEncoded());
   * }</pre>
   */
  private Map<Integer, String> keys = new LinkedHashMap<>();
}
