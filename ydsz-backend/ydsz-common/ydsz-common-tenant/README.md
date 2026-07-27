# ydsz-common-tenant

多租户隔离公共模块（可选引入，数据库无关）。

## 核心能力

- **统一租户上下文** — `TenantContextHolder`（TTL ThreadLocal），全链路传播
- **SQL 隔离拦截器** — JSqlParser SQL 改写，自动注入 `WHERE tenant_id = ?`
- **多级租户支持** — SINGLE / MULTI / ISOLATE_DB 三种模式，仅改配置不改代码
- **全链路传播** — Web Filter / Feign / @Async / 线程池 / 定时任务
- **Redis Key 隔离** — `{tenantId}:` 前缀
- **per-table 列名覆盖** — `@TenantColumn` 注解 + 配置映射
- **Fail-Closed** — 无法确定租户时拒绝执行 SQL

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-tenant</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  tenant:
    enabled: true
    mode: SINGLE
```

### 3. DO 继承 MpBaseEntity

`tenantId` 字段已在 `MpBaseEntity` 基类中统一声明，业务 DO 无需再单独声明。

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.tenant.enabled` | false | 启用多租户 |
| `ydsz.tenant.mode` | SINGLE | SINGLE / MULTI / ISOLATE_DB |
| `ydsz.tenant.tenant-column` | tenant_id | 默认租户列名 |
| `ydsz.tenant.super-tenant-id` | 0 | 超级管理员租户 ID |
| `ydsz.tenant.system-tenant-id` | 0 | 系统租户 ID（定时任务/异步） |
| `ydsz.tenant.ignore-tables` | - | 全局忽略表 |
| `ydsz.tenant.anon-urls` | - | 匿名 URL |
| `ydsz.tenant.table-column-mapping` | - | per-table 列名覆盖 |
