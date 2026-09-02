package com.njydsz.userinfo.domain.scim;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.common.json.annotation.JsonProperty;

/**
 * SCIM 2.0 PATCH 操作请求体。
 *
 * <p>遵循 RFC 7644 Section 3.5.2（PATCH），支持对资源的部分更新。
 *
 * <p><b>操作类型：</b>
 *
 * <ul>
 *   <li>{@code add} — 添加属性值（不存在则创建，存在则替换）</li>
 *   <li>{@code remove} — 移除属性值（指定 path 时移除对应属性，否则报错）</li>
 *   <li>{@code replace} — 替换属性值（不存在则报错）</li>
 * </ul>
 *
 * <p><b>示例：</b>
 *
 * <pre>
 * {
 *   "schemas": ["urn:ietf:params:scim:schemas:core:2.0:PatchOp"],
 *   "Operations": [
 *     {"op": "replace", "path": "displayName", "value": "张三"},
 *     {"op": "replace", "path": "emails[value eq \"old@test.com\"].value", "value": "new@test.com"},
 *     {"op": "add", "path": "phoneNumbers", "value": "1*********0"},
 *     {"op": "remove", "path": "phoneNumbers[value eq \"1*********0\"]"}
 *   ]
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimPatchOp {

  /** PATCH 操作 Schema 标识。 */
  private static final List<String> PATCH_OP_SCHEMA =
      List.of("urn:ietf:params:scim:schemas:core:2.0:PatchOp");

  /** Schema 标识（固定值）。 */
  @JsonProperty("schemas")
  @Builder.Default
  private List<String> schemas = PATCH_OP_SCHEMA;

  /** PATCH 操作列表（按顺序执行）。 */
  @JsonProperty("Operations")
  private List<ScimPatchOperation> operations;

  /**
   * 单个 PATCH 操作。
   *
   * <p>表示对资源的一个原子修改操作。
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ScimPatchOperation {

    /**
     * 操作类型。
     *
     * <p>可选值：add、remove、replace（大小写不敏感）。
     */
    @JsonProperty("op")
    private String op;

    /**
     * 目标属性路径（可选）。
     *
     * <p>支持 SCIM 标准路径语法，如 {@code displayName}、{@code emails}、
     * {@code emails[value eq "xxx"].value}。为 null 时表示操作整个资源。
     */
    @JsonProperty("path")
    private String path;

    /**
     * 操作值（可选）。
     *
     * <p>用于 add/replace 操作。当 op 为 remove 时，value 应为 null。
     */
    @JsonProperty("value")
    private Object value;
  }
}
