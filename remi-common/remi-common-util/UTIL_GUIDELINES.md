# remi-common-util 工具类复用规范

## 模块定位

remi-common-util 是 Remi Cloud 项目的通用工具能力中心，提供跨业务模块复用的基础工具方法。

**设计原则：**
- 零第三方依赖（JDK 原生实现优先）
- 无状态、线程安全
- 单一职责：每个工具类聚焦一个能力域
- 方法签名简洁、语义清晰

---

## 能力清单与使用规范

### 字符串处理

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 判空/判空白 | `StringUtils` | `isEmpty`, `isBlank`, `hasText` | 首选 |
| 驼峰/下划线互转 | `StringUtils` | `toCamelCase`, `toUnderScoreCase` | - |
| 格式化字符串 | `StringUtils` | `format("{} is {}", name, age)` | SLF4J 风格 |
| 手机号/邮箱脱敏 | ❌ **已废弃** | `StringUtils.maskMobile` 等 | 统一使用 `SensitiveUtils` |
| 数据脱敏 | `SensitiveUtils` | `mask(value, SensitiveType.MOBILE)` | remi-common-safe 模块 |

### ID 生成与追踪

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 生成雪花 ID | `SnowflakeUtils` | `nextId()` | 核心能力，使用广泛 |
| 获取 TraceId | `TracerUtils` | `getTraceId()` | 链路追踪 |

### IP 地址处理

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| HTTP 请求获取客户端 IP | `ClientIpResolver` | `getClientIp(request)` | remi-common-safe 模块，**推荐** |
| 旧版 Servlet IP 解析 | ❌ `IpAddrUtils.getIpAddr` | - | 已废弃，迁移到 ClientIpResolver |
| 可信代理判断 | `ClientIpResolver` | `isTrustedProxy(ip)` | - |
| CIDR 网段判断 | `CidrUtils` | `isInRange(ip, cidr)` | 支持 IPv4/IPv6 |
| IP 格式校验 | `IpValidator` | `validIpv4`, `validIpv6` | - |

### 安全加解密

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| SHA-256/512 散列 | `DigestUtils` | `sha256Hex(input)` | - |
| HMAC 签名 | `DigestUtils` | `hmacSha256Hex(data, key)` | API 签名场景 |
| PBKDF2 密码派生 | `DigestUtils` | `pbkdf2Hex(pwd, salt, iter, len)` | - |
| AES 加解密 | `AesUtils` / `AesGcmCrypto` | - | 按需使用 |
| 国密 SM2/SM3/SM4 | `Sm2Utils` / `Sm3Utils` / `Sm4Utils` | - | 国内合规场景 |
| BCrypt 密码哈希 | `PwdUtils` | `hashPasswordBCrypt(pwd)` | PBKDF2 也支持 |
| 密码强度检查 | `PwdUtils` | `checkPasswordStrength(pwd)` | SPI 可扩展 |

### 集合工具

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 安全取值 | `MapUtils` | `getStr(map, key)`, `getInt(map, key)` | 避免类型强转 |
| 空安全集合判空 | `CollectionUtils` | `isEmpty`, `isNotEmpty` | - |

### 时间处理

| 需求场景 | 推荐方式 | 备注 |
|---------|---------|------|
| 当前时间获取 | JDK: `LocalDateTime.now()` | 不推荐 DateUtils |
| JDK 原生 API | `DateTimeFormatter.ISO_LOCAL_DATE` | 推荐直接使用 |
| 友好时长输出 | `DateUtils.formatDuration(millis)` | 唯一推荐场景 |

### HTTP 相关

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 请求头获取 | `ServletRequestUtils` | `getHeader(request, name)` | Servlet 栈专用 |
| 请求参数解析 | `ServletRequestUtils` | `getParam`, `getIntParam` | - |
| AJAX/JSON 判断 | `ServletRequestUtils` | `isAjaxRequest`, `isJsonRequest` | - |
| 可信代理判断 | `ClientIpResolver` | `isTrustedProxy` | 优先使用 |

### 并发工具

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 线程池创建 | `ExecutorUtils` | `newThreadPool(name, size)` | 统一监控命名 |

### 认证上下文

| 需求场景 | 推荐类 | 方法 | 备注 |
|---------|--------|------|------|
| 获取当前用户信息 | `RequestHolder` | `get()`, `getUserId()` | - |
| AuthInfo 读取 | `AuthInfoUtils` | `currentAuthInfo()` | - |

---

## 迁移指引（2.1.0 版本）

### 废弃类迁移

| 废弃类/方法 | 目标替代 | 迁移优先级 |
|------------|----------|-----------|
| `StringUtils.maskSensitive/maskMobile/maskIdCard/maskEmail` | `SensitiveUtils.mask(value, type)` | P0 |
| `IpAddrUtils.getIpAddr(request)` | `ClientIpResolver.getClientIp(request)` | P0 |
| `IpAddrUtils.getIpAddrWithTrustedProxies(...)` | `ClientIpResolver.getClientIp(request)` | P0 |
| `DateUtils` 系列方法 | JDK `java.time` 原生 API | P1 |

### 新增类使用引导

| 能力 | 推荐类 | 原来使用 | 说明 |
|------|--------|---------|------|
| CIDR 网段判断 | `CidrUtils.isInRange` | 本地实现 | 网关、安全模块统一使用 |
| IP 格式校验 | `IpValidator` | 手动正则 | 支持 IPv4/IPv6 |
| 数据脱敏 | `SensitiveUtils` | StringUtils.maskXxx | 更丰富的类型支持 |

---

## 已移除类（2.1.0）

以下类在 2.1.0 版本中移除：

| 类 | 移除原因 |
|----|---------|
| `CookieUtils` | 零引用，业务方多直接使用 Spring 的 ResponseCookie |
| `UrlUtils` | 零引用，多数场景可使用 JDK `URI` 或 Spring `UriComponentsBuilder` |
| `ClassLoaderUtils` | 零引用，可使用 Spring `ClassUtils` 替代 |
| `RandomUtils` | 零引用，可使用 JDK `ThreadLocalRandom` 或 `SecureRandom` |

---

## 工具类开发规范

### 新增工具类检查清单

1. **是否已有类似能力？** - 先搜索 remi-common-* 各模块
2. **是否零第三方依赖？** - 优先 JDK 原生实现
3. **是否线程安全？** - 确保无状态或使用 ThreadLocal
4. **是否提供 JavaDoc？** - 包含 `@since` 和迁移指引
5. **是否废弃了旧实现？** - 如替代已有能力，旧类须加 `@Deprecated`

### 方法命名约定

- 布尔返回：`isXxx`, `hasXxx`, `canXxx`
- 获取单值：`getXxx`（返回 null 表示不存在）
- 获取多值：`listXxx`, `findXxx`
- 转换：`toXxx`（如 `toCamelCase`）
- 判断：`validXxx`, `checkXxx`

---

## 版本日志

### 2.1.0（当前）

- 统一脱敏入口至 `SensitiveUtils`
- 统一 HTTP IP 解析至 `ClientIpResolver`
- 网关 CIDR 匹配复用 `CidrUtils`
- 移除零引用死代码（CookieUtils、UrlUtils、ClassLoaderUtils、RandomUtils）
- 新增 `IpAddrUtils` 废弃标记和迁移指引

### 2.0.0

- `DateUtils` 标记废弃，推荐使用 JDK 原生 API

### 1.4.0

- IP 能力拆分为 `IpValidator`、`CidrUtils`、`NetworkInterfaceUtils`

---

## 联系方式

如需新增工具类或发现能力缺失，请联系 remi-common 模块负责人或在项目 Issue 中反馈。
