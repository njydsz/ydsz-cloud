new HashSet<>(16)jydsz.common.jdbc.permission;

/**
 * 数据权限上下文。
 *
 * <p>封装当前请求的数据权限信息，包括用户ID、 公司ID列表、部门ID列表、项目ID列表、区域ID列表等， 用于 SQL 拦截器自动拼接数据权限过滤条件。
 *
 * <p><b>注意：</b>租户隔离（TENANT 维度）已由独立的 {@code common-tenant} 模块 通过 {@code TenantIsolationInterceptor}
 * 处理，本上下文不再包含租户相关字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.Data;

/**
 * DataPermissionContext 类。
 *
 * <p>所属包：{@code com.njydsz.common.jdbc.permission}
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
  private Set<String> companyIds = new HashSet<>();

  /** 部门ID集合 */
  private Set<String> deptIds = new HashSet<>();

  /** 项目ID集合 */
  private Set<String> projectIds = new HashSet<>();

  /** 区域ID集合 */
  private Set<String> regionIds = new HashSet<>(16);

  /** 列级权限规则：table -> allowed columns（小写化后比对）。 */
  private Map<String, Set<String>> visibleColumnsByTable = Collections.emptyMap();

  /** 列级权限规则：table -> editable columns（小写化后比对）。 */
  private Map<String, Set<String>> editableColumnsByTable = Collections.emptyMap();

  /**
   * 返回一个空的 DataPermissionContext 实例。
   *
   * @return 所有字段为默认空值的上下文
   */
  public static DataPermissionContext empty() {
    return new DataPermissionContext();
  }

  /**
   * 是否缺少全部行级约束信息。
   *
   * @return {@code true} 表示数据范围、用户、部门、项目等约束均未设置
   */
  public boolean isEmptyRowScope() {
    return dataScope == null
        && userId == null
        && companyIds.isEmpty()
        && deptIds.isEmpty()
        && projectIds.isEmpty()
        && regionIds.isEmpty();
  }
}
