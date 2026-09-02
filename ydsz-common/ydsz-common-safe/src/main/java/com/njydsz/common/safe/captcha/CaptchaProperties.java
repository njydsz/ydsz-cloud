package com.njydsz.common.safe.captcha;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码配置属性（P1-12：统一验证码能力下沉到 common-safe）。
 *
 * <p>供 {@link CaptchaGenerator} 使用，业务模块通过配置自定义验证码行为。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   safe:
 *     captcha:
 *       enabled: true
 *       ttl-seconds: 300
 *       code-length: 4
 *       image-width: 120
 *       image-height: 40
 *       key-prefix: auth:captcha:
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.captcha")
public class CaptchaProperties {

  /** 是否启用验证码功能 */
  private boolean enabled = true;

  /** 验证码有效期（秒） */
  private long ttlSeconds = 300;

  /** 验证码字符长度 */
  private int codeLength = 4;

  /** 验证码图片宽度 */
  private int imageWidth = 120;

  /** 验证码图片高度 */
  private int imageHeight = 40;

  /** Redis 存储 key 前缀 */
  private String keyPrefix = "auth:captcha:";

  /**
   * 验证码字符集（去除易混淆字符 0/o/1/l/I）。
   *
   * <p>包含大写字母、小写字母、数字，不包括 0/o/1/l/I 等易混淆字符。
   */
  private String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
}
