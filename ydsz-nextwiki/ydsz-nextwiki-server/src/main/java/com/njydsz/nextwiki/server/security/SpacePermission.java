package com.njydsz.nextwiki.server.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 知识库空间权限校验注解（S4-P3-03）。
 *
 * <p>用于声明式校验用户对指定空间的 RBAC 权限，通过 {@link SpacePermissionAspect} AOP 切面自动拦截并校验。
 *
 * <p><b>权限层级（由高到低）：</b>
 *
 * <pre>
 *   owner  — 全部权限（管理成员、编辑设置、删除空间）
 *   admin  — 管理成员、编辑内容（不可删除空间）
 *   editor — 编辑内容（创建/修改/删除节点）
 *   viewer — 只读
 * </pre>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 *   @SpacePermission(level = SpacePermission.Level.ADMIN)
 *   public void addMember(@SpaceId String spaceId, ...) { ... }
 *
 *   @SpacePermission(level = SpacePermission.Level.EDITOR)
 *   public void createNode(@SpaceId String spaceId, ...) { ... }
 * </pre>
 *
 * <p><b>空间 ID 参数：</b>使用 {@link SpaceId} 标注的参数将被自动提取并查询空间权限。若方法参数名就是 {@code spaceId}，可省略 {@link SpaceId} 注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpacePermission {

  /**
   * 要求的最低权限级别。
   *
   * <p>用户的实际角色层级必须 ≥ 此级别才算通过校验。
   *
   * @return 最低权限级别
   */
  Level level();

  /**
   * 权限级别枚举（数字越大权限越高）。
   *
   * <p>用于比较用户角色是否满足最低权限要求。
   */
  enum Level {
    /** 只读 */
    VIEWER(1, "viewer"),
    /** 编辑内容 */
    EDITOR(2, "editor"),
    /** 管理成员 */
    ADMIN(3, "admin"),
    /** 全部权限（所有者） */
    OWNER(4, "owner");

    /** 层级数值（越大权限越高） */
    private final int priority;

    /** 角色名称 */
    private final String roleName;

    Level(int priority, String roleName) {
      this.priority = priority;
      this.roleName = roleName;
    }

    public int getPriority() {
      return priority;
    }

    public String getRoleName() {
      return roleName;
    }

    /**
     * 判断给定角色是否满足此级别要求。
     *
     * @param userRole 用户实际角色
     * @return 若用户角色层级 ≥ 此级别则返回 true
     */
    public boolean satisfiedBy(String userRole) {
      if (userRole == null) {
        return false;
      }
      for (Level level : values()) {
        if (level.roleName.equals(userRole)) {
          return level.priority >= this.priority;
        }
      }
      return false;
    }
  }
}
