# ydsz-system

> 系统基础服务（System Foundation）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9001**（按构建顺序 2/10） |
| **服务名** | `ydsz-system` |
| **构建顺序** | 2/10 |
| **数据库** | PostgreSQL（共享主库） |
| **依赖** | Nacos、PostgreSQL、Redis |

## 核心职责

本模块是 YDSZ 的**系统级基础服务**，承担**横切关注点**。

| 业务域 | 说明 |
|---|---|
| **系统配置** | 参数配置（`ydsz_config`），支持按 key 查询、按 group 批量查询、公开配置查询、Redis 缓存、缓存穿透防护、值类型校验 |
| **数据字典** | 字典类型（`ydsz_dict_type`）+ 字典项（`ydsz_dict_item`），支持树形字典、缓存、版本管理 |
| **应用注册** | OAuth2 应用注册（`ydsz_app_info`），支持 BCrypt 密钥校验（强度可配置） |
| **系统变量** | 业务级变量管理（`ydsz_variable`），支持 Redis 缓存、缓存穿透防护 |
| **实体版本** | 通用实体变更历史快照（`ydsz_entity_version`），写操作自动记录变更、支持查询审计与回滚 |
| **多租户** | 租户（`ydsz_tenant`）+ 套餐（`ydsz_tenant_plan`）+ 套餐菜单（`ydsz_tenant_plan_menu`），支持租户级菜单定制 |
| **全局搜索** | `GlobalSearchController` + `SearchDashboardController`，基于 common-search 聚合各模块 SearchProvider |

## DDD 分层结构

```
ydsz-system/
├── pom.xml
├── ydsz-system-api/                    # API 层：Feign Client + Fallback
│   └── src/main/java/com/njydsz/system/api/client/
│       ├── AppInfoClient.java          # 应用校验 Feign 客户端
│       ├── AppInfoClientFallback.java
│       ├── ConfigClient.java           # 配置查询 Feign 客户端（getConfig）
│       ├── ConfigClientFallback.java
│       ├── DictClient.java             # 字典查询 Feign 客户端（dict item / dict list）
│       └── DictClientFallback.java
├── ydsz-system-domain/                 # 领域层：Entity + DTO + VO
│   └── src/main/java/com/njydsz/system/domain/
│       ├── entity/                     # 实体（无 DO 后缀，符合 entity-naming 规范）
│       │   ├── AppInfo.java            # OAuth2 应用注册
│       │   ├── Config.java             # 系统参数
│       │   ├── DictItem.java           # 字典项
│       │   ├── DictType.java           # 字典类型
│       │   ├── EntityVersion.java      # 通用实体版本快照
│       │   ├── Variable.java           # 系统变量
│       │   ├── Tenant.java             # 租户
│       │   ├── TenantPlan.java         # 租户套餐
│       │   └── TenantPlanMenu.java     # 套餐菜单关联
│       ├── dto/                        # 创建/更新 DTO（含 JSR-303 校验）
│       └── vo/                         # 视图对象（不含敏感字段如 appSecret）
├── ydsz-system-infra/                  # 基础设施层：MyBatis Mapper + Repository
│   └── src/main/java/com/njydsz/system/infra/
│       ├── mapper/                     # AppInfo/Config/DictItem/DictType/EntityVersion/Tenant/TenantPlan/TenantPlanMenu/Variable Mapper
│       └── repository/                 # 8 个仓储（AppInfo/Config/Dict/EntityVersion/TenantPlanMenu/TenantPlan/Tenant/Variable）
├── ydsz-system-server/                 # 应用层：Service + Config + Health + Metrics
│   └── src/main/java/com/njydsz/system/server/
│       ├── config/                     # SystemProperties + SystemConfiguration + CacheConfig + InternalApiIpFilter
│       ├── service/                    # 接口 + impl（含缓存、事务、指标、版本快照）
│       │   ├── AppInfoService.java
│       │   ├── ConfigService.java
│       │   ├── DictService.java
│       │   ├── DictItemService.java
│       │   ├── EntityVersionService.java
│       │   ├── VariableService.java
│       │   └── impl/
│       ├── health/                     # SystemHealthIndicator（轻量探针）
│       └── metrics/                    # SystemMetrics（Micrometer，含 dict 专用指标）
└── ydsz-system-web/                    # Web 层：Controller + Bootstrap
    └── src/main/java/com/njydsz/system/web/
        ├── SystemApplication.java
        └── controller/                 # 14 个 Controller
            ├── AppInfoController.java       # /api/v1/app
            ├── ConfigController.java        # /api/v1/config
            ├── DictController.java          # /api/v1/dict/type
            ├── DictItemController.java      # /api/v1/dict/item
            ├── DictVersionController.java   # /api/v1/dict/version
            ├── VariableController.java      # /api/v1/variable
            ├── ConfigVersionController.java # /api/v1/config/version（回滚）
            ├── VariableVersionController.java # /api/v1/variable/version（回滚）
            ├── TenantController.java        # /api/v1/tenant
            ├── TenantPlanController.java    # /api/v1/tenant-plan
            ├── GlobalSearchController.java  # /api/v1/search 全局搜索聚合
            ├── SearchDashboardController.java # /api/v1/search/dashboard
            ├── AuditAdminController.java    # /api/v1/admin/audit
            └── InternalApiController.java   # /api/internal（POST + body，Feign 内部调用）
```

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/config` | 系统参数 CRUD + 按 key 查询 + 按 group 批量查询 + 公开配置查询 |
| `/api/v1/dict/type` | 字典类型 CRUD（支持搜索过滤） |
| `/api/v1/dict/item` | 字典项 CRUD + 按类型查询 + 树形查询 + 批量写入 |
| `/api/v1/dict/version` | 字典版本历史查询 |
| `/api/v1/config/version` | 配置版本历史 + 回滚 |
| `/api/v1/variable/version` | 变量版本历史 + 回滚 |
| `/api/v1/tenant` `/api/v1/tenant-plan` | 租户 / 套餐 / 套餐菜单管理 |
| `/api/v1/app` | 应用注册 CRUD（支持搜索过滤） |
| `/api/v1/variable` | 系统变量 CRUD + 按 key 查询值 |
| `/api/v1/search` | 全局搜索（聚合各模块 SearchProvider，基于 common-search） |
| `/api/v1/search/dashboard` | 搜索分析 / 质量看板 |
| `/api/v1/admin/audit` | 审计日志查询（AuditAdminController） |
| `POST /api/internal/config/get` | Feign 内部调用：按 key 查配置值（POST body 传输） |
| `POST /api/internal/dict/item` | Feign 内部调用：按类型+编码查字典项（POST body 传输） |
| `POST /api/internal/app/validate` | Feign 内部调用：校验应用密钥（POST body 传输，不暴露密钥） |

## 数据库表

| 表名 | 说明 |
|---|---|
| `ydsz_config` | 系统参数（key-value，支持租户维度） |
| `ydsz_dict_type` | 字典类型 |
| `ydsz_dict_item` | 字典项（支持树形 parent_id、扩展 ext_json） |
| `ydsz_entity_version` | 通用实体版本（配置/变量/字典等变更历史快照，含 tenant_id） |
| `ydsz_app_info` | 应用注册（OAuth2 client_id/client_secret） |
| `ydsz_variable` | 系统变量 |
| `ydsz_tenant` | 租户（多租户隔离的租户主表） |
| `ydsz_tenant_plan` | 租户套餐（套餐定义，含菜单配额/功能开关） |
| `ydsz_tenant_plan_menu` | 套餐菜单关联（套餐 → 菜单多对多） |

## 核心能力

### 配置缓存
- 按 `config_key` 缓存配置值到 Redis，TTL 可配置（默认 5 分钟）
- 缓存穿透防护：空值写入哨兵 `__NULL__`，短 TTL（1 分钟）
- 写操作（save/update/delete）自动清除缓存

### 字典缓存
- 按 `type_code:item_code` 缓存单个字典项，TTL 可配置（默认 10 分钟）
- 按 `type_code` 缓存字典项列表，TTL 可配置
- 缓存清除使用 SCAN 替代 KEYS，避免 Redis 阻塞
- 缓存穿透防护：空值写入哨兵 `__NULL__`
- 写操作自动清除对应类型的缓存 + 记录版本快照

### 系统变量缓存
- 按 `variable_key` 缓存变量值到 Redis，TTL 可配置
- 缓存穿透防护：空值写入哨兵 `__NULL__`
- 写操作自动清除缓存

### 应用密钥安全
- `appSecret` 使用 BCrypt 加密存储，强度可配置（`ydsz.system.app.bcrypt-strength`）
- `validateClient` 使用 `BCryptPasswordEncoder.matches()` 校验
- `AppInfoVO` 不包含 `appSecret` 字段，避免泄露
- 内部 API 使用 POST body 传输密钥，不暴露在 URL 中

### 实体版本管理（EntityVersion）
- 配置 / 变量 / 字典等实体的写操作（save/update/delete）自动记录版本快照到 `ydsz_entity_version`
- 版本记录包含实体类型、实体 ID、版本号、变更内容（diff）
- 支持版本历史查询与**回滚**（`/api/v1/config/version`、`/api/v1/variable/version` 等）

### 可观测性
- `SystemMetrics`：Micrometer 指标（前缀 `ydsz_system_`）
  - `config_read_total` / `config_read_duration_ms` — 配置读取次数/耗时
  - `config_cache_hit_total` / `config_cache_miss_total` — 配置缓存命中/未命中
  - `dict_query_total` / `dict_query_duration_ms` — 字典查询次数/耗时
  - `dict_cache_hit_total` / `dict_cache_miss_total` — 字典缓存命中/未命中（专用指标）
  - `app_validate_success_total` / `app_validate_failure_total` — 应用校验成功/失败
  - `variable_read_total` / `variable_cache_hit_total` / `variable_cache_miss_total`、`config_validation_warning_total`
- `SystemHealthIndicator`：Redis 连通性 + DataSource 可达性检查（轻量探针）
- `@Audit` 注解：所有写操作自动记录审计日志（`sys_audit_log`，common-audit）

### 分页搜索
- 所有分页接口支持搜索过滤（configGroup/configKey/status 等）
- Service `page()` 方法返回 `PageResponse<VO>`（继承自 `BaseResponse<T>`），自带 `getPageNum()`/`getPageSize()`/`getPages()` 便捷方法，Controller 无需重复 toVO 转换

## 配置

**bootstrap.yml**：

```yaml
spring:
  application:
    name: ydsz-system
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        file-extension: yaml
        shared-configs:
          - data-id: ydsz-common.yaml
            refresh: true
          - data-id: ydsz-system.yaml
            refresh: true
server:
  port: 9001
```

**可配置属性**（`ydsz.system.*`）：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `ydsz.system.health-enabled` | true | 是否启用健康检查 |
| `ydsz.system.config.enabled` | true | 是否启用配置服务 |
| `ydsz.system.config.cache-ttl-minutes` | 5 | 配置缓存 TTL（分钟） |
| `ydsz.system.dict.enabled` | true | 是否启用字典服务 |
| `ydsz.system.dict.cache-ttl-minutes` | 10 | 字典缓存 TTL（分钟） |
| `ydsz.system.variable.enabled` | true | 是否启用变量服务 |
| `ydsz.system.variable.cache-ttl-minutes` | 10 | 变量缓存 TTL（分钟） |
| `ydsz.system.app.bcrypt-strength` | 10 | BCrypt 加密强度（4-31） |

> 说明：内部 API 的 IP 白名单已由 `ydsz.system.internal-api-ip-whitelist` 迁移至 `ydsz.safe.ip-access`（common-safe 统一管控）。

## Feign 接口

被以下 Feign 客户端调用（位于 `ydsz-system-api`）：

- `ConfigClient` → 配置查询（`getConfig`）
- `DictClient` → 字典项 / 字典列表查询
- `AppInfoClient` → 应用密钥校验（`/api/v1/app/validate`）

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-system spring-boot:run
```

## 常见问题

### Q1：配置查询返回 null

检查 `ydsz_config` 表中是否存在对应的 `config_key` 且 `status = 'ENABLED'`、`deleted = 0`。
配置查询走 Redis 缓存，空值会缓存 1 分钟（防穿透），期间重复查询返回 null。

### Q2：应用密钥校验失败

1. 确认 `ydsz_app_info` 表中 `app_key` 存在且 `status = 'ENABLED'`
2. 确认 `app_secret` 字段存储的是 BCrypt 加密后的哈希值（非明文）
3. 创建应用时通过 API 传入明文密钥，Service 会自动 BCrypt 加密
4. BCrypt 强度可通过 `ydsz.system.app.bcrypt-strength` 配置

### Q3：字典项查询缓存不刷新

字典项写操作（save/update/delete）会自动清除对应 `type_code` 的缓存（使用 SCAN 命令）。
如果缓存未清除，检查 Redis 连接是否正常。

### Q4：内部 API 安全

内部 API（`/api/internal/**`）使用 POST + body 传输参数，appSecret 不暴露在 URL 中。
Gateway 应配置路由规则限制 `/api/internal/**` 仅允许内部服务调用。
