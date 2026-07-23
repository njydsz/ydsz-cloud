# ydsz-common-config

YDSZ 公共配置模块 — Jasypt 加密增强层与动态配置变更监听。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **职责** | Jasypt 加密增强（健康检查 / CLI 工具）+ 配置变更监听桥接 |

## 设计说明

本模块**不自行实现配置加密**，而是作为 Jasypt 的增强层：

- **加解密**：由 `jasypt-spring-boot-starter`（在 `common-web` 引入）全局处理 `ENC()` 格式的透明解密
- **本模块增强**：
  - 配置变更监听桥接（Nacos / Spring Cloud Config → `ConfigChangeListener`）
  - 加密健康检查（`ConfigEncryptHealthIndicator`）
  - CLI 加密工具（`ConfigCliTool`）

## 核心能力

### 1. 配置变更监听桥接

监听 Spring Cloud 的 `RefreshEvent`（刷新前）和 `EnvironmentChangeEvent`（刷新后），自动 diff 属性变更并通知 `ConfigChangeListener`。

#### 使用方式

```java
@Component
public class MyConfigListener implements ConfigChangeListener {
    @Override
    public void onChange(String key, String oldValue, String newValue) {
        log.info("配置变更: {} | {} -> {}", key, oldValue, newValue);
    }
}
```

#### 事件监听

也可通过 `@EventListener` 监听 `ConfigChangeEvent`：

```java
@EventListener
public void onConfigChange(ConfigChangeEvent event) {
    List<ConfigChange> changes = event.getChanges();
    // 批量处理变更
}
```

#### 工作时序

```
Nacos 配置推送
    │
    ▼
RefreshEvent ──► ConfigChangeBridge 快照旧值
    │
    ▼
EnvironmentChangeEvent ──► ConfigChangeBridge diff 计算
    │
    ├──► 发布 ConfigChangeEvent
    └──► 通知所有 ConfigChangeListener
```

### 2. 加密健康检查

`ConfigEncryptHealthIndicator` 暴露 Jasypt 加密状态到 Actuator `/health` 端点：

```json
{
  "status": "UP",
  "details": {
    "encryptorPasswordSource": "ENV_VARIABLE",
    "encryptedPropertyCount": 3,
    "encryptedProperties": ["***.password", "***.password", "***.secret"]
  }
}
```

- **UP**：主密码已配置，或无加密属性
- **DOWN**：存在 `ENC()` 加密属性但主密码未配置

### 3. CLI 加密工具

```bash
# 加密（主密码作为第 3 参数）
java -cp ydsz-common-config.jar \
  com.njydsz.common.config.cli.ConfigCliTool \
  encrypt "my-db-password" "master-password"

# 输出: ENC(G8NkR6qVw2J3FpY0bXxC7A==)

# 解密
java -cp ydsz-common-config.jar \
  com.njydsz.common.config.cli.ConfigCliTool \
  decrypt "G8NkR6qVw2J3FpY0bXxC7A==" "master-password"

# 使用环境变量提供主密码（推荐）
export JASYPT_ENCRYPTOR_PASSWORD="master-password"
java -cp ydsz-common-config.jar \
  com.njydsz.common.config.cli.ConfigCliTool \
  encrypt "my-db-password"
```

## 配置项

```yaml
ydsz:
  config:
    change-monitor:
      enabled: true                    # 是否启用配置变更监听桥接（默认 true）
      snapshot-old-values: true        # 是否快照旧值（默认 true，false 则 oldValue 为 null）
    cli:
      enabled: true                   # 是否启用 CLI 工具 Bean（默认 true）
      algorithm: PBEWithHMACSHA512AndAES_256  # 加密算法（与 Jasypt 对齐）
      key-obtention-iterations: 1000  # 密钥派生迭代次数
      pool-size: 4                    # 加密器池大小
    health:
      enabled: true                   # 是否启用加密健康检查（默认 true）
```

## 与 Jasypt 的关系

| 职责 | Jasypt（common-web） | 本模块 |
|---|---|---|
| `ENC()` 透明解密 | ✅ 全局自动处理 | ❌ 不重复 |
| 加密算法 | `PBEWithHMACSHA512AndAES_256` | — |
| 主密码注入 | `JASYPT_ENCRYPTOR_PASSWORD` 环境变量 | — |
| 健康检查 | ❌ 无 | ✅ `ConfigEncryptHealthIndicator` |
| CLI 工具 | 需下载 jasypt CLI jar | ✅ `ConfigCliTool`（模块内直接使用） |
| 配置变更通知 | ❌ 无 | ✅ `ConfigChangeBridge` + `ConfigChangeListener` |

## 自动配置

| 配置类 | 条件 | 注册的 Bean |
|---|---|---|
| `ConfigAutoConfiguration` | 始终激活 | `ConfigProperties` |
| ↑ | Spring Cloud Context 在 classpath | `ConfigChangeBridge` |
| ↑ | HealthIndicator 在 classpath | `ConfigEncryptHealthIndicator` |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-config</artifactId>
</dependency>
```

### Optional 依赖

- `jasypt-spring-boot-starter`：CLI 工具和健康检查需要
- `spring-cloud-context`：配置变更桥接需要
- `spring-boot-health`：健康检查需要
