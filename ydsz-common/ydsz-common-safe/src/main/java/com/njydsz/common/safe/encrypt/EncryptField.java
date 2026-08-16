package com.njydsz.common.safe.encrypt;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级加密注解
 *
 * <p>标注在实体类字段上，MyBatis 持久化时自动加密，查询时自动解密。 支持 AES-256-GCM 加密算法，密钥由配置统一管理。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * public class User {
 *     @EncryptField
 *     private String idCard;  // 身份证号加密存储
 *
 *     @EncryptField
 *     private String phone;   // 手机号加密存储
 * }
 * }</pre>
 *
 * <p><b>配合 MyBatis 使用：</b>
 *
 * <pre>{@code
 * @TableName(autoResultMap = true)
 * public class User {
 *     @TableField(typeHandler = EncryptTypeHandler.class)
 *     @EncryptField
 *     private String idCard;
 * }
 * }</pre>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>AES-256-GCM 认证加密，防篡改
 *   <li>每次加密使用随机 IV，相同明文产生不同密文
 *   <li>密钥通过配置中心管理，支持密钥轮换
 * </ul>
 *
 * @author ydsz-team
 * @author ydsz-team
 * @since 1.0.0
 * @see EncryptTypeHandler
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EncryptField {

  /**
   * 加密算法（默认 AES-256-GCM）
   *
   * @return 加密算法名称
   */
  String algorithm() default "AES-256-GCM";

  /**
   * 是否启用加密（默认 true）
   *
   * <p>设置为 false 可临时禁用加密，字段以明文存储。
   *
   * @return 是否启用
   */
  boolean enabled() default true;

  /**
   * 密钥版本（用于密钥轮换）
   *
   * <p>当密钥更新时，可通过版本号区分新旧密钥加密的数据。 解密时根据版本号选择对应密钥。
   *
   * @return 密钥版本号，默认 1
   */
  int keyVersion() default 1;
}
