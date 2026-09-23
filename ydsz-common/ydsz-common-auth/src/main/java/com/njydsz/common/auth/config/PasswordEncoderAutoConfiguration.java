package com.njydsz.common.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器自动配置（P1-7 安全组件收敛）。
 *
 * <p>全平台统一提供唯一的 BCrypt {@link PasswordEncoder} Bean，消除各业务模块
 * （原 ydsz-system / ydsz-userinfo）各自注册 {@code BCryptPasswordEncoder} 导致的
 * 双 Bean、强度配置键不一致问题。
 *
 * <p>强度通过 {@code ydsz.auth.bcrypt-strength} 配置（合法范围 4-31，越界回退默认 10）。
 * 业务模块如需自定义实现，可自行注册 {@code PasswordEncoder} Bean（本配置声明了
 * {@code @ConditionalOnMissingBean}，会自动让位）。
 *
 * <p>注意：BCrypt 为<b>单向</b>哈希，同一明文每次加密结果不同（盐值随机），
 * 校验必须使用 {@code matches()} 而非字符串比对。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@AutoConfiguration
@ConditionalOnClass(BCryptPasswordEncoder.class)
public class PasswordEncoderAutoConfiguration {

  /** BCrypt 强度下限 */
  private static final int MIN_STRENGTH = 4;

  /** BCrypt 强度上限 */
  private static final int MAX_STRENGTH = 31;

  /**
   * 平台统一 BCrypt 密码编码器。
   *
   * @param authProperties 认证配置（读取 bcrypt-strength）
   * @return PasswordEncoder 实例
   */
  @Bean
  @ConditionalOnMissingBean(PasswordEncoder.class)
  public PasswordEncoder passwordEncoder(AuthProperties authProperties) {
    int strength = authProperties.getBcryptStrength();
    if (strength < MIN_STRENGTH || strength > MAX_STRENGTH) {
      strength = 10;
    }
    return new BCryptPasswordEncoder(strength);
  }
}
