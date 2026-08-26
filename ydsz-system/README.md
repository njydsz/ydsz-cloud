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
| **依赖** | Nacos、PostgreSQL、Redis、MinIO |

## 数据库初始化

> 项目规范**禁止**使用 Flyway / Liquibase 等 schema-migration 框架。数据库 DDL 统一以 SQL 脚本形式管理。

```bash
psql -U postgres -d ydsz_cloud -f ydsz-system/deploy/sql/init.sql
```

脚本包含 9 张表（`ydsz_sys_config` / `ydsz_sys_dict_type` / `ydsz_sys_dict_item` / `ydsz_sys_entity_version` / `ydsz_sys_app_info` / `ydsz_sys_variable` / `ydsz_sys_tenant` / `ydsz_sys_tenant_plan` / `ydsz_sys_tenant_plan_menu`）的建表 DDL、索引与初始数据（内置租户、默认套餐、示例字典与配置）。

## 核心职责

本模块是 YDSZ 的**系统级基础服务**，承担**横切关注点**。

| 业务域 | 说明 |
|---|---|
| **系统配置** | 参数配置（`ydsz_sys_config`），支持按 key 查询、按 group 批量查询、公开配置查询、缓存、缓存穿透防护、值类型校验 |
| **数据字典** | 字典类型（`ydsz_sys_dict_type`）+ 字典项（`ydsz_sys_dict_item`），支持树形字典、缓存、版本管理、批量写入 |
| **应用注册** | OAuth2 应用注册（`ydsz_sys_app_info`），支持 BCrypt 密钥校验（强度可配置） |
| **系统变量** | 业务级变量管理（`ydsz_sys_variable`），支持缓存、缓存穿透防护 |
| **实体版本** | 通用实体变更历史快照（`ydsz_sys_entity_version`），写操作自动记录变更、支持查询审计与回滚 |
| **多租户** | 租户（`ydsz_sys_tenant`）+ 套餐（`ydsz_sys_tenant_plan`）+ 套餐菜单（`ydsz_sys_tenant_plan_menu`），支持租户级菜单定制与配额管理 |
| **前端初始化** | 聚合公开配置 + 字典数据 + 系统版本号，减少前端启动请求次数 |

## DDD 分层结构

```
ydsz-system/
├── pom.xml
├── ydsz-system-api/                    # API 层：Feign Client + Fallback + DTO
│   └── src/main/java/com/njydsz/system/api/
│       ├── client/
│       │   ├── AppInfoClient.java          # 应用校验 Feign 客户端
│       │   ├── ConfigClient.java           # 配置查询 Feign 客户端（getConfig）
│       │   └── DictClient.java             # 字典查询 Feign 客户端（dict item / dict list）
│       ├── dto/                            # Feign 请求 DTO
│       └── fallback/                       # Feign 降级实现
├── ydsz-system-domain/                 # 领域层：DTO + VO + Query + Enum + Repository 接口 + Event
│   └── src/main/java/com/njydsz/system/domain/
│       ├── dto/                        # 创建/更新 DTO（含 JSR-303 校验）
│       ├── vo/                         # 视图对象（不含敏感字段如 appSecret）
│       ├── enums/                      # 领域枚举（ConfigValueType / SystemExceptionCode / QuotaType）
│       ├── query/                      # 分页查询条件对象（8 个）
│       ├── repository/                 # 仓储接口（8 个，实现在 infra/repository，依赖方向 domain ← infra）
│       └── event/                      # 领域事件（VersionSnapshotEvent）
├── ydsz-system-infra/                  # 基础设施层：MyBatis Mapper + Entity + Repository 实现 + Converter
│   └── src/main/java/com/njydsz/system/infra/
│       ├── entity/                     # 持久化实体（9 个，继承 MpBaseEntity）
│       │   ├── Config.java            # 系统配置（充血模型：validate / getTypedValue / validateValueFormat 等领域方法）
│       │   ├── DictItem.java          # 字典项
│       │   ├── DictType.java          # 字典类型
│       │   ├── EntityVersion.java     # 通用实体版本快照
│       │   ├── AppInfo.java           # OAuth2 应用注册
│       │   ├── Variable.java          # 系统变量
│       │   ├── Tenant.java            # 租户
│       │   ├── TenantPlan.java        # 租户套餐
│       │   └── TenantPlanMenu.java    # 套餐菜单关联
│       ├── mapper/                     # 9 个 MyBatis Mapper（含自定义 XML）
│       ├── repository/                 # 8 个仓储实现
│       └── converter/                  # SystemConverter（MapStruct）
├── ydsz-system-server/                 # 应用层：Service + Config + Health + Metrics + Cache + Listener + Schedule
│   └── src/main/java/com/njydsz/system/server/
│       ├── config/                     # SystemProperties + SystemConfiguration + CacheConfig + InternalApiIpFilter
│       ├── service/                    # 15 个 Service 接口 + impl
│       │   ├── AppInfoService.java
│       │   ├── ConfigService.java
│       │   ├── ConfigBatchService.java     # 配置批量操作
│       │   ├── ConfigExcelService.java     # 配置 Excel 导入导出
│       │   ├── DictService.java
│       │   ├── DictItemService.java
│       │   ├── DictItemBatchService.java   # 字典项批量写入
│       │   ├── EntityVersionService.java
│       │   ├── FrontendInitService.java    # 前端初始化聚合服务
│       │   ├── TenantService.java
│       │   ├── TenantPlanService.java
│       │   ├── TenantPlanMenuService.java
│       │   ├── TenantQuotaService.java     # 租户配额管理
│       │   ├── VariableService.java
│       │   └── impl/                       # 15 个实现类
│       │       └── rollback/               # 回滚策略（4 个）
│       │           ├── RollbackStrategy.java
│       │           ├── ConfigRollbackStrategy.java
│       │           ├── VariableRollbackStrategy.java
│       │           └── DictItemRollbackStrategy.java
│       ├── cache/                     # 缓存组件
│       │   ├── CacheKeyBuilder.java    # 租户感知缓存键构造器
│       │   └── CacheWarmer.java        # 缓存预热
│       ├── listener/                  # 事件监听器
│       │   └── VersionSnapshotListener.java    # 版本快照监听（事务提交后异步创建）
│       ├── schedule/                  # 定时任务
│       │   └── TenantExpireScheduler.java      # 租户到期检查
│       ├── health/                    # SystemHealthIndicator（Redis + DataSource 探针）
│       ├── metrics/                   # SystemMetrics（Micrometer，前缀 ydsz_system_）
│       └── vo/                        # 服务端 VO（ConfigExcelVO / DictItemExcelVO / VariableExcelVO）
├── ydsz-system-app/                    # App 端模块：移动端自动配置 + OpenAPI
│   └── src/main/java/com/njydsz/system/app/
│       ├── config/                     # SystemAppAutoConfiguration（条件装配：平台=APP）
│       ├── health/                     # SystemAppHealthIndicator
│       └── openapi/                    # SystemAppOpenApiConfiguration
└── ydsz-system-web/                    # Web 层：Controller + Bootstrap
    └── src/main/java/com/njydsz/system/web/
        ├── SystemApplication.java      # 启动类（@EnableYdszAuth / @EnableYdszAudit / @EnableYdszSafe / @EnableYdszFeign）
        └── controller/                 # 13 个 Controller
            ├── AppInfoController.java         # /api/v1/app
            ├── AuditAdminController.java      # /api/v1/admin/audit
            ├── ConfigController.java          # /api/v1/config
            ├── ConfigVersionController.java   # /api/v1/config/version（回滚）
            ├── DictController.java            # /api/v1/dict/type
            ├── DictItemController.java        # /api/v1/dict/item
            ├── DictVersionController.java     # /api/v1/dict/version
            ├── FrontendInitController.java    # /api/v1/system/init（前端初始化聚合）
            ├── InternalApiController.java     # /api/internal（POST + body，Feign 内部调用）
            ├── TenantController.java          # /api/v1/tenant
            ├── TenantPlanController.java      # /api/v1/tenant-plan
            ├── VariableController.java        # /api/v1/variable
            └── VariableVersionController.java # /api/v1/variable/version（回滚）
```

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/config` | 系统参数 CRUD + 按 key 查询 + 按 group 批量查询 + 公开配置查询 + 游标分页 + 导入导出 |
| `/api/v1/config/version` | 配置版本历史查询 + 回滚 |
| `/api/v1/dict/type` | 字典类型 CRUD + 全量列表 |
| `/api/v1/dict/item` | 字典项 CRUD + 按 (typeCode, itemCode) 精确查询 + 按类型查询启用列表 + 树形查询 + 批量写入 |
| `/api/v1/dict/version` | 字典版本历史查询（支持分页） + 回滚 |
| `/api/v1/variable` | 系统变量 CRUD + 按 key 查询值 |
| `/api/v1/variable/version` | 变量版本历史 + 回滚 |
| `/api/v1/tenant` | 租户 CRUD |
| `/api/v1/tenant-plan` | 套餐 CRUD + 菜单配置 |
| `/api/v1/app` | 应用注册 CRUD（支持搜索过滤） |
| `/api/v1/admin/audit` | 审计日志查询（按时间/操作人/行为/追踪ID） |
| `/api/v1/system/init` | 前端初始化聚合接口（公开配置 + 默认字典 + 系统版本号） |
| `/api/internal/config/get` | Feign 内部调用：按 key 查配置值（POST body 传输） |
| `/api/internal/dict/item` | Feign 内部调用：按类型+编码查字典项展示值（POST body 传输） |
| `/api/internal/dict/list` | Feign 内部调用：按类型查全部启用字典项展示值列表（POST body 传输） |
| `/api/internal/app/validate` | Feign 内部调用：校验应用密钥（POST body 传输，不暴露密钥） |

### 安全特性

所有写接口统一启用：

- `@AuthApiPermission` — 接口级权限码校验
- `@Idempotent` — 幂等保护（Redis SET NX EX，5-30 秒）
- `@RateLimit` — 接口级限流（写操作 50 QPS，回滚 10 QPS，批量 10 QPS，前端初始化 100 QPS）
- `@Audit` — 审计日志（异步持久化到 `sys_audit_log`）

内部 API（`/api/internal/**`）额外启用：
- `InternalApiIpFilter` — IP 白名单校验（委托 `ydsz-common-safe` IpAccessService，支持 CIDR 网段）
- 敏感参数通过 POST body 传输，不暴露在 URL 中

## 数据库表

| 表名 | 说明 |
|---|---|
| `ydsz_sys_config` | 系统参数（key-value，含 `tenant_id` 列 + 唯一索引 `uk_config_group_key(tenant_id, config_group, config_key)`，多租户隔离在 schema 层保证） |
| `ydsz_sys_dict_type` | 字典类型 |
| `ydsz_sys_dict_item` | 字典项（支持树形 parent_id、扩展 ext_json） |
| `ydsz_sys_entity_version` | 通用实体版本（配置/变量/字典等变更历史快照，含 tenant_id） |
| `ydsz_sys_app_info` | 应用注册（OAuth2 client_id/client_secret） |
| `ydsz_sys_variable` | 系统变量 |
| `ydsz_sys_tenant` | 租户（多租户隔离的租户主表） |
| `ydsz_sys_tenant_plan` | 租户套餐（套餐定义，含菜单配额/功能开关） |
| `ydsz_sys_tenant_plan_menu` | 套餐菜单关联（套餐 → 菜单多对多） |

## 核心能力

### 配置缓存
- 基于 ydsz-common-cache（本地缓存 + Redis 二级缓存），缓存键由 `CacheKeyBuilder` 生成：
  - 单值：`value:{tenantId}:{configKey}`
  - 组批量：`group:{tenantId}:{configGroup}`
  - 公开配置：`public:{tenantId}`
- TTL 通过 `ydsz.system.config.cache-ttl-minutes` 配置（默认 **15 分钟**）
- 缓存穿透防护：空值缓存（ydsz-common-cache 内置防穿透能力）
- 写操作（save/update/delete）自动清除缓存（`@CacheEvict`）+ 发布 `VersionSnapshotEvent`
- 缓存失效：由 Service 层 `@CacheEvict` 单点执行（规范 35.4.1），单实例部署下无需跨实例同步
- 缓存预热：`CacheWarmer` 在 `ApplicationReadyEvent` 后异步加载热点数据

### 字典缓存
- 基于 ydsz-common-cache，缓存键由 `CacheKeyBuilder` 生成：
  - 单条：`item:{tenantId}:{typeCode}:{itemCode}`
  - 列表：`list:{tenantId}:{typeCode}`
- TTL 通过 `ydsz.system.dict.cache-ttl-minutes` 配置（默认 **30 分钟**）
- 缓存穿透防护：空值写入哨兵
- 写操作自动失效对应类型缓存 + 记录版本快照（`VersionSnapshotEvent`）

### 系统变量缓存
- 基于 ydsz-common-cache，缓存键由 `CacheKeyBuilder` 生成：
  - `{tenantId}:{variableKey}`
- TTL 通过 `ydsz.system.variable.cache-ttl-minutes` 配置（默认 **15 分钟**）
- 缓存穿透防护：空值写入哨兵
- 写操作自动清除缓存 + 发布 `VersionSnapshotEvent`

### 应用密钥安全
- `appSecret` 使用 BCrypt 加密存储，强度可配置（`ydsz.system.app.bcrypt-strength`，默认 10，合法范围 4-31）
- `validateClient` 使用 `BCryptPasswordEncoder.matches()` 校验
- `AppInfoVO` 不包含 `appSecret` 字段，避免泄露
- 内部 API 使用 POST body 传输密钥，不暴露在 URL 中

### 实体版本管理（EntityVersion）
- 配置 / 变量 / 字典等实体的写操作（save/update/delete）自动发布 `VersionSnapshotEvent`
- `VersionSnapshotListener` 在事务提交后（`AFTER_COMMIT`）异步创建版本快照到 `ydsz_sys_entity_version`
- 版本记录包含实体类型、实体 ID、版本号、变更内容（diff）
- 支持版本历史查询与**回滚**（`/api/v1/config/version`、`/api/v1/variable/version`、`/api/v1/dict/version`）
- 回滚策略：`RollbackStrategy` 接口 + 具体实现（ConfigRollbackStrategy / VariableRollbackStrategy / DictItemRollbackStrategy）

### 前端初始化
- `FrontendInitController`（`/api/v1/system/init`）聚合返回公开配置 + 默认字典 + 系统版本号
- 支持按需指定字典类型（`/api/v1/system/init/dicts?dictTypes=user_status,gender`）
- 限流保护（100 QPS），防止恶意刷接口

### 多租户配额管理
- `TenantQuotaService` 提供租户套餐配额校验能力
- 支持配额类型（`QuotaType` 枚举）、配额上限查询、当前使用量查询
- 配额不足时抛出业务异常

### 租户到期管理
- `TenantExpireScheduler`：定时检查租户到期状态，自动处理过期租户
- 配置项：`ydsz.system.tenant.expire-check-interval-ms`（默认 30 分钟）、`ydsz.system.tenant.expire-check-initial-delay-ms`（默认 60 秒）
- 使用单条原子 UPDATE 批量停用到期租户，无 N+1 问题

### 可观测性
- `SystemMetrics`：Micrometer 指标（前缀 `ydsz_system_`）
  - `ydsz_system_config_validation_warning_total` — 配置值格式校验告警
  - `ydsz_system_app_validate_success_total` / `ydsz_system_app_validate_fail_total` — 应用密钥校验成功/失败
- `SystemHealthIndicator`：Redis 连通性 + DataSource 可达性检查（轻量探针）
  - 启用条件：`ydsz.system.health-enabled=true`（默认开启）
  - 访问端点：`GET /actuator/health/system`
- `@Audit` 注解：所有写操作自动记录审计日志（`sys_audit_log`，common-audit）

### 分页搜索
- 所有分页接口支持搜索过滤（configGroup/configKey/status 等）
- Service `page()` 方法返回 `PageResponse<VO>`（继承自 `YdszResponse<T>`），自带 `getPageNum()`/`getPageSize()`/`getPages()` 便捷方法
- 游标分页：`ConfigController` 支持 `/api/v1/config/cursor` 端点，基于 ID 的 seek method 分页
- 分页安全上限：所有分页接口硬上限 500 条（`MAX_PAGE_SIZE`）

### 批量操作
- `ConfigBatchService`：配置批量创建（单次最多 500 条，幂等 30 秒）
- `DictItemBatchService`：字典项批量写入（单次最多 500 条，幂等 30 秒）
- `ConfigExcelService`：配置 Excel 导入导出（含 ConfigExcelVO / DictItemExcelVO / VariableExcelVO）

### 安全加固
- SQL 防火墙（`ydsz.jdbc.sql-firewall`）：拦截 DDL、无 WHERE 写操作、多语句注入
- 慢 SQL 检测（`ydsz.jdbc.slow-sql`）：阈值 500ms，告警阈值 2000ms
- SQL 审计（`ydsz.sql-audit`）：写操作落盘备查
- JDBC 安全加固（`ydsz.jdbc.*`）
- 统一 IP 访问控制（`ydsz.safe.ip-access.*`，支持 CIDR 网段）

## 配置

**bootstrap.yml**：

```yaml
server:
  port: 9001
  max-http-request-header-size: 16KB
  servlet:
    context-path: /

spring:
  application:
    name: ydsz-system
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  main:
    web-application-type: servlet
    allow-bean-definition-overriding: true
  cloud:
    nacos:
      discovery:
        enabled: true
        register-enabled: true
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:ydsz}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        metadata:
          version: 1.0.0
          port: 9001
        ip-type: IPv4
      config:
        enabled: true
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:ydsz}
        group: ${NACOS_GROUP:${spring.profiles.active}}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        file-extension: yaml
        refresh-enabled: true
        shared-configs:
          - data-id: ydsz-common.yaml
            group: ${spring.profiles.active}
            refresh: true
          - data-id: ydsz-system.yml
            group: ${spring.profiles.active}
            refresh: true
```

**可配置属性**（`ydsz.system.*`）：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `ydsz.system.health-enabled` | true | 是否启用健康检查 |
| `ydsz.system.config.enabled` | true | 是否启用配置缓存 |
| `ydsz.system.config.cache-ttl-minutes` | 15 | 配置缓存 TTL（分钟） |
| `ydsz.system.config.strict-validation` | false | 配置值格式严格校验：true 时格式非法阻止保存，false 仅告警放行（生产建议开启） |
| `ydsz.system.dict.enabled` | true | 是否启用字典缓存 |
| `ydsz.system.dict.cache-ttl-minutes` | 30 | 字典缓存 TTL（分钟） |
| `ydsz.system.variable.enabled` | true | 是否启用变量缓存 |
| `ydsz.system.variable.cache-ttl-minutes` | 15 | 变量缓存 TTL（分钟） |
| `ydsz.system.app.bcrypt-strength` | 10 | BCrypt 加密强度（4-31） |
| `ydsz.system.cache.cross-instance-enabled` | false | 是否启用跨实例缓存失效（Redis Pub/Sub），多实例部署且需实时一致性时开启 |
| `ydsz.system.version` | 1.0.0 | 系统版本号（前端展示） |
| `ydsz.system.tenant.expire-check-interval-ms` | 1800000 | 租户到期检查间隔（毫秒，默认 30 分钟） |
| `ydsz.system.tenant.expire-check-initial-delay-ms` | 60000 | 租户到期检查初始延迟（毫秒，默认 60 秒） |

> 说明：内部 API 的 IP 白名单已由 `ydsz.system.internal-api-ip-whitelist` 迁移至 `ydsz.safe.ip-access`（common-safe 统一管控，支持 CIDR 网段）。

## 主要依赖

| 依赖 | 说明 |
|---|---|
| `ydsz-common-jdbc` | JDBC 安全加固、分页、SQL 防火墙 |
| `ydsz-common-cache` | 本地缓存 + Redis 二级缓存 |
| `ydsz-common-audit` | 审计日志 |
| `ydsz-common-auth` | 接口权限校验 |
| `ydsz-common-safe` | IP 访问控制、限流 |
| `ydsz-common-lock` | 幂等保护 |
| `ydsz-common-excel` | Excel 导入导出 |
| `ydsz-common-event` | 事务性 Outbox 事件 |
| `ydsz-common-sentry` | 统一监控告警（Sentry SPI） |
| `ydsz-common-tenant` | 多租户隔离 |
| `ydsz-common-feign` | Feign 客户端 |
| `ydsz-common-redis` | Redis 操作 |
| `ydsz-common-config` | 配置中心 |
| `mybatis-plus-spring-boot4-starter` | ORM 框架 |
| `dynamic-datasource-spring-boot3-starter` | 动态数据源 |
| `micrometer-registry-prometheus` | Prometheus 指标 |
| `spring-boot-starter-actuator` | 健康检查端点 |
| `spring-cloud-starter-alibaba-nacos-discovery` | Nacos 服务注册 |
| `spring-cloud-starter-alibaba-nacos-config` | Nacos 配置中心 |
| `springdoc-openapi-starter-webmvc-ui` | OpenAPI 文档 |
| `spring-security-crypto` | BCrypt 加密 |
| `spring-data-redis` | Redis 数据访问 |

## Feign 接口

被以下 Feign 客户端调用（位于 `ydsz-system-api`）：

- `ConfigClient` → 配置查询（`getConfig`）
- `DictClient` → 字典项 / 字典列表查询
- `AppInfoClient` → 应用密钥校验（`/api/internal/app/validate`）

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-system/spring-boot:run
```

或启动 `ydsz-system-web` 模块：

```bash
cd ydsz-cloud/ydsz-system/ydsz-system-web
mvn spring-boot:run
```

## 常见问题

### Q1：配置查询返回 null

检查 `ydsz_sys_config` 表中是否存在对应的 `config_key` 且 `status = 'ENABLED'`、`deleted = 0`。
配置查询走缓存，空值会缓存（TTL 默认 15 分钟），期间重复查询返回 null。

### Q2：应用密钥校验失败

1. 确认 `ydsz_sys_app_info` 表中 `app_key` 存在且 `status = 'ENABLED'`
2. 确认 `app_secret` 字段存储的是 BCrypt 加密后的哈希值（非明文）
3. 创建应用时通过 API 传入明文密钥，Service 会自动 BCrypt 加密
4. BCrypt 强度可通过 `ydsz.system.app.bcrypt-strength` 配置

### Q3：字典项查询缓存不刷新

字典项写操作（save/update/delete）会自动失效对应 `type_code` 的缓存。
如果缓存未清除，检查 Redis 连接是否正常。多实例部署时可开启 `ydsz.system.cache.cross-instance-enabled` 实现跨实例缓存失效。

### Q4：内部 API 安全

内部 API（`/api/internal/**`）使用 POST + body 传输参数，appSecret 不暴露在 URL 中。
Gateway 应配置路由规则限制 `/api/internal/**` 仅允许内部服务调用。
所有内部 API 启用限流（50 QPS）+ 幂等保护（5 秒）。

### Q5：配置值格式校验告警

当配置值未通过格式校验时（如 `value_type=INTEGER` 但 `config_value=abc`），会记录 `ydsz_system_config_validation_warning_total` 指标。
可通过 Grafana 监控异常配置比例。如需阻止保存，开启 `ydsz.system.config.strict-validation=true`。

### Q6：租户到期未自动锁定

确认 `TenantExpireScheduler` 是否启用（`@EnableScheduling`），检查配置项 `ydsz.system.tenant.expire-check-interval-ms` 是否被覆盖。
调度任务使用单条原子 UPDATE 批量停用到期租户，日志中可搜索 `[TenantExpireScheduler]` 关键词确认执行情况。
