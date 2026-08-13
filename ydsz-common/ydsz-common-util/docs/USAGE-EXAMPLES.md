# ydsz-common-util 使用示例

> 本文档提供各类工具的典型使用代码示例。

---

## 1. ID 生成

### 1.1 快速生成（静态门面）

```java
import com.njydsz.common.util.id.IdGenerator;

long id = IdGenerator.nextId();
```

### 1.2 Spring 注入

```java
@Autowired
private SnowflakeIdGenerator idGenerator;

public long generateId() {
    return idGenerator.nextId();
}
```

### 1.3 ID 解析

```java
long id = idGenerator.nextId();

// 反解时间戳
long timestamp = SnowflakeIdGenerator.parseTimestamp(id);
// 反解 workerId
long workerId = SnowflakeIdGenerator.parseWorkerId(id);
// 反解序列号
long sequence = SnowflakeIdGenerator.parseSequence(id);
```

## 2. 加密

### 2.1 AES-GCM 加密

```java
import com.njydsz.common.util.security.crypto.CryptoUtils;

// 生成密钥
String hexKey = CryptoUtils.generateKeyHex("AES");

// 加密
String ciphertext = CryptoUtils.encryptHex("敏感数据", hexKey);

// 解密
String plaintext = CryptoUtils.decryptHex(ciphertext, hexKey);
```

### 2.2 国密 SM4

```java
String sm4Key = CryptoUtils.generateKeyHex("SM4");
String encrypted = CryptoUtils.encryptHex("敏感数据", sm4Key);
String decrypted = CryptoUtils.decryptHex(encrypted, sm4Key);
```

## 3. 密码安全

### 3.1 BCrypt 哈希

```java
// 哈希
String hashed = PwdUtils.hashPasswordBCrypt("userPassword123");

// 验证
boolean valid = PwdUtils.verifyPasswordBCrypt("userPassword123", hashed);
```

### 3.2 PBKDF2 哈希

```java
// 自动生成盐值
String encoded = PwdUtils.encodePBKDF2WithAutoSalt("MyPassword".toCharArray());

// 验证
boolean valid = PwdUtils.verifyPBKDF2("MyPassword", encoded);
```

### 3.3 密码强度检查

```java
PwdUtils.PasswordStrength strength = PwdUtils.checkPasswordStrength("abc123");
// 返回: WEAK / MEDIUM / STRONG

PwdUtils.PasswordStrengthLevel level = PwdUtils.checkPasswordStrengthLevel("abc123");
// 返回: VERY_WEAK / WEAK / MEDIUM / STRONG / VERY_STRONG
```

## 4. HTTP 工具

### 4.1 请求参数

```java
// 获取请求 IP
String ip = ServletRequestUtils.getClientIp(request);

// 获取请求参数
String name = ServletRequestUtils.getStringParam(request, "name");
```

### 4.2 响应渲染

```java
return HttpResponseUtils.success(data);
return HttpResponseUtils.error("操作失败");
```

## 5. 线程池

### 5.1 创建线程池

```java
// CPU 密集型
ExecutorService pool = ExecutorUtils.newCpuBoundThreadPool();

// IO 密集型（虚拟线程）
ExecutorService ioPool = ExecutorUtils.newIoBoundThreadPool();
```

### 5.2 优雅关闭

```java
ExecutorUtils.shutdownGracefully(pool, 30, TimeUnit.SECONDS);
```

### 5.3 带监控的线程池

```java
MeteredThreadPoolExecutor pool = MeteredThreadPoolExecutor.builder()
    .corePoolSize(4)
    .maxPoolSize(8)
    .queueCapacity(100)
    .meterRegistry(meterRegistry)
    .build();
```

## 6. Map 工具

### 6.1 安全取值

```java
Map<String, Object> map = ...;
String name = MapUtils.getString(map, "name");
Integer age = MapUtils.getInteger(map, "age");
Long id = MapUtils.getLong(map, "id");
```

### 6.2 Map → Bean

```java
// 自动检测 Record / POJO
UserDO user = MapUtils.toBeanOrRecord(map, UserDO.class);
```

### 6.3 泛型转换

```java
List<User> users = MapUtils.toBean(rawList, new MapUtils.TypeReference<List<User>>() {});
```

## 7. 重试工具

### 7.1 固定延迟重试

```java
String result = RetryUtils.executeWithRetry(() -> callRemoteApi(), 3, Duration.ofSeconds(1));
```

### 7.2 指数退避重试

```java
RetryUtils.RetryConfig config = RetryUtils.RetryConfig.builder()
    .maxAttempts(5)
    .initialDelay(Duration.ofMillis(100))
    .maxDelay(Duration.ofSeconds(10))
    .build();
String result = RetryUtils.executeWithExponentialBackoff(() -> callRemoteApi(), config);
```

## 8. 限流器

```java
// 创建限流器：每秒 100 个令牌，允许突发 200 个
RateLimiter limiter = RateLimiter.create(100, 200);

// 阻塞式获取
limiter.acquire();

// 带超时的非阻塞获取
boolean acquired = limiter.tryAcquire(100, TimeUnit.MILLISECONDS);
```

## 9. IP 工具

```java
// IPv4 校验
boolean valid = IpValidator.validIpv4("192.168.1.1");

// 内网判断
boolean internal = IpValidator.isInternalIp("10.0.0.1");

// CIDR 判断
boolean inRange = CidrUtils.isInRange("192.168.1.5", "192.168.1.0/24");
```

## 10. 字符串工具

```java
// 判空
boolean empty = StringUtils.isEmpty(str);

// 驼峰 → 下划线
String underscore = StringUtils.camelToUnderscore("userName"); // user_name

// 下划线 → 驼峰
String camel = StringUtils.underscoreToCamel("user_name"); // userName

// 掩码
String masked = StringUtils.mask("13812345678", 3, 4); // 138****5678
```

---

> **文档更新时间**：2026-08-13
