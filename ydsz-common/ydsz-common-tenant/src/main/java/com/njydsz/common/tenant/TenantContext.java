package com.njydsz.common.tenant;
import java.util.List;
import java.util.Map;

/**
 * 租户上下文值对象（不可变）。
 *
 * <p>携带当前请求的完整租户字段信息，贯穿整个调用链。 字段完全动态，由配置的 {@code tenant-fields} 决定哪些字段存在。
 *
 * <p><b>字段值类型：</b>
 *
 * <ul>
 *   <li>单值字段 → String
 *   <li>多值字段 → List&lt;String&gt;
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 普通用户请求（单字段）
 * Map<String, Object> fields = Map.of("tenantId", "tenant_001");
 * TenantContext ctx = TenantContext.of(fields);
 *
 * // 多字段组合
 * Map<String, Object> fields = new HashMap<>(16);
 * fields.put("tenantId", "tenant_001");
 * fields.put("deptIds", List.of("dept_1", "dept_2"));
 * TenantContext ctx = TenantContext.of(fields);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class TenantContext {

  /** 主租户 ID */
  private final String tenantId;

  /** 部门 ID 列表（多值字段） */
  private final List<String> deptIds;

  /** 公司 ID 列表（多值字段） */
  private final List<String> companyIds;

  /** 是否跳过隔离 */
  private final boolean skipIsolation;

  /** 是否为超级管理员 */
  private final boolean superAdmin;

  /** 是否为系统租户 */
  private final boolean systemTenant;

  /** 原始字段映射（不可变） */
  private final Map<String, Object> rawFields;

  private TenantContext(
      String tenantId,
      List<String> deptIds,
      List<String> companyIds,
      boolean skipIsolation,
      boolean superAdmin,
      boolean systemTenant,
      Map<String, Object> rawFields) {
    this.tenantId = tenantId;
    this.deptIds = deptIds != null ? List.copyOf(deptIds) : List.of();
    this.companyIds = companyIds != null ? List.copyOf(companyIds) : List.of();
    this.skipIsolation = skipIsolation;
    this.superAdmin = superAdmin;
    this.systemTenant = systemTenant;
    this.rawFields = rawFields != null ? Map.copyOf(rawFields) : Map.of();
  }

  /**
   * 从字段映射构造租户上下文。
   *
   * @param fields 字段映射
   * @return 租户上下文实例
   */
  public static TenantContext of(Map<String, Object> fields) {
    if (fields == null) {
      fields = Map.of();
    }
    String tenantId = getStringField(fields, "tenantId");
    List<String> deptIds = getListField(fields, "deptIds");
    List<String> companyIds = getListField(fields, "companyIds");
    boolean skipIsolation = Boolean.TRUE.equals(fields.get("skipIsolation"));
    boolean superAdmin = Boolean.TRUE.equals(fields.get("superAdmin"));
    boolean systemTenant = Boolean.TRUE.equals(fields.get("systemTenant"));
    return new TenantContext(tenantId, deptIds, companyIds, skipIsolation, superAdmin, systemTenant, fields);
  }

  /**
   * 创建系统租户上下文。
   *
   * @param tenantId 系统租户 ID
   * @return 系统租户上下文
   */
  public static TenantContext system(String tenantId) {
    return new TenantContext(tenantId, List.of(), List.of(), true, false, true, Map.of("tenantId", tenantId));
  }

  /**
   * 返回主租户 ID。
   *
   * @return 主租户 ID
   */
  public String getTenantId() {
    return tenantId;
  }

  /**
   * 返回部门 ID 列表（不可变）。
   *
   * @return 部门 ID 列表（不可变）
   */
  public List<String> getDeptIds() {
    return deptIds;
  }

  /**
   * 返回公司 ID 列表（不可变）。
   *
   * @return 公司 ID 列表（不可变）
   */
  public List<String> getCompanyIds() {
    return companyIds;
  }

  /**
   * 判断是否跳过隔离。
   *
   * @return true=跳过隔离
   */
  public boolean isSkipIsolation() {
    return skipIsolation;
  }

  /**
   * 判断是否为超级管理员。
   *
   * @return true=超级管理员
   */
  public boolean isSuperAdmin() {
    return superAdmin;
  }

  /**
   * 判断是否为系统租户。
   *
   * @return true=系统租户
   */
  public boolean isSystemTenant() {
    return systemTenant;
  }

  /**
   * 返回原始字段映射（不可变）。
   *
   * @return 原始字段映射（不可变）
   */
  public Map<String, Object> getRawFields() {
    return rawFields;
  }

  /**
   * 创建当前上下文的快照（用于异步传播）。
   *
   * @return 当前实例（不可变对象直接返回自身）
   */
  public TenantContext snapshot() {
    return this;
  }

  private static String getStringField(Map<String, Object> fields, String key) {
    Object value = fields.get(key);
    return value != null ? value.toString() : null;
  }

  @SuppressWarnings("unchecked")
  private static List<String> getListField(Map<String, Object> fields, String key) {
    Object value = fields.get(key);
    if (value instanceof List) {
      return (List<String>) value;
    }
    if (value != null) {
      return List.of(value.toString());
    }
    return List.of();
  }
}
