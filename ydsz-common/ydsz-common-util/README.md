# ydsz-common-util

> YDSZ 通用工具类库（L2 工具层）— 覆盖 ID 生成、加密/国密、HTTP、字符串、集合/Map、IP、并发、认证上下文、YAML、国际化消息等领域。

---

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-util</artifactId>
</dependency>
```

### 2. 基础使用

```java
// 生成分布式 ID
long id = IdGenerator.nextId();

// AES-GCM 加密
String hexKey = CryptoUtils.generateKeyHex("AES");
String ciphertext = CryptoUtils.encryptHex("敏感数据", hexKey);
String plaintext = CryptoUtils.decryptHex(ciphertext, hexKey);

// 密码哈希（BCrypt）
String hashed = PwdUtils.hashPasswordBCrypt("userPassword123");
boolean valid = PwdUtils.verifyPasswordBCrypt("userPassword123", hashed);

// IP 校验
boolean ok = IpValidator.validIpv4("192.168.1.1");
boolean internal = IpValidator.isInternalIp("10.0.0.1");

// 线程池创建
ExecutorService pool = ExecutorUtils.newCpuBoundThreadPool();
ExecutorUtils.shutdownGracefully(pool, 30, TimeUnit.SECONDS);
```

### 3. 启用 Snowflake 自动配置

```yaml
ydsz:
  util:
    snowflake:
      enabled: true                 # 默认启用
      worker-id: 1                  # 可选：显式指定（0-1023，最高优先级）
      datacenter-id: 0              # 可选：数据中心 ID（0-31）
```

---

## 能力地图

### 按场景查找工具

| 业务场景 | 推荐入口类 | 一句话说明 |
|---------|-----------|-----------|
| 生成分布式唯一 ID | `IdGenerator` / `SnowflakeIdGenerator` | 雪花算法，支持静态门面或 Spring 注入 |
| AES 加解密 | `CryptoUtils` | 统一入口，支持 AES/SM4 算法路由 |
| 国密算法（SM2/SM3/SM4） | `Sm2Utils` / `Sm3Utils` / `Sm4Utils` | 依赖 bcprov-jdk18on，GM/T 标准 |
| 摘要/签名/HMAC | `DigestUtils` | SHA-256/SHA-512/HMAC-SHA256/PBKDF2 |
| 密码哈希与强度校验 | `PwdUtils` | BCrypt + PBKDF2 + 密码强度评分 |
| 请求解析与响应渲染 | `ServletRequestUtils` / `HttpResponseUtils` | Servlet 请求/响应工具 |
| Token 提取 | `HttpTokenUtils` | Bearer Token 提取与前缀剥离 |
| 线程池创建 | `ExecutorUtils` | 支持 Fixed/Virtual/Scheduled/TTL 等 |
| 线程池监控 | `MeteredThreadPoolExecutor` | Micrometer 指标自动注册 |
| 字符串判空/转换 | `StringUtils` | null-safe 判空、驼峰/下划线互转 |
| 集合判空/转换 | `CollectionUtils` / `MapUtils` | null-safe 判空、类型安全取值 |
| Bean PATCH 更新 | `BeanUpdateUtil` | 仅复制非 null 属性 |
| IP 校验/CIDR | `IpValidator` / `CidrUtils` | IPv4/IPv6 校验、内网判断、CIDR |
| 认证信息读取 | `AuthInfoUtils` | 用户 ID、租户 ID、数据权限维度 |
| YAML ↔ JSON | `YamlUtils` | 基于 snakeyaml |
| 国际化消息 | `MessageUtils` | ResourceBundle 便捷读取 |
| Spring 上下文 | `SpringContextHolder` | 静态 getBean |

### 按包结构查找工具

```
com.njydsz.common.util
├── auth/          认证信息：AuthInfoUtils
├── bean/          Bean 更新：BeanUpdateUtil（PATCH 语义）
├── collection/    集合工具：CollectionUtils、MapUtils、SequencedCollections
├── concurrent/    并发工具：ExecutorUtils、MeteredThreadPoolExecutor、StructuredConcurrencyScopes
├── config/        自动配置：UtilAutoConfiguration
├── http/          HTTP 工具：ServletRequestUtils、HttpResponseUtils、HttpTokenUtils
├── id/            ID 生成：SnowflakeIdGenerator、IdGenerator、WorkerIdAllocator*
├── ip/            IP 工具：IpValidator、CidrUtils、NetworkInterfaceUtils
├── message/       国际化：MessageUtils
├── password/      密码工具：PwdUtils、PasswordStrengthChecker（SPI）
├── security/      加密工具：CryptoUtils、DigestUtils、AesUtils（已废弃）、国密工具
├── spring/        Spring 集成：SpringContextHolder
├── string/        字符串：StringUtils
└── yaml/          YAML：YamlUtils
```

---

## 依赖说明

### 零强制三方依赖原则

| 依赖类型 | 依赖 | 说明 |
|---------|------|------|
| **核心依赖**（强制） | JDK、ydsz-common-core、ydsz-common-domain、ydsz-common-json、snakeyaml、slf4j、commons-io | 编译和运行时必需 |
| **可选依赖**（按需） | spring-security-crypto | BCrypt 密码哈希（未引入时 PwdUtils 使用 PBKDF2 降级） |
| | bcprov-jdk18on | 国密算法（SM2/SM3/SM4） |
| | spring-web | Servlet 请求/响应工具 |
| | transmittable-thread-local | TTL 上下文透传线程池 |
| | spring-boot-health | Snowflake 健康检查 |

> **可选依赖处理**：未引入时，调用对应方法会抛出包含引入指引的 `IllegalStateException`（而非 `NoClassDefFoundError`）。

---

## SPI 扩展点

| SPI 接口 | 用途 | 默认实现 |
|---------|------|---------|
| `WorkerIdAllocator` | 分布式 WorkerId 分配策略（0-1023） | PodOrdinal → IpHash → FilePersisted 链 |
| `PasswordStrengthChecker` | 密码强度评分规则 | `DefaultPasswordStrengthChecker`（长度/多样性/连续重复扣分） |

> **自定义 SPI 实现**：在 `META-INF/services/` 接口全限定名文件中填写实现类全限定名。

---

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `ydsz.util.snowflake.enabled` | `true` | 是否启用 Snowflake 自动配置 |
| `ydsz.util.snowflake.worker-id` | - | WorkerId（0-1023，显式配置最高优先级） |
| `ydsz.util.snowflake.datacenter-id` | - | 数据中心 ID（0-31，未配置时基于主机名哈希） |
| `ydsz.util.snowflake.node-id` | - | 节点标识（用于 PodOrdinal/FilePersisted 策略） |

**WorkerId 解析优先级**：显式配置 → PodOrdinal → IpHash → FilePersisted

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [能力全景图](docs/CAPABILITY-MAP.md) | 完整的工具类清单与场景映射 |
| [使用示例](docs/USAGE-EXAMPLES.md) | 各类工具的使用代码示例 |
| [迁移指南](docs/MIGRATION-3.0.md) | 3.0 版本 API 变更与迁移说明 |
| [架构规范](docs/ARCHITECTURE.md) | 模块分层与设计规范 |

---

## 安全注意事项

1. **AES 安全**：默认使用 AES-256-GCM（认证加密 AEAD），每次加密生成随机 12 字节 IV；密钥禁止硬编码
2. **国密合规**：SM2/SM3/SM4 需 `bcprov-jdk18on`；合规场景推荐使用国密替代国际标准算法
3. **密码存储**：BCrypt 推荐强度 12（OWASP 最低 10）；PBKDF2 迭代次数默认 600,000（OWASP 2023 推荐）
4. **时序攻击防护**：所有密码/签名验证使用 `constantTimeEquals`（`MessageDigest.isEqual`）
5. **Snowflake 时钟回拨**：≤ 5ms 循环等待恢复；> 5ms 抛出 `ClockBackwardException`

---

## 版本与变更

| 版本 | 日期 | 说明 |
|------|------|------|
| 3.0.0 | 2026-08 | CryptoUtils 统一加密入口、WorkerIdAllocator SPI 重构 |
| 2.2.0 | 2026-07 | 新增 TTL 线程池、密码强度 SPI |
| 2.1.0 | 2026-06 | 文档治理、稳定性修复 |
| 1.0.0 | 2026-01 | 初始发布 |

---

> **文档与代码一致性**：本文档严格对齐模块内真实源码（39 个 Java 文件 / 约 37 个公共类）。早期文档中描述的未实现类已全部移除。
