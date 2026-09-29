package com.njydsz.common.domain.permission;

import java.util.Set;

import com.njydsz.common.domain.holder.DataScopeContextHolder;

/**
 * 数据权限上下文轻量工具 — 供业务层（不依赖 jdbc 模块）读取当前请求的数据权限信息。
 *
 * <p>{@link DataPermissionContext} 封装了完整的行级/列级权限信息（userId、companyIds、deptIds、spaceIds 等），
 * 但其解析逻辑封装在 {@code ydsz-common-jdbc} 的 {@code DataPermissionContextResolver} 中。
 * 当业务模块（如 nextwiki 空间过滤、literule 规则可见性判断）需要在不引入 jdbc 依赖的情况下读取权限信息时，
 * 可使用本工具类。
 *
 * <p><b>典型使用场景：</b>
 *
 * <ul>
 *   <li>Space 空间隔离 — nextwiki 判断当前用户是否有指定空间的访问权限
 *   <li>规则可见性 — literule 判断用户所在公司是否有规则管理权限
 *   <li>跨模块数据过滤 — Tenant/Space 多维度复合过滤逻辑
 * </ul>
 *
 * <p><b>生命周期：</b>请求级别。数据由 HTTP 拦截器写入 {@link DataScopeContextHolder}，
 * 在 {@code try-finally} 块中自动清理。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // nextwiki：判断当前用户是否有 space 访问权限
 * if (!DataPermissionUtils.canAccessSpace(targetSpaceId)) {
 *     throw BusinessException.of("nextwiki.space.access.denied");
 * }
 *
 * // literule：获取当前用户可见的公司列表
 * Set<String> companies = DataPermissionUtils.currentCompanyIds();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.30
 * @see DataPermissionContext
 * @see DataScopeContextHolder
 */
public final class DataPermissionUtils {

  private DataPermissionUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 获取当前线程的数据权限上下文。
   *
   * <p>当上下文为空时返回 {@link DataPermissionContext#EMPTY}（不可变空实例），
   * 避免调用方处理 null 的复杂性。
   *
   * @return 永不为 {@code null} 的数据权限上下文
   * @since 26.09.30
   */
  public static DataPermissionContext currentContext() {
    DataPermissionContext ctx = DataScopeContextHolder.get();
    return ctx != null ? ctx : DataPermissionContext.EMPTY;
  }

  /**
   * 获取当前用户可见的空间 ID 集合。
   *
   * <p>适用于 nextwiki 空间隔离、workflow 工作流空间过滤等场景。
   * 返回的集合来自 {@link DataPermissionContext#getSpaceIds()}，永不为 {@code null}。
   *
   * @return 可见空间 ID 集合（无权限时为空集合，非 {@code null}）
   * @since 26.09.30
   */
  public static Set<String> currentSpaceIds() {
    return currentContext().getSpaceIds();
  }

  /**
   * 获取当前用户可见的公司 ID 集合。
   *
   * @return 可见公司 ID 集合（无权限时为空集合，非 {@code null}）
   * @since 26.09.30
   */
  public static Set<String> currentCompanyIds() {
    return currentContext().getCompanyIds();
  }

  /**
   * 获取当前用户可见的部门 ID 集合。
   *
   * @return 可见部门 ID 集合（无权限时为空集合，非 {@code null}）
   * @since 26.09.30
   */
  public static Set<String> currentDeptIds() {
    return currentContext().getDeptIds();
  }

  /**
   * 判断当前用户是否有指定空间的访问权限。
   *
   * <p>当 spaceId 为 {@code null} 或空白时返回 {@code false}（安全兜底）。
   *
   * @param spaceId 目标空间 ID
   * @return 有权限返回 {@code false}，空间 ID 无效时也返回 {@code false}
   * @since 26.09.30
   */
  public static boolean canAccessSpace(String spaceId) {
    if (spaceId == null || spaceId.isBlank()) {
      return false;
    }
    return currentSpaceIds().contains(spaceId);
  }

  /**
   * 判断当前用户是否有任一指定空间的访问权限。
   *
   * <p>当 spaceIds 为空时返回 {@code false}。适用于「用户可访问空间列表与目标空间列表有交集」的判定场景。
   *
   * @param spaceIds 目标空间 ID 集合（可为 {@code null}）
   * @return 有任一交集返回 {@code true}
   * @since 26.09.30
   */
  public static boolean canAccessAnySpace(Set<String> spaceIds) {
    if (spaceIds == null || spaceIds.isEmpty()) {
      return false;
    }
    Set<String> visible = currentSpaceIds();
    for (String sid : spaceIds) {
      if (sid != null && visible.contains(sid)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 判断当前用户是否有行级权限范围数据。
   *
   * <p>当无任何行级约束（无 userId、无公司/部门/项目/区域/空间 ID）时返回 {@code true}，
   * 表示当前用户可访问全部数据（需结合租户隔离）。
   *
   * @return 无行级约束时返回 {@code true}
   * @since 26.09.30
   */
  public static boolean isEmptyRowScope() {
    return currentContext().isEmptyRowScope();
  }

  /**
   * 获取当前登录用户 ID。
   *
   * @return 用户 ID，未登录时返回 {@code null}
   * @since 26.09.30
   */
  public static String currentUserId() {
    return currentContext().getUserId();
  }
}
