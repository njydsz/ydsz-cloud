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

## 数据库初始化

> 项目规范**禁止**使用 Flyway / Liquibase 等 schema-migration 框架。数据库 DDL 统一以 SQL 脚本形式管理。

```bash
psql -U postgres -d ydsz_cloud -f ydsz-system/deploy/sql/init.sql
```

脚本包含 9 张表（`ydsz_config` / `ydsz_dict_type` / `ydsz_dict_item` / `ydsz_entity_version` / `ydsz_app_info` / `ydsz_variable` / `ydsz_tenant` / `ydsz_tenant_plan` / `ydsz_tenant_plan_menu`）的建表 DDL、索引与初始数据（内置租户、默认套餐、示例字典与配置）。

## 核心职责

本模块是 YDSZ 的**系统级基础服务**，承担**横切关注点**。

| 业务域 | 说明 |
|---|---|
| **系统配置** | 参数配置（`ydsz_config`），支持按 key 查询、按 group 批量查询、公开配置查询、Redis 缓存、缓存穿透防护、值类型校验 |
| **数据字典** | 字典类型（`ydsz_dict_type`）+ 字典项（`ydsz_dict_item`），支持树形字典、Redis 缓存、版本管理、批量写入 |
| **应用注册** | OAuth2 应用注册（`ydsz_app_info`），支持 BCrypt 密钥校验（强度可配置） |
| **系统变量** | 业务级变量管理（`ydsz_variable`），支持 Redis 缓存、缓存穿透防护 |
| **实体版本** | 通用实体变更历史快照（`ydsz_entity_version`），写操作自动记录变更、支持查询审计与回滚 |
| **多租户** | 租户（`ydsz_tenant`）+ 套餐（`ydsz_tenant_plan`）+ 套餐菜单（`ydsz_tenant_plan_menu`），支持租户级菜单定制 |
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
│       ├── entity/                     # 持久化实体（无 DO 后缀，符合 entity-naming 规范）
│       │   ├── AppInfo.java            # OAuth2 应用注册
│       │   ├── Config.java             # 系统参数（充血模型：validate / getTypedValue 等领域方法）
│       │   ├── DictItem.java           # 字典项
│       │   ├── DictType.java           # 字典类型
│       │   ├── EntityVersion.java      # 通用实体版本快照
│       │   ├── Variable.java           # 系统变量
│       │   ├── Tenant.java             # 租户
│       │   ├── TenantPlan.java         # 租户套餐
│       │   └── TenantPlanMenu.java     # 套餐菜单关联
│       ├── mapper/                     # 9 个 MyBatis Mapper
│       ├── repository/                 # 8 个仓储实现
│       └── converter/                  # SystemConverter（MapStruct）
├── ydsz-system-server/                 # 应用层：Service + Config + Health + Metrics + Cache + Listener + Schedule
│   └── src/main/java/com/njydsz/system/server/
│       ├── config/                     # SystemProperties + SystemConfiguration + CacheConfig + InternalApiIpFilter + RedisConfig
│       ├── service/                    # 14 个 Service 接口 + impl
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
│       │   └── impl/                       # 14 个实现类
│       │       └── rollback/               # 回滚策略（4 个）
│       │           ├── RollbackStrategy.java
│       │           ├── ConfigRollbackStrategy.java
│       │           ├── VariableRollbackStrategy.java
│       │           └── DictItemRollbackStrategy.java
│       ├── cache/                     # 缓存组件
│       │   ├── CacheKeyBuilder.java    # 缓存键构造器
│       │   ├── CacheInvalidationPublisher.java  # Redis Pub/Sub 缓存失效发布
│       │   ├── CacheInvalidationSubscriber.java # Redis Pub/Sub 缓存失效订阅
│       │   └── CacheWarmer.java        # 缓存预热
│       ├── listener/                  # 事件监听器
│       │   ├── VersionSnapshotListener.java    # 版本快照监听
│       │   └── CrossModuleEventListener.java   # 跨模块事件监听
│       ├── schedule/                  # 定时任务
│       │   └── TenantExpireScheduler.java      # 租户到期检查
│       ├── health/                    # SystemHealthIndicator（Redis + DataSource 探针）
│       ├── metrics/                   # SystemMetrics（Micrometer，前缀 ydsz_system_）
│       └── vo/                        # 服务端 VO（ConfigExcelVO / DictItemExcelVO / VariableExcelVO）
└── ydsz-system-web/                    # Web 层：Controller + Bootstrap
    └── src/main/java/com/njydsz/system/web/
        ├── SystemApplication.java
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
| `/api/v1/config` | 系统参数 CRUD + 按 key 查询 + 按 group 批量查询 + 公开配置查询 |
| `/api/v1/dict/type` | 字典类型 CRUD（支持搜索过滤） |
| `/api/v1/dict/item` | 字典项 CRUD + 按类型查询 + 树形查询 + 批量写入 |
| `/api/v1/dict/version` | 字典版本历史查询 |
| `/api/v1/config/version` | 配置版本历史 + 回滚 |
| `/api/v1/variable/version` | 变量版本历史 + 回滚 |
| `/api/v1/tenant` `/api/v1/tenant-plan` | 租户 / 套餐 / 套餐菜单管理 |
| `/api/v1/app` | 应用注册 CRUD（支持搜索过滤） |
| `/api/v1/variable` | 系统变量 CRUD + 按 key 查询值 |
| `/api/v1/admin/audit` | 审计日志查询（AuditAdminController） |
| `/api/v1/system/init` | 前端初始化聚合接口（公开配置 + 默认字典 + 系统版本号） |
| `POST /api/internal/config/get` | Feign 内部调用：按 key 查配置值（POST body 传输） |
| `POST /api/internal/dict/item` | Feign 内部调用：按类型+编码查字典项（POST body 传输） |
| `POST /api/internal/dict/list` | Feign 内部调用：按类型查全部启用字典项列表（POST body 传输） |
| `POST /api/internal/app/validate` | Feign 内部调用：校验应用密钥（POST body 传输，不暴露密钥） |

## 数据库表

| 表名 | 说明 |
|---|---|
| `ydsz_config` | 系统参数（key-value，含 `tenant_id` 列 + 唯一索引 `uk_config_group_key(tenant_id, config_group, config_key)`，多租户隔离在 schema 层保证） |
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
- 基于 Redis 二级缓存，缓存键：`system:config:value:{configKey}`（单值）/ `system:config:group:{configGroup}`（组批量）/ `system:config:public`（公开配置）
- TTL 通过 `ydsz.system.config.cache-ttl-minutes` 配置（默认 **15 分钟**）
- 缓存穿透防护：空值缓存
- 写操作（save/update/delete）自动清除缓存 + 发布 `CONFIG_CHANGED` 事件
- 跨实例缓存失效：通过 Redis Pub/Sub 实现（`CacheInvalidationPublisher`/`CacheInvalidationSubscriber`），由 `ydsz.system.cache.crossInstanceEnabled` 控制（默认 false）
- 缓存预热：`CacheWarmer` 在启动时加载热点数据

### 字典缓存
- 基于 Redis 缓存，缓存键：`ydsz:dict:item:{typeCode}:{itemCode}`（单条）/ `ydsz:dict:list:{typeCode}`（列表）
- TTL 通过 `ydsz.system.dict.cache-ttl-minutes` 配置（默认 **30 分钟**）
- 缓存穿透防护：空值写入哨兵
- 写操作自动失效对应类型缓存 + 记录版本快照，并发布 `DICT_TYPE_CHANGED` 事件

### 系统变量缓存
- 基于 Redis 缓存，缓存键：`system:variable:{variableKey}`
- TTL 通过 `ydsz.system.variable.cache-ttl-minutes` 配置（默认 **15 分钟**）
- 缓存穿透防护：空值写入哨兵
- 写操作自动清除缓存 + 发布 `VARIABLE_CHANGED` 事件

### 应用密钥安全
- `appSecret` 使用 BCrypt 加密存储，强度可配置（`ydsz.system.app.bcrypt-strength`）
- `validateClient` 使用 `BCryptPasswordEncoder.matches()` 校验
- `AppInfoVO` 不包含 `appSecret` 字段，避免泄露
- 内部 API 使用 POST body 传输密钥，不暴露在 URL 中

### 实体版本管理（EntityVersion）
- 配置 / 变量 / 字典等实体的写操作（save/update/delete）自动记录版本快照到 `ydsz_entity_version`
- 版本记录包含实体类型、实体 ID、版本号、变更内容（diff）
- 支持版本历史查询与**回滚**（`/api/v1/config/version`、`/api/v1/variable/version` 等）
- 回滚策略：`RollbackStrategy` 接口 + 具体实现（ConfigRollbackStrategy / VariableRollbackStrategy / DictItemRollbackStrategy）

### 前端初始化
- `FrontendInitController`（`/api/v1/system/init`）聚合返回公开配置 + 默认字典 + 系统版本号
- 支持按需指定字典类型（`/api/v1/system/init/dicts?dictTypes=user_status,gender`）
- 限流保护（100 QPS），防止恶意刷接口

### 可观测性
- `SystemMetrics`：Micrometer 指标（前缀 `ydsz_system_`）
  - `ydsz_system_config_validation_warning_total` — 配置值格式校验告警
  - `ydsz_system_app_validate_success_total` / `ydsz_system_app_validate_fail_total` — 应用密钥校验成功/失败
- `SystemHealthIndicator`：Redis 连通性 + DataSource 可达性检查（轻量探针）
- `@Audit` 注解：所有写操作自动记录审计日志（`sys_audit_log`，common-audit）

### 分页搜索
- 所有分页接口支持搜索过滤（configGroup/configKey/status 等）
- Service `page()` 方法返回 `PageResponse<VO>`（继承自 `YdszResponse<T>`），自带 `getPageNum()`/`getPageSize()`/`getPages()` 便捷方法

### 批量操作
- `ConfigBatchService`：配置批量操作
- `DictItemBatchService`：字典项批量写入
- `ConfigExcelService`：配置 Excel 导入导出（含 ConfigExcelVO / DictItemExcelVO / VariableExcelVO）

### 租户到期管理
- `TenantExpireScheduler`：定时检查租户到期状态，自动处理过期租户

## 配置

**bootstrap.yml**：

```yaml
spring:
  application:
    name: ydsz-system
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        file-extension: yaml
        shared-configs:
          - data-id: ydsz-common.yaml
            refresh: true
          - data-id: ydsz-system.yml
            refresh: true
server:
  port: 9001
```

**可配置属性**（`ydsz.system.*`）：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `ydsz.system.health-enabled` | true | 是否启用健康检查 |
| `ydsz.system.config.enabled` | true | 是否启用配置服务 |
| `ydsz.system.config.cache-ttl-minutes` | 15 | 配置缓存 TTL（分钟） |
| `ydsz.system.config.strict-validation` | false | 配置值格式严格校验：true 时格式非法阻止保存，false 仅告警放行（生产建议开启） |
| `ydsz.system.dict.enabled` | true | 是否启用字典服务 |
| `ydsz.system.dict.cache-ttl-minutes` | 30 | 字典缓存 TTL（分钟） |
| `ydsz.system.variable.enabled` | true | 是否启用变量服务 |
| `ydsz.system.variable.cache-ttl-minutes` | 15 | 变量缓存 TTL（分钟） |
| `ydsz.system.app.bcrypt-strength` | 10 | BCrypt 加密强度（4-31） |
| `ydsz.system.cache.cross-instance-enabled` | false | 是否启用跨实例缓存失效（Redis Pub/Sub），多实例部署且需实时一致性时开启 |
| `ydsz.system.version` | 1.0.0 | 系统版本号（前端展示） |

> 说明：内部 API 的 IP 白名单已由 `ydsz.system.internal-api-ip-whitelist` 迁移至 `ydsz.safe.ip-access`（common-safe 统一管控，支持 CIDR 网段）。

## Feign 接口

被以下 Feign 客户端调用（位于 `ydsz-system-api`）：

- `ConfigClient` → 配置查询（`getConfig`）
- `DictClient` → 字典项 / 字典列表查询
- `AppInfoClient` → 应用密钥校验（`/api/internal/app/validate`）

## 启动

```bash
cd ydsz-cloud
mvn -pl ydsz-system spring-boot:run
```

## 常见问题

### Q1：配置查询返回 null

检查 `ydsz_config` 表中是否存在对应的 `config_key` 且 `status = 'ENABLED'`、`deleted = 0`。
配置查询走 Redis 缓存，空值会缓存（TTL 默认 15 分钟），期间重复查询返回 null。

### Q2：应用密钥校验失败

1. 确认 `ydsz_app_info` 表中 `app_key` 存在且 `status = 'ENABLED'`
2. 确认 `app_secret` 字段存储的是 BCrypt 加密后的哈希值（非明文）
3. 创建应用时通过 API 传入明文密钥，Service 会自动 BCrypt 加密
4. BCrypt 强度可通过 `ydsz.system.app.bcrypt-strength` 配置

### Q3：字典项查询缓存不刷新

字典项写操作（save/update/delete）会自动失效对应 `type_code` 的 Redis 缓存。
如果缓存未清除，检查 Redis 连接是否正常。多实例部署时可开启 `ydsz.system.cache.cross-instance-enabled` 实现跨实例缓存失效。

### Q4：内部 API 安全

内部 API（`/api/internal/**`）使用 POST + body 传输参数，appSecret 不暴露在 URL 中。
Gateway 应配置路由规则限制 `/api/internal/**` 仅允许内部服务调用。
所有内部 API 启用限流（50 QPS）+ 幂等保护（5 秒）。

### Q5：配置值格式校验告警

当配置值未通过格式校验时（如 `value_type=INTEGER` 但 `config_value=abc`），会记录 `ydsz_system_config_validation_warning_total` 指标。
可通过 Grafana 监控异常配置比例。如需阻止保存，开启 `ydsz.system.config.strict-validation=true`。
