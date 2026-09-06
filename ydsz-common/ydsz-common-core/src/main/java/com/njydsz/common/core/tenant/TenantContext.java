package com.njydsz.common.core.tenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户上下文值对象（不可变）。
 *
 * <p>携带当前请求的完整租户字段信息，贯穿整个调用链。
 *
 * <p><b>迁移说明：</b>P2-3 从 {@code com.njydsz.common.tenant.TenantContext} 下沉至 common-core，
 * 以打破 common-cache ↔ common-tenant 的循环依赖。原有包路径保留废弃转发声明，现有 import 仍可用。
 *
 * <p>字段完全动态，由配置的 {@code tenant-fields} 决定哪些字段存在。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * Map<String, Object> fields = new HashMap<>(16);
 * fields.put("tenantId", "tenant_001");
 * TenantContext ctx = TenantContext.of(fields);
 *
 * TenantContext ctx = TenantContext.builder("tenant_001")
 *     .superAdmin(false)
 *     .field("companyId", "comp_001")
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TenantContext {

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

  /** 保护构造器，允许 common-tenant 中的子类在 P2-3 过渡期继承转发 */
  protected TenantContext(
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

  public static TenantContext skip() {
    Map<String, Object> fields = new HashMap<>(2);
    fields.put("skipIsolation", true);
    fields.put("tenantId", "__skip__");
    return new TenantContext("__skip__", List.of(), List.of(), true, false, false, fields);
  }

  public static Builder builder(String tenantId) {
    return new Builder(tenantId);
  }

  public static TenantContext system(String tenantId) {
    return new TenantContext(tenantId, List.of(), List.of(), true, false, true,
        Map.of("tenantId", tenantId));
  }

  public String getTenantId() {
    return tenantId;
  }

  public List<String> getDeptIds() {
    return deptIds;
  }

  public List<String> getCompanyIds() {
    return companyIds;
  }

  public boolean isSkipIsolation() {
    return skipIsolation;
  }

  public boolean isSuperAdmin() {
    return superAdmin;
  }

  public boolean isSystemTenant() {
    return systemTenant;
  }

  public Map<String, Object> getRawFields() {
    return rawFields;
  }

  public Map<String, Object> getFields() {
    return rawFields;
  }

  public boolean isEmpty() {
    return tenantId == null && rawFields.isEmpty();
  }

  public Object getFieldValue(String key) {
    if (key == null) {
      return null;
    }
    return rawFields.get(key);
  }

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

  public boolean hasSharing() {
    return rawFields.containsKey("sharedTenantIds");
  }

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

  public boolean isSchemaMode() {
    return rawFields.containsKey("schema");
  }

  public String getSchema() {
    Object value = rawFields.get("schema");
    return value != null ? value.toString() : null;
  }

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
   * @author ydsz-team
   * @since 26.09.01
   */
  public static final class Builder {
    private final String tenantId;
    private final Map<String, Object> fields = new HashMap<>(16);

    Builder(String tenantId) {
      this.tenantId = tenantId;
    }

    public Builder superAdmin(boolean isSuperAdmin) {
      fields.put("superAdmin", isSuperAdmin);
      return this;
    }

    public Builder schema(String schema) {
      fields.put("schema", schema);
      return this;
    }

    public Builder sharedTenantIds(List<String> sharedTenantIds) {
      fields.put("sharedTenantIds", sharedTenantIds);
      return this;
    }

    public Builder field(String key, String value) {
      fields.put(key, value);
      return this;
    }

    public Builder fieldValues(String key, List<String> values) {
      fields.put(key, values);
      return this;
    }

    public TenantContext build() {
      if (tenantId != null) {
        fields.put("tenantId", tenantId);
      }
      return TenantContext.of(Map.copyOf(fields));
    }
  }
}
