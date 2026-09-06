package com.njydsz.common.domain.permission;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.Data;

/**
 * 数据权限上下文。
 *
 * <p>封装当前请求的数据权限信息，包括用户ID、 公司ID列表、部门ID列表、项目ID列表、区域ID列表等，
 * 用于 SQL 拦截器自动拼接数据权限过滤条件。
 *
 * <p><b>注意：</b>租户隔离（TENANT 维度）已由独立的租户上下文
 * 通过 {@code TenantContextHolder} 处理，本上下文不再包含租户相关字段。
 *
 * <p><b>层级说明：</b>下沉至 domain 层，auth/jdbc/server 模块均可直接引用，
 * 避免 auth 反向依赖 jdbc 模块。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class DataPermissionContext {
  /** 行级权限维度编码（从请求头或 RequestContext 解析）。 */
  private String dataScope;

  /** 用户ID */
  private String userId;

  /** 公司ID集合 */
  private Set<String> companyIds = new HashSet<>(16);

  /** 部门ID集合 */
  private Set<String> deptIds = new HashSet<>(16);

  /** 项目ID集合 */
  private Set<String> projectIds = new HashSet<>(16);

  /** 区域ID集合 */
  private Set<String> regionIds = new HashSet<>(16);

  /** 空间ID集合（P1-3：空间维度隔离，用于 NextWiki 文件空间 / 工作流空间等场景） */
  private Set<String> spaceIds = new HashSet<>(4);

  /** 列可见规则（key=表名，value=允许查询的列名集合），用于 SELECT 列过滤 */
  private Map<String, Set<String>> visibleColumnsByTable = new HashMap<>(16);

  /** 列可编辑规则（key=表名，value=允许编辑的列名集合），用于 INSERT/UPDATE 列过滤 */
  private Map<String, Set<String>> editableColumnsByTable = new HashMap<>(16);

  /**
   * 创建空的上下文实例（所有字段使用默认值）。
   *
   * <p>当权限上下文为 null 时，使用此方法作为安全降级，不拦截 SQL。
   *
   * @return 空上下文实例
   */
  public static DataPermissionContext empty() {
    return new DataPermissionContext();
  }

  /**
   * 判断是否存在行级权限范围数据。
   *
   * <p>当用户ID、公司ID列表、部门ID列表、项目ID列表、区域ID列表全部为空时，
   * 认为没有行级权限上下文，返回 true。
   *
   * @return 无行级范围数据返回 true，否则返回 false
   */
  public boolean isEmptyRowScope() {
    boolean noUser = userId == null || userId.isBlank();
    return noUser
        && (companyIds == null || companyIds.isEmpty())
        && (deptIds == null || deptIds.isEmpty())
        && (projectIds == null || projectIds.isEmpty())
        && (regionIds == null || regionIds.isEmpty())
        && (spaceIds == null || spaceIds.isEmpty());
  }

  /**
   * 创建仅包含空间ID的数据权限上下文（P1-3：供 NextWiki / 工作流等模块便捷构建空间隔离上下文）。
   *
   * @param spaceId 当前空间ID
   * @return 仅包含 spaceId 的上下文
   */
  public static DataPermissionContext ofSpaceId(String spaceId) {
    DataPermissionContext context = new DataPermissionContext();
    if (spaceId != null && !spaceId.isBlank()) {
      context.getSpaceIds().add(spaceId);
    }
    return context;
  }

  /**
   * 创建包含多个空间ID的数据权限上下文。
   *
   * @param spaceIds 当前用户可访问的空间ID集合
   * @return 包含 spaceIds 的上下文
   */
  public static DataPermissionContext ofSpaceIds(Set<String> spaceIds) {
    DataPermissionContext context = new DataPermissionContext();
    if (spaceIds != null) {
      context.setSpaceIds(new HashSet<>(spaceIds));
    }
    return context;
  }
}
