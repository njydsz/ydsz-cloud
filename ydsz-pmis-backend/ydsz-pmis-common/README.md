# ydsz-pmis-common

> 公共组件库 + Nacos 共享配置中心

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 库（**不独立部署**） |
| **作用** | 被 8 个部署单元 + 1 个规则引擎库依赖 |
| **构建顺序** | 1/10（Maven 构建最先编译） |
| **JVM 进程** | 无（仅作为 jar 依赖） |

## 核心职责

本模块是 PMIS 全栈的**公共能力底座**，所有业务服务都依赖此模块。

### 1. 统一响应 / 异常 / 分页

| 类 | 作用 |
|---|---|
| `Result<T>` | 业务响应包装（`code` / `message` / `data` / `traceId`） |
| `BizException` + `BizErrorCode` | 业务异常（带错误码） |
| `GlobalExceptionHandler` | 全局异常处理（业务异常 / 参数校验 / 系统异常） |
| `PageResult<T>` / `CursorPageResult<T>` | 分页结果（offset / cursor 两种） |

### 2. 注解 + AOP 切面

| 注解 | Aspect | 作用 |
|---|---|---|
| `@PrePermission` | `PermissionAspect` | RBAC 权限拦截 |
| `@Idempotent` | `IdempotentAspect` | 幂等控制（Redis SET NX EX） |
| `@DistributedLock` | `DistributedLockAspect` | 分布式锁（Redisson） |
| `@RateLimit` | `RateLimiterAspect` | 限流（Resilience4j） |
| `@RequireReAuth` | `RequireReAuthAspect` | 二次认证（密码再输入） |
| `@OperationLog` | `OperationLogAspect` | 操作审计（事件 + 异步落库） |
| `@DataExportAudit` | `DataExportAuditAspect` | 数据导出审计 |
| `@DataScope` | `DataScopeAspect` | 数据权限（6 模式） |
| `@ApiMetrics` | `ApiMetricsAspect` | 接口调用指标（Micrometer） |

### 3. Feign 客户端（带 FallbackFactory）

| 客户端 | 目标服务 | Fallback |
|---|---|---|
| `MessageFeignClient` | ydsz-pmis-message | `MessageFeignClientFallbackFactory` |
| `NotificationPushClient` | ydsz-pmis-message | `NotificationPushClientFallbackFactory` |
| `InitiationFeignClient` | ydsz-pmis-project | `InitiationFeignClientFallbackFactory` |
| `ExecutionClient` | ydsz-pmis-project | `ExecutionClientFallback` |
| `ConfigClient` | ydsz-pmis-system | `ConfigClientFallback` |
| `OrgQueryClient` | ydsz-pmis-userinfo | `OrgQueryClientFallbackFactory` |
| `AgentClient` | ydsz-pmis-agent | `AgentClientFallbackFactory` |

> 全部 Feign 客户端**必须**配 `FallbackFactory` 防止级联雪崩。

### 4. 基础设施

| 能力 | 类 |
|---|---|
| JWT Token | `JwtTokenProvider` |
| TOTP 2FA | `TotpUtil` |
| 敏感数据 7 策略 | `SensitiveStrategy` + `SensitiveSerializer` |
| 加密字段迁移 | `EncryptedFieldMigrationService` |
| 雪花 ID | `PmisSnowflakeIdentifierGenerator` |
| 链路追踪 | `TraceIdFilter` + `TracerHolder` |
| 异常聚合 | `SentryCapture` + `SentryCaptureAspect` |
| 混沌工程 | `ChaosService` + `ChaosExperiment` |
| 特性开关 | `FeatureFlag` + `FeatureFlagService` |
| JobHandler | `JobHandler` / `MapTask` / `MapProcessor` / `MapReduceProcessor` |
| KMS 密钥 | `SecretManager` + `JasyptSecretProvider` / `EnvironmentSecretProvider` |
| 对账引擎 | `ReconcileEngine` + `ReconcileHandler` |

### 5. 配置中心模板

本模块还承担 **Nacos 共享配置模板** 的角色：

```
ydsz-pmis-common/src/main/resources/
├── application.yml               # common 模块本地配置
├── application-sentry.yml        # Sentry 配置
├── logback-spring.xml            # 日志配置
├── messages.properties           # 中英文国际化文案
├── messages_en_US.properties
├── validation-messages.properties
├── validation-messages_en_US.properties
└── nacos-config/                 # ⭐ 部署到 Nacos 的共享配置模板
    ├── README.md
    └── ydsz-pmis-common.yaml     # 数据源/Redis/MyBatis-Plus/SpringDoc 等共享配置
```

> **单一来源原则**：所有 7 个部署单元的公共配置，**只能**在 `nacos-config/ydsz-pmis-common.yaml` 维护。
> 各服务 `bootstrap.yml` 通过 `spring.cloud.nacos.config.shared-configs` 引用此 dataId。

## 目录结构

```
ydsz-pmis-common/
├── pom.xml
├── src/main/java/com/njydsz/pmis/common/
│   ├── annotation/      # 9 个注解
│   ├── api/             # Result / PageResult / BizErrorCode
│   ├── aspect/          # 9 个 AOP
│   ├── chaos/           # 混沌工程
│   ├── config/          # 14 个自动配置类
│   ├── constant/        # 6 个常量类
│   ├── datasource/      # 数据源相关
│   ├── entity/          # BaseDO / 分页查询
│   ├── event/           # 事件定义
│   ├── excel/           # Excel 工具
│   ├── exception/       # BizException / GlobalExceptionHandler
│   ├── featureflag/     # 特性开关
│   ├── feign/           # 7 个 Feign 客户端 + Fallback
│   ├── filter/          # TraceId/XSS/SameSite/StrictContentType
│   ├── health/          # 健康检查
│   ├── interceptor/     # AuthInterceptor
│   ├── job/             # JobHandler / MapTask / MapReduce
│   ├── kms/             # 密钥管理
│   ├── migration/       # 加密字段迁移
│   ├── permission/      # 权限码
│   ├── reconcile/       # 对账引擎
│   ├── security/        # 登录用户/密码策略/账号锁定
│   ├── sensitive/       # 7 种脱敏策略
│   ├── sentry/          # Sentry
│   ├── service/         # BloomFilter
│   ├── token/           # JWT
│   ├── tracing/         # 链路追踪
│   ├── tx/              # 事务后置处理
│   └── util/            # 14 个工具类
└── src/main/resources/
    ├── application.yml
    └── nacos-config/    # ⭐ Nacos 共享配置模板
```

## 配置文件

| 文件 | 用途 | 加载时机 |
|---|---|---|
| `application.yml` | common 模块本地配置（关闭 web 容器） | Maven 单元测试 |
| `application-sentry.yml` | Sentry 异常聚合 | 由 `spring.profiles.active` 激活 |
| `logback-spring.xml` | Logback 日志配置 | 应用启动 |
| `nacos-config/ydsz-pmis-common.yaml` | **Nacos 共享配置**（数据源/Redis/MP 等） | 通过 `bootstrap.yml` 拉取 |

> **重要**：`application.yml` 中的 `web-application-type: none` 表示本模块**不启动** Tomcat，
> 不会被独立部署，仅作为依赖 jar 提供能力。

## 启动

**本模块不独立启动**。如需本地调试单个工具类，可：

```bash
# 1. 编译并安装到本地仓库
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-common -am clean install

# 2. 其他服务会自动通过 Maven 依赖此 jar
mvn -pl ydsz-pmis-gateway spring-boot:run
```

## 测试

```bash
# 仅测试 common 模块
mvn -pl ydsz-pmis-common -am test

# 全模块回归
mvn -pl ydsz-pmis-backend -am test
```

## Nacos 共享配置部署

```bash
# 一次性导入 dev/sit/uat/prod 4 个环境
for env in dev sit uat prod; do
  ./deploy/ubuntu/scripts/import-nacos-config.sh pmis $env
done
```

Windows 等价：

```powershell
deploy\windows\scripts\import-nacos-config.bat pmis dev
```

> 脚本优先从 `ydsz-pmis-common/src/main/resources/nacos-config/` 读取，向后兼容 `deploy/common/nacos/`。

## 版本与变更

- **首发版本**：v1.0.0（2026-06-30）
- **当前版本**：v1.3.0-SNAPSHOT
- **变更需走 PR + Code Review**

---

> 任何对本模块的修改（注解 / 切面 / Feign / 工具类）都会影响所有 8 个部署单元，
> 必须经过充分测试与跨服务联调验证。
