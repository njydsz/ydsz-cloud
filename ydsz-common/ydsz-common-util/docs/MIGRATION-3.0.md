# ydsz-common-util 3.0 迁移指南

> 本文档说明从 2.x 升级到 3.0 时的 API 变更与迁移步骤。

---

## 1. 加密 API 迁移

### 1.1 AesUtils → CryptoUtils

| 旧 API（已废弃） | 新 API |
|----------------|--------|
| `AesUtils.encrypt(text, hexKey)` | `CryptoUtils.encryptHex(text, hexKey)` |
| `AesUtils.decrypt(ct, hexKey)` | `CryptoUtils.decryptHex(ct, hexKey)` |
| `AesUtils.initHexKey()` | `CryptoUtils.generateKeyHex("AES")` |
| `AesUtils.initHexKey(256)` | `CryptoUtils.generateKeyHex("AES", 256)` |

**密文兼容**：新 API 密文格式与旧版一致（IV(12B) || ciphertext+tag，Base64），直接切换无需数据迁移。

### 1.2 AesGcmCrypto → CryptoUtils

| 旧 API（已废弃） | 新 API |
|----------------|--------|
| `new AesGcmCrypto(key).encrypt(text)` | `CryptoUtils.encrypt(text, key)` |
| `new AesGcmCrypto(key).decrypt(ct)` | `CryptoUtils.decrypt(ct, key)` |

## 2. MapUtils.toBean 迁移

### 2.1 POJO 转换（setter 注入）

旧代码使用 `toBean(Map, Class)` 转换 POJO，新代码推荐使用 `toBeanOrRecord`：

```java
// 旧
UserDO user = MapUtils.toBean(map, UserDO.class);

// 新（自动检测 Record / POJO）
UserDO user = MapUtils.toBeanOrRecord(map, UserDO.class);
```

### 2.2 Record 转换

Record 没有无参构造器和 setter，必须使用 `toBeanOrRecord`：

```java
public record Point(double x, double y) {}

Map<String, Object> data = Map.of("x", 1.0, "y", 2.0);
Point point = MapUtils.toBeanOrRecord(data, Point.class);
```

### 2.3 泛型集合转换

```java
// 旧（不支持泛型）
List<User> users = (List<User>) map.getList("users"); // 需要强转

// 新（类型安全）
List<User> users = MapUtils.toBean(rawList, new MapUtils.TypeReference<List<User>>() {});
```

## 3. 新增能力

### 3.1 RetryUtils（重试工具）

```java
// 固定延迟重试
String result = RetryUtils.executeWithRetry(() -> callApi(), 3, Duration.ofSeconds(1));

// 指数退避重试
RetryUtils.RetryConfig config = RetryUtils.RetryConfig.builder()
    .maxAttempts(5)
    .initialDelay(Duration.ofMillis(100))
    .maxDelay(Duration.ofSeconds(10))
    .build();
String result = RetryUtils.executeWithExponentialBackoff(() -> callApi(), config);
```

### 3.2 RateLimiter（限流器）

```java
RateLimiter limiter = RateLimiter.create(100, 200); // 100 QPS，突发 200
limiter.acquire(); // 阻塞获取
boolean ok = limiter.tryAcquire(100, TimeUnit.MILLISECONDS); // 超时获取
```

### 3.3 BoundedVirtualThreadScheduler

```java
BoundedVirtualThreadScheduler scheduler = new BoundedVirtualThreadScheduler(100);
scheduler.submit(() -> task.execute());
```

## 4. 废弃清单

| 类 / 方法 | 废弃版本 | 移除版本 | 替代 |
|-----------|---------|---------|------|
| `AesUtils` | 3.0.0 | 4.0.0 | `CryptoUtils` |
| `AesGcmCrypto` | 3.0.0 | 4.0.0 | `CryptoUtils.encrypt/decrypt` |
| `MapUtils.toBean(Map, Class)` | 4.0.0 | 5.0.0 | `MapUtils.toBeanOrRecord` |
| `MapUtils.toBean(Map, Class, DateTimeFormatter)` | 4.0.0 | 5.0.0 | `MapUtils.toBeanOrRecord` |

---

> **文档更新时间**：2026-08-13
