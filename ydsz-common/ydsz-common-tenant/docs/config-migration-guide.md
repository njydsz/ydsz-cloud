# 租户配置迁移指南

> **版本**：v1.0  
> **生效日期**：2026-08-15  
> **目标**：将租户配置从 `ydsz.jdbc.tenant-isolation.*` 迁移至 `ydsz.tenant.*`

---

## 1. 背景

自 v2.0.0 起，租户配置统一收口至 `ydsz.tenant.*` 前缀，
旧前缀 `ydsz.jdbc.tenant-isolation.*` 已标记为 @Deprecated，计划在 v3.0.0 移除。

---

## 2. 配置项映射

### 2.1 核心配置

| 旧配置（@Deprecated） | 新配置 | 说明 |
|----------------------|--------|------|
| `ydsz.jdbc.tenant-isolation.enabled` | `ydsz.tenant.enabled` | 是否启用多租户 |
| `ydsz.jdbc.tenant-isolation.mode` | `ydsz.tenant.mode` | 租户隔离模式 |
| `ydsz.jdbc.tenant-isolation.tenant-column` | `ydsz.tenant.tenant-column` | 租户列名 |
| `ydsz.jdbc.tenant-isolation.tenant-fields` | `ydsz.tenant.tenant-fields` | 租户字段列表 |
| `ydsz.jdbc.tenant-isolation.ignore-tables` | `ydsz.tenant.ignore-tables` | 忽略隔离的表 |
| `ydsz.jdbc.tenant-isolation.anon-urls` | `ydsz.tenant.anon-urls` | 匿名访问白名单 |

### 2.2 新增配置（仅新前缀支持）

| 配置项 | 说明 |
|--------|------|
| `ydsz.tenant.super-tenant-id` | 超级管理员租户 ID |
| `ydsz.tenant.system-tenant-id` | 系统租户 ID |
| `ydsz.tenant.default-claim` | 默认 JWT claim 名 |
| `ydsz.tenant.default-header` | 默认 HTTP header 名 |
| `ydsz.tenant.table-column-mapping` | per-table 列名覆盖 |
| `ydsz.tenant.tenant-sharing` | 跨租户数据共享 |
| `ydsz.tenant.datasource.mapping` | ISOLATE_DB 模式数据源映射 |
| `ydsz.tenant.sql-cache.enabled` | SQL 改写缓存开关 |

---

## 3. 迁移示例

### 3.1 旧配置

```yaml
ydsz:
  jdbc:
    tenant-isolation:
      enabled: true
      mode: SINGLE
      tenant-column: tenant_id
      ignore-tables: [sys_config, sys_dict]
      anon-urls: [/auth/login]
```

### 3.2 新配置

```yaml
ydsz:
  tenant:
    enabled: true
    mode: SINGLE
    tenant-column: tenant_id
    ignore-tables: [sys_config, sys_dict]
    anon-urls: [/auth/login]
```

---

## 4. MULTI 模式迁移示例

### 4.1 旧配置（仅支持 SINGLE）

```yaml
ydsz:
  jdbc:
    tenant-isolation:
      enabled: true
      mode: SINGLE
      tenant-fields:
        - column: tenant_id
          source: TENANT
```

### 4.2 新配置（支持 MULTI）

```yaml
ydsz:
  tenant:
    enabled: true
    mode: MULTI
    tenant-fields:
      - column: tenant_id
        claim: tenantId
        header: X-Tenant-Id
      - column: company_id
        claim: companyId
        header: X-Company-Ids
      - column: dept_id
        claim: deptId
        header: X-Dept-Ids
        multi-value: true
```

---

## 5. 兼容方案

在 v2.x 版本中，旧配置前缀仍然有效（向后兼容），但会输出弃用警告：

```
[TenantConfig] 检测到已弃用的配置前缀 'ydsz.jdbc.tenant-isolation.*'，
请迁移至 'ydsz.tenant.*'，旧前缀将在 v3.0.0 移除。
```

---

## 6. 迁移检查清单

- [ ] 将所有 `ydsz.jdbc.tenant-isolation.*` 配置迁移至 `ydsz.tenant.*`
- [ ] 验证 SINGLE 模式下租户隔离功能正常
- [ ] 如需要 MULTI 模式，参考多字段租户最佳实践文档
- [ ] 运行集成测试验证 SQL 改写正确
- [ ] 确认异步任务租户传播正常
- [ ] 确认 Feign 跨服务调用租户传播正常

---

## 7. 常见问题

### 7.1 迁移后租户隔离不生效

**原因**：配置前缀错误或未重启服务。

**排查**：
1. 确认配置前缀为 `ydsz.tenant.*`
2. 确认 `enabled: true`
3. 重启服务使配置生效

### 7.2 MULTI 模式下 SQL 条件缺失

**原因**：表缺少多字段租户所需的列。

**排查**：
1. 确认表中有对应的列（如 `company_id`、`dept_id`）
2. 确认已有数据已补充租户字段值

### 7.3 配置项不识别

**原因**：使用了旧版配置项名称。

**排查**：
1. 参考配置项映射表
2. 注意新版的 `claim`/`header` 替代旧版的 `source`

---

## 8. 参考

- [TenantProperties 源码](../src/main/java/com/njydsz/common/tenant/config/TenantProperties.java)
- [多字段租户最佳实践](./multi-tenant-best-practices.md)
- [云顶编码规范](../../docs/云顶编码规范.md)
