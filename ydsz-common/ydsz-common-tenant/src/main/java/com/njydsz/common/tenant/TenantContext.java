package com.njydsz.common.tenant;.tenant
import java.util.ArrayList;
import java.util.Collections;
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