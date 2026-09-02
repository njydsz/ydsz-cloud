package com.njydsz.common.safe.encrypt;

/**
 * 字段解密失败时的处理策略
 *
 * <p>当 {@link EncryptTypeHandler} 在读取数据库字段时调用 {@link FieldEncryptionService#decrypt(String)}
 * 抛出异常（密钥版本缺失、GCM 认证失败、Base64 解码失败、密文被篡改等场景）时的处理方式。
 *
 * <p><b>安全说明：</b>
 *
 * <ul>
 *   <li>{@link #THROW}（默认）— 失败安全（fail-safe）：直接抛出异常，阻止读取链路继续， 避免把不可信的明文当作敏感数据返回给上层业务。
 *   <li>{@link #RETURN_MASKED} — 失败降级：返回 {@code maskedValue}（默认 {@code ****}），
 *       适用于「宁可展示打码值也不让链路中断」的场景（如列表页展示）。
 *   <li>{@link #RETURN_ORIGINAL} — <b>不推荐</b>：返回原值。仅用于历史明文数据兼容场景， 会在日志中记录 WARN
 *       级别告警。新接入的加密字段不应使用此策略。
 * </ul>
 *
 * <p><b>大厂实践对标：</b>阿里 / 腾讯 / 字节内部字段加密组件默认采用 THROW 策略； 对于历史明文兼容场景，应通过 {@link
 * EncryptTypeHandler#looksLikeCiphertext(String)} 自动识别密文格式，而不是无差别地返回原值。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum DecryptFailureStrategy {

  /** 抛出异常（默认，fail-safe） */
  THROW,

  /** 返回打码值（如 {@code ****}） */
  RETURN_MASKED,

  /** 返回原值（不推荐，仅用于历史明文兼容） */
  RETURN_ORIGINAL
}
