package com.njydsz.common.core.context;

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
 * <p>三个安全标记字段（skipIsolation、superAdmin、systemTenant）使用三值语义：
 * <ul>
 *   <li>{@code TRUE} — 字段在来源数据中显式为 true</li>
 *   <li>{@code FALSE} — 字段在来源数据中显式为 false 或未传入（安全降级默认）</li>
 *   <li>{@code UNKNOWN} — 仅内部使用，表示"来源未传入该字段"，对外等价于 {@code FALSE}</li>
 * </ul>
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

  /**
   * 三值枚举：明确区分"显式 TRUE"、"显式 FALSE"、"未传入（UNKNOWN）"。
   *
   * <p>当来源 Map 中未包含对应键时，字段为 {@link #UNKNOWN}； 安全相关的 getter（如 {@link TenantContext#isSkipIsolation()}）将 UNKNOWN 降级为 {@code false}，
   * 遵循"安全默认"原则（未明确授权视为未授权）。
   *
   * @since 26.09.01
   */
  enum TriState {
    /** 字段在来源数据中显式为 true。 */
    TRUE,
    /** 字段在来源数据中显式为 false。 */
    FALSE,
    /** 来源数据未传入该字段（内部状态，对外等价于 {@link #FALSE}）。 */
    UNKNOWN;

    /**
     * 将 Map 中的原始值解析为 TriState。
     *
     * @param raw 原始对象值（可为 null、Boolean、String 等）
     * @return 解析后的 TriState；未知值返回 {@link #UNKNOWN}
     */
    static TriState from(Object raw) {
      if (raw instanceof Boolean b) {
        return b ? TRUE : FALSE;
      }
      if (raw instanceof String s) {
        if ("true".equalsIgnoreCase(s)) {
          return TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
          return FALSE;
        }
      }
      if (raw == null) {
        return UNKNOWN;
      }
      // 兼容数字 1/0、以及其他真值语义
      if (raw instanceof Number n && n.intValue() != 0) {
        return TRUE;
      }
      return UNKNOWN;
    }

    /**
     * 安全降级为 boolean：{@link #UNKNOWN} 视为 {@code false}。
     *
     * @return TRUE → true；FALSE / UNKNOWN → false
     */
    boolean toSafeBoolean() {
      return this == TRUE;
    }
  }

  /** 主租户 ID */
  private final String tenantId;

  /** 部门 ID 列表（多值字段） */
  private final List<String> deptIds;

  /** 公司 ID 列表（多值字段） */
  private final List<String> companyIds;

  /** 是否跳过隔离（UNKNOWN 降级为 false = 不跳过（安全默认）） */
  private final TriState skipIsolation;

  /** 是否为超级管理员（UNKNOWN 降级为 false = 非超管（最小权限原则）） */
  private final TriState superAdmin;

  /** 是否为系统租户（UNKNOWN 降级为 false = 非系统租户） */
  private final TriState systemTenant;

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
    this.skipIsolation = skipIsolation ? TriState.TRUE : TriState.FALSE;
    this.superAdmin = superAdmin ? TriState.TRUE : TriState.FALSE;
    this.systemTenant = systemTenant ? TriState.TRUE : TriState.FALSE;
    this.rawFields = rawFields != null ? Map.copyOf(rawFields) : Map.of();
  }

  /**
   * 内部全参数构造器（支持 TriState 三值传递）。
   *
   * @param tenantId 租户 ID
   * @param deptIds 部门 ID 列表
   * @param companyIds 公司 ID 列表
   * @param skipIsolation 是否跳过隔离（三值）
   * @param superAdmin 是否超级管理员（三值）
   * @param systemTenant 是否系统租户（三值）
   * @param rawFields 原始字段映射
   */
  private TenantContext(
      String tenantId,
      List<String> deptIds,
      List<String> companyIds,
      TriState skipIsolation,
      TriState superAdmin,
      TriState systemTenant,
      Map<String, Object> rawFields) {
    this.tenantId = tenantId;
    this.deptIds = deptIds != null ? List.copyOf(deptIds) : List.of();
    this.companyIds = companyIds != null ? List.copyOf(companyIds) : List.of();
    this.skipIsolation = skipIsolation != null ? skipIsolation : TriState.UNKNOWN;
    this.superAdmin = superAdmin != null ? superAdmin : TriState.UNKNOWN;
    this.systemTenant = systemTenant != null ? systemTenant : TriState.UNKNOWN;
    this.rawFields = rawFields != null ? Map.copyOf(rawFields) : Map.of();
  }

  public static TenantContext of(Map<String, Object> fields) {
    if (fields == null) {
      fields = Map.of();
    }
    String tenantId = getStringField(fields, "tenantId");
    List<String> deptIds = getListField(fields, "deptIds");
    List<String> companyIds = getListField(fields, "companyIds");
    TriState skipIsolation = TriState.from(fields.get("skipIsolation"));
    TriState superAdmin = TriState.from(fields.get("superAdmin"));
    TriState systemTenant = TriState.from(fields.get("systemTenant"));
    return new TenantContext(tenantId, deptIds, companyIds, skipIsolation, superAdmin, systemTenant, fields);
  }

  public static TenantContext skip() {
    Map<String, Object> fields = new HashMap<>(2);
    fields.put("skipIsolation", true);
    fields.put("tenantId", "__skip__");
    return new TenantContext("__skip__", List.of(), List.of(), TriState.TRUE, TriState.FALSE, TriState.FALSE, fields);
  }

  public static Builder builder(String tenantId) {
    return new Builder(tenantId);
  }

  public static TenantContext system(String tenantId) {
    return new TenantContext(tenantId, List.of(), List.of(), TriState.TRUE, TriState.FALSE, TriState.TRUE,
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
    return skipIsolation.toSafeBoolean();
  }

  public boolean isSuperAdmin() {
    return superAdmin.toSafeBoolean();
  }

  public boolean isSystemTenant() {
    return systemTenant.toSafeBoolean();
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
