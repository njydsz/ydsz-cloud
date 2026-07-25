# ydsz-system

> 系统基础服务（System Foundation）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9002**（按构建顺序 3/8） |
| **服务名** | `ydsz-system` |
| **构建顺序** | 3/8 |
| **数据库** | PostgreSQL（共享主库） |
| **依赖** | Nacos、PostgreSQL、Redis |

## 核心职责

本模块是 YDSZ 的**系统级基础服务**，承担**横切关注点**。

| 业务域 | 说明 |
|---|---|
| **系统配置** | 参数配置（`ydsz_config`），支持按 key 查询、Redis 缓存、值类型校验 |
| **数据字典** | 字典类型（`ydsz_dict_type`）+ 字典项（`ydsz_dict_item`），支持树形字典、缓存、版本管理 |
| **应用注册** | OAuth2 应用注册（`ydsz_app_info`），支持 BCrypt 密钥校验 |
| **系统变量** | 业务级变量管理（`ydsz_variable`），可对接 common-config 热加载 |
| **字典版本** | 字典变更历史快照（`ydsz_dict_version`），支持回滚与变更审计 |

## DDD 分层结构

```
ydsz-system/
├── pom.xml
├── ydsz-system-api/                    # API 层：Feign Client + Fallback
│   └── src/main/java/com/njydsz/system/api/client/
│       ├── AppInfoClient.java
│       ├── AppInfoClientFallback.java
│       ├── ConfigClient.java
│       └── ConfigClientFallback.java
├── ydsz-system-domain/                 # 领域层：Entity + DTO + VO
│   └── src/main/java/com/njydsz/system/domain/
│       ├── entity/                     # DO（ConfigDO, DictItemDO, DictTypeDO, AppInfoDO, VariableDO, DictVersionDO）
│       ├── dto/                        # 创建/更新 DTO（含 JSR-303 校验）
│       └── vo/                         # 视图对象（不含敏感字段如 appSecret）
├── ydsz-system-infra/                  # 基础设施层：MyBatis Mapper
│   └── src/main/java/com/njydsz/system/infra/mapper/
│       ├── AppInfoMapper.java          # 含 selectEnabledByAppKey 自定义查询
│       ├── ConfigMapper.java           # 含 selectByConfigKey 自定义查询
│       ├── DictItemMapper.java         # 含 selectByTypeAndCode / listEnabledByTypeCode
│       ├── DictTypeMapper.java
│       ├── DictVersionMapper.java      # 含 listByTypeCode
│       └── VariableMapper.java
├── ydsz-system-server/                 # 应用层：Service + Health + Metrics
│   └── src/main/java/com/njydsz/system/server/
│       ├── service/                    # 接口 + impl（含缓存、事务、指标）
│       │   ├── AppInfoService.java
│       │   ├── ConfigService.java
│       │   ├── DictService.java
│       │   ├── DictItemService.java
│       │   ├── DictVersionService.java
│       │   ├── VariableService.java
│       │   └── impl/
│       ├── health/                     # SystemHealthIndicator
│       └── metrics/                    # SystemMetrics（Micrometer）
└── ydsz-system-web/                    # Web 层：Controller + Bootstrap
    └── src/main/java/com/njydsz/system/web/
        ├── SystemApplication.java
        └── controller/
            ├── AppInfoController.java      # /api/v1/app
            ├── ConfigController.java        # /api/v1/config
            ├── DictController.java          # /api/v1/dict/type
            ├── DictItemController.java      # /api/v1/dict/item
            ├── InternalApiController.java   # /api/internal（Feign 内部调用）
            └── VariableController.java      # /api/v1/variable
```

## 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/config` | 系统参数 CRUD + 按 key 查询 |
| `/api/v1/dict/type` | 字典类型 CRUD |
| `/api/v1/dict/item` | 字典项 CRUD + 按类型查询 |
| `/api/v1/app` | 应用注册 CRUD |
| `/api/v1/variable` | 系统变量 CRUD |
| `/api/internal/config/get` | Feign 内部调用：按 key 查配置值 |
| `/api/internal/dict/item` | Feign 内部调用：按类型+编码查字典项 |
| `/api/internal/app/validate` | Feign 内部调用：校验应用密钥 |

## 数据库表

| 表名 | 说明 |
|---|---|
| `ydsz_config` | 系统参数（key-value，支持租户维度） |
| `ydsz_dict_type` | 字典类型 |
| `ydsz_dict_item` | 字典项（支持树形 parent_id、扩展 ext_json） |
| `ydsz_dict_version` | 字典版本（变更历史快照） |
| `ydsz_app_info` | 应用注册（OAuth2 client_id/client_secret） |
| `ydsz_variable` | 系统变量 |

## 核心能力

### 配置缓存
- 按 `config_key` 缓存配置值到 Redis，TTL 5 分钟
- 写操作（save/update/delete）自动清除缓存

### 字典缓存
- 按 `type_code:item_code` 缓存单个字典项，TTL 10 分钟
- 按 `type_code` 缓存字典项列表，TTL 10 分钟
- 写操作自动清除对应类型的缓存

### 应用密钥安全
- `appSecret` 使用 BCrypt 加密存储
- `validateClient` 使用 `BCryptPasswordEncoder.matches()` 校验
- `AppInfoVO` 不包含 `appSecret` 字段，避免泄露

### 可观测性
- `SystemMetrics`：Micrometer 指标（配置读取次数/耗时、缓存命中/未命中、字典查询、应用校验）
- `SystemHealthIndicator`：Redis 连通性 + 配置表/字典表可达性检查
- `@Audit` 注解：所有写操作自动记录审计日志

## Feign 接口

被以下 Feign 客户端调用（位于 `ydsz-system-api`）：

- `ConfigClient` → `/api/internal/config/get`、`/api/internal/dict/item`
- `AppInfoClient` → `/api/internal/app/validate`

## 启动

```bash
cd ydsz-backend
mvn -pl ydsz-system spring-boot:run
```

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
  port: 9002
```

## 常见问题

### Q1：配置查询返回 null

检查 `ydsz_config` 表中是否存在对应的 `config_key` 且 `status = 'ENABLED'`、`deleted = 0`。
配置查询走 Redis 缓存，如果 Redis 不可用会降级为直接查询数据库。

### Q2：应用密钥校验失败

1. 确认 `ydsz_app_info` 表中 `app_key` 存在且 `status = 'ENABLED'`
2. 确认 `app_secret` 字段存储的是 BCrypt 加密后的哈希值（非明文）
3. 创建应用时通过 API 传入明文密钥，Service 会自动 BCrypt 加密

### Q3：字典项查询缓存不刷新

字典项写操作（save/update/delete）会自动清除对应 `type_code` 的缓存。
如果缓存未清除，检查 Redis 连接是否正常。
