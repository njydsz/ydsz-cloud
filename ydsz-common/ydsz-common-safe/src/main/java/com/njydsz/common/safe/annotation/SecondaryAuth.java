package com.njydsz.common.safe.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 场景化二级认证注解（P0-2 标准化）。
 *
 * <p>标记在 Controller 方法上，表示该操作需要在指定场景下通过二级认证（密码确认）后才能执行。
 * 与 {@link SensitiveOperation} 的区别：{@code SecondaryAuth} 支持<b>场景隔离</b>（不同业务场景独立验证），
 * 而 {@code SensitiveOperation} 是全局单一验证标记。
 *
 * <p><b>典型场景：</b>
 *
 * <ul>
 *   <li>{@code password_change} — 修改密码前验证
 *   <li>{@code role_assign} — 分配角色前验证
 *   <li>{@code data_export} — 数据导出前验证
 *   <li>{@code tenant_config} — 租户配置变更前验证
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @SecondaryAuth(scene = "password_change", level = SensitiveLevel.HIGH)
 * @PutMapping("/api/v1/user/password")
 * public YdszResponse<Void> changePassword(@RequestBody ChangePasswordDTO dto) {
 *     // 业务逻辑
 * }
 * }</pre>
 *
 * <p><b>验证流程：</b>
 *
 * <ol>
 *   <li>前端先调用 {@code /api/v1/auth/secondary-auth} 接口，传入场景标识和当前用户密码
 *   <li>后端校验密码通过后，在 Redis 中写入场景化的安全标记（Key: {@code userinfo:safe:{scene}:{userId}}）
 *   <li>请求到达目标方法时，AOP 切面检查对应场景的 Redis 标记是否存在且有效
 *   <li>未通过验证时抛出 {@code SECONDARY_AUTH_REQUIRED} 异常
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SensitiveOperation 全局敏感操作验证（无场景隔离）
 * @see com.njydsz.common.safe.annotation.SensitiveLevel 敏感操作等级
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SecondaryAuth {

  /**
   * 场景标识（scene）。
   *
   * <p>用于区分不同业务场景的二级认证，每个场景独立验证、独立过期。
   * 同一用户在不同场景下的二级认证互不影响。
   *
   * <p>常用场景：{@code password_change}、{@code role_assign}、{@code data_export}、{@code tenant_config}
   *
   * @return 场景标识字符串
   */
  String scene();

  /**
   * 二级认证有效期（秒）。
   *
   * <p>默认{@code 300}秒（5分钟），与 Sa-Token 二级认证的默认时效保持一致。
   * 需要更短时效的极敏感操作可通过 {@link #level()} 自动缩短。
   *
   * @return 有效期（秒）
   */
  int ttlSeconds() default 300;

  /**
   * 敏感操作等级（P0-2 差异化时效）。
   *
   * <p>{@link SensitiveLevel#CRITICAL} 级别时，实际生效 TTL 缩短为 {@link #ttlSeconds()} 的 40%
   * （最小 60 秒），降低极敏感操作的风险窗口。
   *
   * @return 敏感操作等级，默认 {@link SensitiveLevel#HIGH}
   */
  SensitiveLevel level() default SensitiveLevel.HIGH;

  /**
   * 操作描述（用于审计日志）。
   *
   * @return 操作描述文本
   */
  String value() default "";
}
