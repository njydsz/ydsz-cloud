# ydsz-common-util 工具类复用规范

## 模块定位

`ydsz-common-util` 是 YDSZ Cloud 项目的通用工具能力中心，提供跨业务模块复用的基础工具方法（ID 生成、加密/国密、HTTP、字符串、集合/Map、IP、并发、认证上下文、YAML、国际化消息等）。

**设计原则：**

- **核心零强制三方依赖**：基础能力仅依赖 JDK + 公共依赖（`ydsz-common-core/-domain/-json`、`snakeyaml`、`slf4j`、`commons-io`）。`spring-security-crypto`、`bcprov-jdk18on`、`spring-web`、`spring-boot-starter-validation`、`transmittable-thread-local` 等为 `optional`，按需引入。
- **无状态、线程安全**：工具类应为无状态或基于 `ThreadLocal` 复用（如 `Cipher`/`Signature`）。
- **单一职责**：每个工具类聚焦一个能力域。
- **方法签名简洁、语义清晰**。

> ⚠️ 本文档与 `README.md` 均严格对齐模块内**真实存在的类**。早期文档中提到的 `SnowflakeUtils`、`JcaCipherPool`、`Rsa2Utils`、`ChaCha20Utils`、`Ed25519Utils`、`DateUtils`、`RequestHolder`、`BeanCopyUtils`、`ServletUtils`、`CookieUtils`、`UrlUtils`、`ContextPropagationUtils` 等类**从未在本模块中实现**，禁止在新代码中引用，也不要为它们编写"迁移指引"。

---

## 能力清单与使用规范

### 字符串处理

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 判空 / 判空白 | `StringUtils` | `isEmpty` / `isNotEmpty` / `isBlank` / `isNotBlank` / `hasText` | 首选 |
| 驼峰 / 下划线互转 | `StringUtils` | `toCamelCase` / `toUnderScoreCase` | - |
| 格式化字符串 | `StringUtils` | `format("{} is {}", name, age)` | SLF4J 风格 |
| 默认值 / 去前缀 | `StringUtils` | `defaultIfBlank` / `removeStart` / `startsWithIgnoreCase` | - |
| 数据脱敏（手机号 / 邮箱 / 身份证） | `SensitiveUtils`（**ydsz-common-safe** 模块） | `mask(value, SensitiveType)` | 本模块 `StringUtils` **不提供**脱敏方法 |

### ID 生成与追踪

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 生成雪花 ID（Spring 环境） | `SnowflakeIdGenerator` | `nextId()`（注入 Bean） | 当前标准做法 |
| 生成雪花 ID（非 Spring / 静态门面） | `IdGenerator` | `nextId()` / `nextIdStr()` | 内部委托 `SnowflakeIdGenerator`，失败冷却后重试 |
| 解析 ID 字段 | `SnowflakeIdGenerator` | `parseTimestamp` / `parseDatacenterId` / `parseWorkerId` / `parseSequence` | 静态方法 |
| 获取 TraceId | `TracerUtils` | `getTraceId()` / `getOrCreateTraceId()` | 链路追踪 |

### IP 地址处理

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| HTTP 请求获取客户端 IP | `ClientIpResolver`（**ydsz-common-safe** 模块） | `getClientIp(request)` | 跨模块复用，推荐 |
| CIDR 网段判断 | `CidrUtils` | `isInRange(ip, cidr)` | 支持 IPv4 / IPv6 |
| 可信代理判断 | `ServletRequestUtils` / `TrustedProxyConfiguration` | `isTrustedProxy(request)` / `isTrusted(ip)` | 本模块内优先 |
| IP 格式校验 | `IpValidator` | `validIpv4` / `validIpv6` / `validIp` | - |
| 内网 / 私网判断 | `IpValidator` | `isInternalIp` / `isPrivateIp` | 零 DNS 解析 |
| 本机网络信息 | `NetworkInterfaceUtils` | `getHostIp` / `getHostName` / `listLocalIps` | - |

### 安全加解密

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| SHA-256 / SHA-512 散列 | `DigestUtils` | `sha256Hex` / `sha512Hex` | - |
| HMAC 签名 | `DigestUtils` | `hmacSha256Hex(data, key)` | API 签名场景 |
| PBKDF2 密钥派生 | `DigestUtils` / `PwdUtils` | `pbkdf2Hex(...)` / `encodePBKDF2*` | - |
| AES 加解密 | `AesUtils` / `AesGcmCrypto` | `AesUtils.encrypt/decrypt`；`new AesGcmCrypto(key).encrypt` | 默认 GCM（AEAD），实例化用法适合高频 |
| 国密 SM2 / SM3 / SM4 | `Sm2Utils` / `Sm3Utils` / `Sm4Utils` | - | 国内合规场景（需 `bcprov-jdk18on`） |
| BCrypt 密码哈希 | `PwdUtils` | `hashPasswordBCrypt` / `verifyPasswordBCrypt` | 依赖 `spring-security-crypto` |
| 密码强度检查 | `PwdUtils` | `checkPasswordStrength` / `checkPasswordStrengthLevel` | SPI 可扩展 |

> 本模块**不提供** RSA（`Rsa2Utils`）、ChaCha20（`ChaCha20Utils`）、Ed25519（`Ed25519Utils`）等非国密国际算法类；如需此类能力，应引入独立的加密模块，而非在本模块新增。

### 集合与对象

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 类型安全取值 | `MapUtils` | `getString` / `getInteger` / `getLong` / `getBoolean` | 避免类型强转 |
| Map → Bean / List 转换 | `MapUtils` | `toBean` / `safeCastList` / `safeCastMap` | - |
| 集合判空 / 转换 / 分组 | `CollectionUtils` | `isEmpty` / `isNotEmpty` / `listToMap` / `listToGroup` / `convertList` / `filter` | - |
| Bean PATCH 更新（仅非 null） | `BeanUpdateUtil` | `copyNonNull(source, target, ignore...)` | 用于部分更新，替代全量拷贝 |

### HTTP 相关

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 请求头 / 参数解析 | `ServletRequestUtils` | `getHeader` / `getParam` / `getIntParam` / `getLongParam` | Servlet 栈专用 |
| AJAX / JSON 判断 | `ServletRequestUtils` | `isAjaxRequest` / `isJsonRequest` | - |
| 响应渲染 | `HttpResponseUtils` | `renderString` / `renderObject` / `renderJson` / `renderError` | JSON 经 `YdszJson` 转义 |
| Token 提取与剥离 | `HttpTokenUtils` | `getToken` / `hasToken` / `stripPrefix` | - |
| 当前请求 / 响应 | `RequestContextUtils` | `getRequest` / `getResponse` | 基于 `RequestContextHolder` |
| URL 路径匹配 | `UrlPathUtils` | `matchAny` / `isIgnoreUrl` | - |

### 并发工具

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 线程池创建 | `ExecutorUtils` | `newFixedThreadPool` / `newCachedThreadPool` / `newCpuBoundThreadPool` / `newVirtualThreadExecutor` / `newScheduledThreadPool` | 统一监控命名 `ydsz-` |
| 流式构建线程池 | `ExecutorUtils` | `builder()` → `ThreadPoolBuilder` | - |

### 认证上下文

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 获取当前认证信息 | `AuthInfoUtils` | `getAuthInfo` / `getTenantId` / `getUniqueId` / `getAccessToken` / `getClaim` | 本模块唯一认证上下文入口 |
| 获取当前 Servlet 请求 | `RequestContextUtils` | `getRequest` | 非 `RequestHolder`（该类不存在） |

---

## 迁移指引（针对历史误述）

早期指南中存在与代码不符的描述，按以下口径纠正：

| 误述 | 实际情况 | 正确做法 |
|------|---------|---------|
| `RandomUtils` "已移除" | `RandomUtils` **真实存在**且被网关、消息、规则、工作流、定时任务等多模块引用 | 继续使用，不要尝试删除 |
| `StringUtils.maskXxx` 已废弃 | 本模块 `StringUtils` 从未提供脱敏方法 | 脱敏改用 `ydsz-common-safe` 的 `SensitiveUtils` |
| `IpAddrUtils.getIpAddr` 已废弃 | 本模块无 `IpAddrUtils` | 客户端 IP 用 `ClientIpResolver`（safe 模块）；可信代理用 `ServletRequestUtils.isTrustedProxy` |
| `DateUtils` 系列方法 | 本模块无 `DateUtils` | 直接使用 JDK `java.time` API |
| `BeanCopyUtils.copyProperties` | 本模块无 `BeanCopyUtils` | 部分更新用 `BeanUpdateUtil.copyNonNull` |
| `RequestHolder.get()` | 本模块无 `RequestHolder` | 认证信息用 `AuthInfoUtils`，请求用 `RequestContextUtils` |

---

## 工具类开发规范

### 新增工具类检查清单

1. **是否已有类似能力？** - 先搜索 `ydsz-common-*` 各模块（优先复用真实存在的类）。
2. **是否零强制三方依赖？** - 优先 JDK 原生实现；非可选依赖需评估。
3. **是否线程安全？** - 确保无状态或使用 `ThreadLocal`（如 `Cipher`/`Signature` 池化）。
4. **是否提供 JavaDoc？** - 包含 `@since` 与准确的用法说明（不要描述未实现的能力）。
5. **是否过度设计？** - 避免为单一调用点引入抽象层 / 统一池化框架（如已删除的 `JcaCipherPool`）；ThreadLocal 池仅在有 ≥2 处真实复用收益时引入。

### 方法命名约定

- 布尔返回：`isXxx` / `hasXxx` / `canXxx`
- 获取单值：`getXxx`（返回 null 表示不存在）
- 转换：`toXxx`（如 `toCamelCase`、`toBean`）
- 判断 / 校验：`validXxx` / `checkXxx`
- 静态门面 / 工具：`XxxUtils`

---

## 版本日志

### 2026-08-09（文档治理）

- 重写本文档与 `README.md`，基于真实 39 个 Java 文件，移除对未实现类（`SnowflakeUtils` / `JcaCipherPool` / `Rsa2Utils` / `ChaCha20Utils` / `Ed25519Utils` / `DateUtils` / `RequestHolder` / `BeanCopyUtils` 等）的引用。
- 纠正 `RandomUtils` "已移除" 的虚假陈述（实际仍被广泛使用）。
- 修正脱敏、IP 解析、Bean 更新等场景的推荐类（指向真实存在的 `SensitiveUtils` / `ClientIpResolver` / `AuthInfoUtils` / `BeanUpdateUtil` 等）。

### 设计基线（v1.0.0）

- 提供 ID 生成、加密/国密、HTTP、字符串、集合/Map、IP、并发、认证上下文、YAML、国际化消息等真实工具类能力。
- `WorkerIdRegistry`、`PasswordStrengthChecker` 作为 SPI 扩展点（前者为预留接口，仓库内暂无默认实现）。
