package com.njydsz.common.domain.permission;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Collections;
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
 * <p><b>反序列化安全：</b>通过 {@link #readObject(ObjectInputStream)} 确保集合字段在反序列化后
 * 不会为 {@code null}，防止下游 {@link #isEmptyRowScope()} 调用出现 NPE。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class DataPermissionContext {

  /** 确保反序列化后集合字段非空（JVM 反序列化不会执行字段初始化器）。 */
  @Serial
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    if (companyIds == null) {
      companyIds = new HashSet<>(16);
    }
    if (deptIds == null) {
      deptIds = new HashSet<>(16);
    }
    if (projectIds == null) {
      projectIds = new HashSet<>(16);
    }
    if (regionIds == null) {
      regionIds = new HashSet<>(16);
    }
    if (spaceIds == null) {
      spaceIds = new HashSet<>(4);
    }
    if (visibleColumnsByTable == null) {
      visibleColumnsByTable = new HashMap<>(16);
    }
    if (editableColumnsByTable == null) {
      editableColumnsByTable = new HashMap<>(16);
    }
  }

  /**
   * 不可变的空上下文常量（所有字段为空集合，不可添加元素）。
   *
   * <p>适用于：
   *
   * <ul>
   *   <li>无数据权限要求时的默认返回值
   *   <li>降级场景（无登录用户、无权限配置）
   *   <li>只读判断场景（{@link #isEmptyRowScope()} 必然返回 {@code true}）
   * </ul>
   *
   * <p><b>注意：</b>EMPTY 是共享常量，禁止通过 setter 修改其字段。
   * 如需可变空上下文（后续会添加公司/部门 ID），使用 {@link #emptyMutable()}。
   *
   * @since 26.09.19
   */
  // YDIZ-WARN-001 允许保留：集合已通过 Collections.unmodifiableXxx 包装，不可外部修改
  @SuppressWarnings("squid:S2386")
  public static final DataPermissionContext EMPTY;

  static {
    DataPermissionContext ctx = new DataPermissionContext();
    // 类级别初始化器已赋空集合，此处包装为不可变视图
    ctx.companyIds = Collections.unmodifiableSet(ctx.companyIds);
    ctx.deptIds = Collections.unmodifiableSet(ctx.deptIds);
    ctx.projectIds = Collections.unmodifiableSet(ctx.projectIds);
    ctx.regionIds = Collections.unmodifiableSet(ctx.regionIds);
    ctx.spaceIds = Collections.unmodifiableSet(ctx.spaceIds);
    EMPTY = ctx;
  }

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
   * 获取不可变的空上下文常量（所有字段为空集合）。
   *
   * <p>当权限上下文需要安全降级时使用此常量，避免重复创建空对象。
   *
   * <p><b>注意：</b>返回的 {@link #EMPTY} 是不可变常量，禁止修改。 如需可变空上下文，使用 {@link #emptyMutable()}。
   *
   * @return 不可变的空上下文常量 {@link #EMPTY}
   * @since 26.09.19 优化：从每次创建新实例改为返回共享常量
   */
  public static DataPermissionContext empty() {
    return EMPTY;
  }

  /**
   * 创建可变空上下文实例（所有字段使用默认空集合）。
   *
   * <p>适用于需要在返回后向上下文填充数据的场景（如通过拦截器逐步构建权限条件）。
   *
   * @return 可变空上下文实例
   * @since 26.09.19
   */
  public static DataPermissionContext emptyMutable() {
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
