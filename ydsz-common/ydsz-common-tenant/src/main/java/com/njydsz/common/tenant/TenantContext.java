package com.njydsz.common.tenant;
import java.util.ArrayList;
import java.util.HashMap;
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
 *
 * // Builder 模式（WebFilter / 内部使用）
 * TenantContext ctx = TenantContext.builder("tenant_001")
 *     .superAdmin(false)
 *     .schema("tenant_001")
 *     .sharedTenantIds(List.of("shared_1"))
 *     .field("companyId", "comp_001")
 *     .fieldValues("deptIds", List.of("dept_1", "dept_2"))
 *     .build();
 *
 * // 跳过隔离
 * TenantContext skip = TenantContext.skip();
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
   * 创建跳过隔离的租户上下文（用于匿名 URL）。
   *
   * @return 跳过隔离的上下文实例
   */
  public static TenantContext skip() {
    Map<String, Object> fields = new HashMap<>(2);
    fields.put("skipIsolation", true);
    fields.put("tenantId", "__skip__");
    return new TenantContext("__skip__", List.of(), List.of(), true, false, false, fields);
  }

  /**
   * 创建 Builder 实例（以 tenantId 为初始值）。
   *
   * @param tenantId 租户 ID
   * @return Builder 实例
   */
  public static Builder builder(String tenantId) {
    return new Builder(tenantId);
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
   * 返回全部动态字段映射（等价于 {@link #getRawFields()}）。
   *
   * <p>为保持与消费代码中 {@code context.getFields()} 调用一致提供的别名。
   *
   * @return 全部动态字段（不可变）
   */
  public Map<String, Object> getFields() {
    return rawFields;
  }

  /**
   * 判断上下文是否为空（无租户 ID 且无字段）。
   *
   * @return true=空上下文
   */
  public boolean isEmpty() {
    return tenantId == null && rawFields.isEmpty();
  }

  /**
   * 获取指定 key 的单值字段值。
   *
   * @param key 字段 key（通常为 claim 名或列名）
   * @return 字段值，不存在返回 null
   */
  public Object getFieldValue(String key) {
    if (key == null) {
      return null;
    }
    return rawFields.get(key);
  }

  /**
   * 获取指定 key 的多值字段。
   *
   * @param key 字段 key（通常为 claim 名或列名）
   * @return 字段值列表（不可变），不存在返回空列表
   */
  public List<String> getFieldValues(String key) {
    if (key == null) {
      return List.of();
    }
    Object value = rawFields.get(key);
    if (value instanceof List<?> list) {
      List<String> result = new ArrayList<>(list.size());
      for (Object item : list) {
        if (item != null) {
          result.add(item.toString());
        }
      }
      return result;
    }
    if (value != null) {
      return List.of(value.toString());
    }
    return List.of();
  }

  /**
   * 判断是否配置了跨租户共享。
   *
   * @return true=已配置共享租户 ID
   */
  public boolean hasSharing() {
    return rawFields.containsKey("sharedTenantIds");
  }

  /**
   * 获取共享租户 ID 列表。
   *
   * @return 共享租户 ID 列表（不可变），未配置返回空列表
   */
  public List<String> getSharedTenantIds() {
    Object value = rawFields.get("sharedTenantIds");
    if (value instanceof List<?> list) {
      List<String> result = new ArrayList<>(list.size());
      for (Object item : list) {
        if (item != null) {
          result.add(item.toString());
        }
      }
      return result;
    }
    return List.of();
  }

  /**
   * 判断是否为 Schema 隔离模式（上下文包含 schema 字段）。
   *
   * @return true=已设置 schema
   */
  public boolean isSchemaMode() {
    return rawFields.containsKey("schema");
  }

  /**
   * 获取 Schema 隔离模式下的 schema 名。
   *
   * @return schema 名，未设置返回 null
   */
  public String getSchema() {
    Object value = rawFields.get("schema");
    return value != null ? value.toString() : null;
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

  private static List<String> getListField(Map<String, Object> fields, String key) {
    Object value = fields.get(key);
    if (value instanceof List<?> list) {
      List<String> result = new ArrayList<>(list.size());
      for (Object item : list) {
        result.add(item != null ? item.toString() : null);
      }
      return result;
    }
    if (value != null) {
      return List.of(value.toString());
    }
    return List.of();
  }

  /**
   * 租户上下文构建器。
   *
   * <p>收集所有动态字段后通过 {@link #build()} 构造 {@link TenantContext}。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  public static final class Builder {
    private final String tenantId;
    private final Map<String, Object> fields = new HashMap<>(16);

    Builder(String tenantId) {
      this.tenantId = tenantId;
    }

    /**
     * 设置是否为超级管理员。
     *
     * @param isSuperAdmin true=超级管理员
     * @return this
     */
    public Builder superAdmin(boolean isSuperAdmin) {
      fields.put("superAdmin", isSuperAdmin);
      return this;
    }

    /**
     * 设置 Schema 隔离模式下的 schema 名。
     *
     * @param schema schema 名（如 {@code tenant_xxx}）
     * @return this
     */
    public Builder schema(String schema) {
      fields.put("schema", schema);
      return this;
    }

    /**
     * 设置跨租户共享的租户 ID 列表。
     *
     * @param sharedTenantIds 可访问的源租户 ID 列表
     * @return this
     */
    public Builder sharedTenantIds(List<String> sharedTenantIds) {
      fields.put("sharedTenantIds", sharedTenantIds);
      return this;
    }

    /**
     * 添加单值字段。
     *
     * @param key 字段 key（通常为 claim 名或列名）
     * @param value 字段值
     * @return this
     */
    public Builder field(String key, String value) {
      fields.put(key, value);
      return this;
    }

    /**
     * 添加多值字段。
     *
     * @param key 字段 key（通常为 claim 名或列名）
     * @param values 字段值列表
     * @return this
     */
    public Builder fieldValues(String key, List<String> values) {
      fields.put(key, values);
      return this;
    }

    /**
     * 构建 {@link TenantContext} 实例。
     *
     * @return 不可变租户上下文
     */
    public TenantContext build() {
      if (tenantId != null) {
        fields.put("tenantId", tenantId);
      }
      return TenantContext.of(Map.copyOf(fields));
    }
  }
}
