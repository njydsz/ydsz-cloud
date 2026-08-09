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
| **字典版本** | 字典变更历史快照（`ydsz_dict_version`），写操作自动记录变更、支持查询审计 |
| **多租户** | 租户（`ydsz_tenant`）+ 套餐（`ydsz_tenant_plan`）+ 套餐菜单（`ydsz_tenant_plan_menu`），支持租户级菜单定制 |
| **全局搜索** | `GlobalSearchController` + `SystemSearchController`，基于 common-search 聚合各模块 SearchProvider |

## DDD 分层结构

```
ydsz-system/
├── pom.xml
├── ydsz-system-api/                    # API 层：Feign Client + Fallback
│   └── src/main/java/com/njydsz/system/api/client/
│       ├── AppInfoClient.java          # POST /api/internal/app/validate
│       ├── AppInfoClientFallback.java
│       ├── ConfigClient.java           # POST /api/internal/config/get + /dict/item
│       └── ConfigClientFallback.java
├── ydsz-system-domain/                 # 领域层：Entity + DTO + VO
│   └── src/main/java/com/njydsz/system/domain/
│       ├── entity/                     # 实体（无 DO 后缀，符合 entity-naming 规范）
│       │   ├── AppInfo.java            # OAuth2 应用注册
│       │   ├── Config.java             # 系统参数
│       │   ├── DictItem.java           # 字典项
│       │   ├── DictType.java           # 字典类型
│       │   ├── DictVersion.java        # 字典版本快照
│       │   ├── Variable.java           # 系统变量
│       │   ├── Tenant.java             # 租户
│       │   ├── TenantPlan.java         # 租户套餐
│       │   └── TenantPlanMenu.java     # 套餐菜单关联
│       ├── dto/                        # 创建/更新 DTO（含 JSR-303 校验）
│       └── vo/                         # 视图对象（不含敏感字段如 appSecret）
├── ydsz-system-infra/                  # 基础设施层：MyBatis Mapper
│   └── src/main/java/com/njydsz/system/infra/mapper/
│       ├── AppInfoMapper.java          # 含 selectEnabledByAppKey 自定义查询
│       ├── ConfigMapper.java           # 含 selectByConfigKey 自定义查询
│       ├── DictItemMapper.java         # 含 selectByTypeAndCode / listEnabledByTypeCode
│       ├── DictTypeMapper.java
│       ├── DictVersionMapper.java      # 含 listByTypeCode
│       ├── TenantMapper.java           # 租户 CRUD
│       ├── TenantPlanMapper.java       # 租户套餐 CRUD
│       ├── TenantPlanMenuMapper.java   # 套餐菜单关联
│       └── VariableMapper.java
│   └── src/main/java/com/njydsz/system/infra/repository/
│       ├── ConfigRepository.java       # Config 仓储
│       └── DictRepository.java         # Dict 仓储
├── ydsz-system-server/                 # 应用层：Service + Config + Health + Metrics
│   └── src/main/java/com/njydsz/system/server/
│       ├── config/                     # SystemProperties + SystemConfiguration
│       ├── service/                    # 接口 + impl（含缓存、事务、指标、版本快照）
│       │   ├── AppInfoService.java
│       │   ├── ConfigService.java
│       │   ├── DictService.java
│       │   ├── DictItemService.java
│       │   ├── DictVersionService.java
│       │   ├── VariableService.java
│       │   └── impl/
│       ├── health/                     # SystemHealthIndicator（轻量探针）
│       └── metrics/                    # SystemMetrics（Micrometer，含 dict 专用指标）
└── ydsz-system-web/                    # Web 层：Controller + Bootstrap
    └── src/main/java/com/njydsz/system/web/
        ├── SystemApplication.java
        └── controller/
            ├── AppInfoController.java      # /api/v1/app
            ├── ConfigController.java        # /api/v1/config
            ├── DictController.java          # /api/v1/dict/type
            ├── DictItemController.java      # /api/v1/dict/item
            ├── DictVersionController.java   # /api/v1/dict/version
            ├── GlobalSearchController.java  # /api/v1/search 全局搜索聚合
            ├── InternalApiController.java   # /api/internal（POST + body，Feign 内部调用）
            ├── SystemSearchController.java  # /api/v1/system/search 系统模块搜索
            └── VariableController.java      # /api/v1/variable
```

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/config` | 系统参数 CRUD + 按 key 查询 + 按 group 批量查询 + 公开配置查询 |
| `/api/v1/dict/type` | 字典类型 CRUD（支持搜索过滤） |
| `/api/v1/dict/item` | 字典项 CRUD + 按类型查询 + 树形查询 |
| `/api/v1/dict/version` | 字典版本历史查询 |
| `/api/v1/app` | 应用注册 CRUD（支持搜索过滤） |
| `/api/v1/variable` | 系统变量 CRUD + 按 key 查询值 |
| `/api/v1/search` | 全局搜索（聚合各模块 SearchProvider，基于 common-search） |
| `/api/v1/system/search` | 系统模块内搜索（Config/Dict/AppInfo） |
| `POST /api/internal/config/get` | Feign 内部调用：按 key 查配置值（POST body 传输） |
| `POST /api/internal/dict/item` | Feign 内部调用：按类型+编码查字典项（POST body 传输） |
| `POST /api/internal/app/validate` | Feign 内部调用：校验应用密钥（POST body 传输，不暴露密钥） |

## 数据库表

| 表名 | 说明 |
|---|---|
| `ydsz_config` | 系统参数（key-value，支持租户维度） |
| `ydsz_dict_type` | 字典类型 |
| `ydsz_dict_item` | 字典项（支持树形 parent_id、扩展 ext_json） |
| `ydsz_dict_version` | 字典版本（变更历史快照，含 tenant_id） |
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

### 字典版本管理
- 字典项写操作（save/update/delete）自动记录版本快照
- 版本记录包含 typeCode、版本号、变更说明
- 支持 `GET /api/v1/dict/version/{typeCode}` 查询版本历史

### 可观测性
- `SystemMetrics`：Micrometer 指标
  - `system.config.read.total/duration` — 配置读取次数/耗时
  - `system.config.cache.hit/miss` — 配置缓存命中/未命中
  - `system.dict.query.total/duration` — 字典查询次数/耗时
  - `system.dict.cache.hit/miss` — 字典缓存命中/未命中（专用指标）
  - `system.app.validate.success/fail` — 应用校验成功/失败
- `SystemHealthIndicator`：Redis 连通性 + 配置表/字典表可达性检查（轻量探针，不走 COUNT）
- `@Audit` 注解：所有写操作自动记录审计日志

### 分页搜索
- 所有分页接口支持搜索过滤（configGroup/configKey/status 等）
- Service `page()` 方法返回 `IPage<VO>`，Controller 无需重复 toVO 转换

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
        file-extension: yml
        shared-configs:
          - data-id: ydsz-common.yml
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
| `ydsz.system.config.cache-ttl-minutes` | 5 | 配置缓存 TTL（分钟） |
| `ydsz.system.config.hot-reload-enabled` | true | 配置热加载（预留） |
| `ydsz.system.dict.cache-ttl-minutes` | 10 | 字典缓存 TTL（分钟） |
| `ydsz.system.app.bcrypt-strength` | 10 | BCrypt 加密强度（4-31） |
| `ydsz.system.internal-api-ip-whitelist` | [] | 内部 API IP 白名单 |

## Feign 接口

被以下 Feign 客户端调用（位于 `ydsz-system-api`）：

- `ConfigClient` → `POST /api/internal/config/get`、`POST /api/internal/dict/item`
- `AppInfoClient` → `POST /api/internal/app/validate`

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
