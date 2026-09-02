package com.njydsz.userinfo.server.service;

import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;

/**
 * 用户生命周期状态机服务接口（P2-3）。
 *
 * <p>提供单用户状态流转能力，涵盖 PENDING → ENABLED → SUSPENDED/DISABLED → RESIGNED 完整链路。
 * 所有流转均通过 {@link UserLifecycleStatusEnum#canTransitTo} 进行前置校验，
 * 杜绝非法流转（如终态 REISIGNED 反向流转）。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>状态变更自动发布领域事件，供审计日志和下游订阅方（如搜索索引同步、权限回收）使用</li>
 *   <li>终态 {@link UserLifecycleStatusEnum#RESIGNED} 不允许任何流出流转</li>
 *   <li>幂等性：流转到当前状态时不报错，直接返回（兼容前端重复提交）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UserLifecycleService {

  /**
   * 激活账号（PENDING → ENABLED）。
   *
   * <p>适用于邮箱/手机验证通过后的正式启用场景。
   *
   * @param userId 用户 ID
   * @return 流转后的目标状态
   */
  UserLifecycleStatusEnum activate(String userId);

  /**
   * 暂停账号（ENABLED → SUSPENDED）。
   *
   * <p>临时停用，可由管理员恢复。暂停期间禁止登录但不触发会话驱逐。
   *
   * @param userId 用户 ID
   * @return 流转后的目标状态
   */
  UserLifecycleStatusEnum suspend(String userId);

  /**
   * 恢复账号（SUSPENDED → ENABLED）。
   *
   * <p>从暂停状态恢复到正常启用状态。
   *
   * @param userId 用户 ID
   * @return 流转后的目标状态
   */
  UserLifecycleStatusEnum resume(String userId);

  /**
   * 禁用账号（ENABLED/SUSPENDED → DISABLED）。
   *
   * <p>长期禁用，通常伴随会话驱逐。仅 ENABLED 状态可重新启用。
   *
   * @param userId 用户 ID
   * @return 流转后的目标状态
   */
  UserLifecycleStatusEnum disable(String userId);

  /**
   * 启用账号（DISABLED → ENABLED）。
   *
   * <p>从禁用状态恢复到正常启用状态。
   *
   * @param userId 用户 ID
   * @return 流转后的目标状态
   */
  UserLifecycleStatusEnum enable(String userId);

  /**
   * 账号离职（ENABLED/SUSPENDED → RESIGNED）。
   *
   * <p>终态操作，执行后账号永久不可再激活。同时触发全量会话驱逐和数据归档。
   *
   * @param userId 用户 ID
   * @return 流转后的目标状态（始终为 RESIGNED）
   */
  UserLifecycleStatusEnum resign(String userId);

  /**
   * 通用状态流转（核心方法）。
   *
   * <p>所有具体流转方法（activate/suspend/resume/disable/enable/resign）最终委托此方法执行。
   * 包含完整的状态校验、幂等判断、持久化和事件发布。
   *
   * @param userId 用户 ID
   * @param target 目标状态
   * @return 流转后的目标状态
   * @throws com.njydsz.common.exception.custom.BusinessException 用户不存在或非法流转时抛出
   */
  UserLifecycleStatusEnum transition(String userId, UserLifecycleStatusEnum target);
}
