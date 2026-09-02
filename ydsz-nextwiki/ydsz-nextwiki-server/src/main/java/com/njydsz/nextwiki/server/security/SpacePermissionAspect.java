package com.njydsz.nextwiki.server.security;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.SpaceMemberRepository;

/**
 * 空间权限校验 AOP 切面（S4-P3-03）。
 *
 * <p>拦截标注了 {@link SpacePermission} 注解的方法，自动提取空间 ID 参数并校验当前用户的 RBAC 权限。
 *
 * <p><b>执行顺序：</b>{@code @Order(1)} 确保在事务切面之前执行，避免无权限方法开启事务。
 *
 * <p><b>空间 ID 提取策略：</b>
 *
 * <ol>
 *   <li>优先使用 {@link SpaceId} 注解标记的参数
 *   <li>若无注解，查找名为 {@code spaceId} 的 String 类型参数
 *   <li>若仍找不到，抛出 {@link NextwikiExceptionCode#PARAM_INVALID}
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class SpacePermissionAspect {

  /** 空间成员仓储（查询用户角色） */
  private final SpaceMemberRepository spaceMemberRepository;

  /**
   * 前置通知：校验空间权限。
   *
   * @param joinPoint 切点
   * @param spacePermission 空间权限注解
   */
  @Before("@annotation(spacePermission)")
  public void checkPermission(JoinPoint joinPoint, SpacePermission spacePermission) {
    // 获取当前用户 ID
    String userId = getCurrentUserId();
    if (userId == null || userId.isBlank()) {
      throw BusinessException.of(NextwikiExceptionCode.PERMISSION_DENIED)
          .data("reason", "未获取到用户身份");
    }

    // 提取空间 ID
    String spaceId = extractSpaceId(joinPoint);
    if (spaceId == null || spaceId.isBlank()) {
      throw BusinessException.of(NextwikiExceptionCode.PARAM_ERROR)
          .data("reason", "未找到空间 ID 参数");
    }

    // 查询用户在该空间的角色
    Optional<SpaceMemberDTO> memberOpt = spaceMemberRepository.findBySpaceIdAndUserId(spaceId, userId);

    // 公开空间允许读取（viewer 级别）
    if (memberOpt.isEmpty()) {
      if (spacePermission.level() == SpacePermission.Level.VIEWER) {
        // 检查是否为公开空间
        if (isPublicSpace(spaceId)) {
          return; // 公开空间允许匿名读取
        }
      }
      throw BusinessException.of(NextwikiExceptionCode.PERMISSION_DENIED)
          .data("spaceId", spaceId)
          .data("reason", "用户不是空间成员");
    }

    String userRole = memberOpt.get().getRole();

    // 校验角色是否满足权限级别要求
    if (!spacePermission.level().satisfiedBy(userRole)) {
      throw BusinessException.of(NextwikiExceptionCode.PERMISSION_DENIED)
          .data("spaceId", spaceId)
          .data("requiredLevel", spacePermission.level().getRoleName())
          .data("userRole", userRole);
    }

    log.debug("[SpacePermissionAspect] 权限校验通过: userId={}, spaceId={}, role={}, required={}",
        userId, spaceId, userRole, spacePermission.level().getRoleName());
  }

  /**
   * 从方法参数中提取空间 ID。
   *
   * @param joinPoint 切点
   * @return 空间 ID，未找到返回 null
   */
  private String extractSpaceId(JoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] args = joinPoint.getArgs();

    // 1. 优先查找 @SpaceId 注解标记的参数
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].isAnnotationPresent(SpaceId.class)) {
        return args[i] != null ? args[i].toString() : null;
      }
    }

    // 2. 查找名为 spaceId 的 String 类型参数
    for (int i = 0; i < parameters.length; i++) {
      if ("spaceId".equals(parameters[i].getName())
          && parameters[i].getType() == String.class
          && args[i] != null) {
        return (String) args[i];
      }
    }

    return null;
  }

  /**
   * 获取当前用户 ID。
   *
   * <p>优先从 RequestContext 获取，其次从 Spring Security Context 获取。
   *
   * @return 用户 ID，未获取到返回 null
   */
  private String getCurrentUserId() {
    // 从 RequestContext 获取（网关注入的用户 ID）
    String userId = RequestContext.getUserId();
    if (userId != null && !userId.isBlank()) {
      return userId;
    }
    // 从请求头获取
    return RequestContext.getExtraHeader(AuthHeaderConstants.X_USER_ID);
  }

  /**
   * 检查是否为公开空间。
   *
   * @param spaceId 空间 ID
   * @return 若空间可见性为 public 则返回 true
   */
  private boolean isPublicSpace(String spaceId) {
    // 简化实现：直接查询空间可见性
    // 实际项目中可注入 SpaceRepository
    return false; // 默认非公开，需扩展
  }
}
