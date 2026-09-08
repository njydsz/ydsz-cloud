package com.njydsz.common.web.lock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.lock.idempotent.RepeatSubmitTokenService;
import com.njydsz.common.lock.spi.CurrentUserIdResolver;

/**
 * Lock Web 层自动配置（ydsz-common-web 专有）
 *
 * <p>将原来位于 ydzz-common-lock（L4）中 {@code DistributedLockAutoConfiguration} 的
 * {@code RepeatSubmitTokenController} Bean 迁移至此，承担 Web 层契约职责。
 *
 * <p>依赖条件：
 * <ul>
 *   <li>ydsz-common-lock 在 classpath（提供 RepeatSubmitTokenService）
 *   <li>RepeatSubmitTokenService Bean 存在（即 lock 能力启用）
 *   <li>应用为 Web SERVLET 模式
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@AutoConfiguration
@ConditionalOnClass(RepeatSubmitTokenService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LockWebAutoConfiguration {

  /**
   * 创建重复提交 Token 控制器
   *
   * <p>仅当 {@link RepeatSubmitTokenService} Bean 存在时才装配，
   * 避免非 lock 使用方的 web 应用被强制加载。
   *
   * @param tokenService Token 服务
   * @param userIdResolver 当前用户 ID 解析器
   * @return RepeatSubmitTokenController 实例
   */
  @Bean
  @ConditionalOnBean(RepeatSubmitTokenService.class)
  public RepeatSubmitTokenController repeatSubmitTokenController(
      RepeatSubmitTokenService tokenService, CurrentUserIdResolver userIdResolver) {
    return new RepeatSubmitTokenController(tokenService, userIdResolver);
  }
}
