# ydsz-common-util 架构规范

> 本文档说明 `ydsz-common-util` 模块的设计原则与架构约束。

---

## 1. 模块定位

`ydsz-common-util` 是 YDSZ 快速开发框架的 **L2 工具层**，位于 `ydsz-common-core`（L1 基础设施）之上，为上层业务模块提供通用工具能力。

### 1.1 分层架构

```
L6 应用层：common-app / common-web
L5 业务服务层：common-auth / common-safe / common-feign / ...
L4 基础数据层：common-jdbc / common-redis / common-cache / ...
L3 基础服务层：common-domain / common-exception
L2 工具层：common-util ← 本模块
L1 基础设施层：common-core / common-json
```

### 1.2 模块依赖规则

```
ydsz-common-util 可以依赖：
├── ydsz-common-core（L1）
├── ydsz-common-domain（L3）
├── ydsz-common-json（L1）
└── 第三方库（Spring、Apache Commons、BouncyCastle 等）

ydsz-common-util 不得依赖：
├── ydsz-common-jdbc / redis / cache（L4）
└── ydsz-common-auth / feign / safe（L5）
```

## 2. 设计原则

### 2.1 零强制三方依赖

核心能力不依赖第三方库。可选依赖（如 bcprov、spring-security-crypto）通过 `@ConditionalOnClass` 或运行时检查提供能力：

- **BCrypt 不可用时**：`PwdUtils` 自动降级到 PBKDF2
- **BouncyCastle 不可用时**：国密 API 抛出包含引入指引的 `IllegalStateException`

### 2.2 工具类设计规范

1. **私有构造器**：工具类不允许实例化，通过 `throw new UnsupportedOperationException()` 防止反射实例化
2. **static 方法**：工具类方法全部为 static
3. **null-safe**：公共方法对 null 入参安全处理，返回 null 或默认值而非抛 NPE
4. **不可变状态**：工具类不维护可变状态，确保线程安全

### 2.3 SPI 扩展模式

需要通过 SPI 扩展的能力，提供：
- 接口定义（如 `WorkerIdAllocator`、`PasswordStrengthChecker`、`CryptoProvider`）
- 默认实现
- `META-INF/services` 注册引导

## 3. 包结构规范

```
com.njydsz.common.util
├── auth/          认证上下文（AuthInfo 接口与工具）
├── bean/          Bean 更新
├── collection/    集合工具
├── concurrent/    并发工具（线程池、限流、重试）
├── config/        自动配置
├── http/          HTTP 请求/响应工具
├── id/            分布式 ID 生成
├── ip/            IP 校验与网段计算
├── message/       国际化消息
├── password/      密码哈希与强度校验
├── security/      加密/摘要/国密
├── spring/        Spring 静态上下文
├── string/        字符串工具
└── yaml/          YAML 工具
```

## 4. 命名规范

### 4.1 工具类命名

| 后缀 | 用途 | 示例 |
|------|------|------|
| `Utils` | 通用静态工具类 | `StringUtils`、`CollectionUtils` |
| `Properties` | 配置属性绑定 | `SnowflakeProperties` |
| `HealthIndicator` | Actuator 健康检查 | `SnowflakeHealthIndicator` |

### 4.2 方法命名

| 模式 | 用途 | 示例 |
|------|------|------|
| `getXxx` / `setXxx` | Getter / Setter | `getString`、`setWorkerId` |
| `newXxx` | 创建型（Builder/Factory） | `ExecutorUtils.newCpuBoundThreadPool` |
| `executeWithXxx` | 执行型（可能抛出异常） | `executeWithRetry` |
| `tryXxx` | 非阻塞尝试型 | `tryAcquire` |

## 5. 自动配置

模块通过 `spring-boot-autoconfigure` 提供自动配置：

| 自动配置类 | 启用条件 |
|-----------|---------|
| `UtilAutoConfiguration` | 无条件启用 |
| `SnowflakeIdGenerator` | `ydsz.util.snowflake.enabled != false` |
| `SnowflakeHealthIndicator` | `ydsz.util.snowflake.enabled != false` && `HealthIndicator` 类存在 |
| `MessageSourceConfiguration` | Spring `MessageSource` Bean 存在 |

## 6. 测试规范

- 单元测试使用 JUnit 5 + AssertJ
- 抽象工具类使用 ArchUnit 进行架构约束测试
- 关键路径（ID 生成并发安全、加密认证、密码哈希）必须有测试覆盖

---

> **文档更新时间**：2026-08-13
