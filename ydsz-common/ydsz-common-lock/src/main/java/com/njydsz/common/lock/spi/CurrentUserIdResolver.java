package com.njydsz.common.lock.spi;

/**
 * 当前用户 ID 解析器 SPI 接口
 *
 * <p>用于解耦 {@code ydsz-common-lock} 与上游认证模块（{@code ydsz-common-auth}）， 打破循环依赖。业务服务层实现此接口后注入到需要获取当前用户
 * ID 的组件 （如 {@code RepeatSubmitTokenService}）。
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>lock 模块（基础设施层）定义接口，不依赖上层业务模块
 *   <li>auth 模块（业务服务层）提供实现，遵循依赖倒置原则
 *   <li>未注入实现时，相关功能降级为非用户绑定模式（如未登录用户跳过校验）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@FunctionalInterface
public interface CurrentUserIdResolver {

  /**
   * 获取当前登录用户 ID
   *
   * @return 用户 ID，未登录或无上下文时返回 null
   */
  String getCurrentUserId();
}
