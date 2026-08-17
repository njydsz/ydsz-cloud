package com.njydsz.common.core.model;

import java.util.Set;

import org.springframework.lang.Nullable;

/**
 * 当前用户极简身份契约
 *
 * <p>定义数据层（L4）、横切服务层（L5）读取当前请求用户身份所需的最小数据集。 仅包含基础标识信息，不涉及列级权限、令牌等高级认证特性。
 *
 * <p>本接口定义于 core 模块（L2 基础设施层），解耦上层对 common-auth 的直接依赖。 common-auth 模块的 {@code
 * com.njydsz.common.auth.model.YdszAuthInfo} 实现本接口。
 *
 * <h3>使用场景</h3>
 *
 * <ul>
 *   <li>数据审计字段填充：created_by / updated_by
 *   <li>多租户隔离：tenant_id 过滤
 *   <li>行级数据权限：根据 dataScope + 维度 ID 集合过滤
 * </ul>
 *
 * <p>获取方式：通过 {@link com.njydsz.common.core.context.RequestContext#get(String)} 以 key {@link
 * com.njydsz.common.core.context.BizContextKeys#KEY_AUTH_INFO} 读取， 返回对象可安全转换为本接口类型。
 *
 * @author ydsz-team
 * @since 2.0.0
 * @see com.njydsz.common.auth.model.AuthInfo
 * @see com.njydsz.common.auth.model.YdszAuthInfo
 */
public interface CurrentUser {

  /**
   * 获取当前登录用户唯一标识
   *
   * @return 用户ID；无上下文返回 null
   */
  @Nullable
  String getUniqueId();

  /**
   * 获取身份类型编码
   *
   * @return 身份类型编码（如 "company"、"visitor"）
   */
  @Nullable
  String getIdentityType();

  /**
   * 获取数据权限范围类型编码
   *
   * @return 数据范围类型（tenant/group/company/dept/user/project/region/custom）
   */
  @Nullable
  String getDataScope();

  /**
   * 获取租户唯一标识
   *
   * <p>用于多租户场景下的数据隔离。
   *
   * @return 租户ID；无上下文返回 null
   */
  @Nullable
  String getTenantId();

  /**
   * 获取数据权限维度 ID 集合（类型安全扩展点，替代反射调用）。
   *
   * <p>默认返回 null（表示当前实现不提供该维度）。 上层实现（如 auth 模块的 {@code AuthInfo}）按需覆盖：
   *
   * <ul>
   *   <li>{@code companyIds} → 有权限的公司 ID 集合
   *   <li>{@code deptIds} → 有权限的部门 ID 集合
   * </ul>
   *
   * @param claim 维度标识（companyIds / deptIds / projectIds / regionIds）
   * @return 权限 ID 集合；不支持或不存在返回 null
   * @since 2.1.0
   */
  @Nullable
  default Set<String> getPermissionIds(String claim) {
    return null;
  }
}
