# ydsz-common-config

> 配置增强公共模块（L5 业务服务层）

作为 Jasypt 的增强层，提供配置变更监听桥接（Nacos / Spring Cloud Config → `ConfigChangeListener`）、加密健康检查、CLI 加密工具、JSON 配置合并工具等能力。本模块**不自行实现配置加解密**，`ENC()` 格式的透明解密由 `jasypt-spring-boot-starter` 全局处理。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 Jasypt 加密增强（健康检查 / CLI 工具）+ 配置变更监听桥接 + JSON 配置合并工具 |
| **依赖** | common-core、common-json（透传）；可选依赖 jasypt-spring-boot-starter、spring-cloud-context、spring-boot-health |
| **版本** | 26.09.01 |

## 核心能力

### 1. 配置变更监听桥接

| 类 | 说明 |
|---|---|
| `ConfigChangeBridge` | 配置变更桥接器，实现 `ApplicationListener<ApplicationEvent>`，监听 Spring Cloud 的 `RefreshEvent`（刷新前快照）与 `EnvironmentChangeEvent`（刷新后 diff），自动计算属性变更并分发 |
| `ConfigChangeEvent` | 配置变更 Spring 事件，携带 `List<ConfigChange>` 变更记录，业务模块可通过 `@EventListener` 监听 |
| `ConfigChangeEvent.ConfigChange` | 单个属性变更记录（record 类型，含 `key`/`oldValue`/`newValue`） |
| `ConfigChangeListener` | 配置变更监听器接口（`@FunctionalInterface`），定义 `onChange(key, oldValue, newValue)` |
| `ConfigProperties.ChangeMonitor` | 变更监听配置属性 |

工作原理：

```
Nacos / Apollo 配置推送
    │
    ▼
RefreshEvent ──► ConfigChangeBridge 快照当前 Environment 所有可枚举属性值
    │
    ▼
EnvironmentChangeEvent ──► ConfigChangeBridge 反射调用 getKeys() 提取变更键集合
    │
    ├── 与快照对比计算 oldValue / newValue（跳过未实际变更的属性）
    ├── 发布 ConfigChangeEvent（Spring 事件）
    ├── 记录审计日志（含节点 IP、变更数量）
    └── 通知所有 ConfigChangeListener（逐个回调 onChange，单监听器异常不影响其他）
```

条件激活：仅在 classpath 存在 `org.springframework.cloud.context.environment.EnvironmentChangeEvent`（即引入 `spring-cloud-context`）时生效，由 `@ConditionalOnClass` 控制。

### 2. 加密健康检查

| 类 | 说明 |
|---|---|
| `ConfigEncryptHealthIndicator` | 配置加密健康指标，实现 Spring Boot 4.x 的 `HealthIndicator`，检查 Jasypt 主密码配置与 `ENC()` 加密属性状态 |
| `ConfigProperties.Health` | 健康检查配置属性 |

检查项：

- 主密码来源识别：`ENV_VARIABLE`（`JASYPT_ENCRYPTOR_PASSWORD` 环境变量）/ `CONFIG_PROPERTY`（`jasypt.encryptor.password` 属性）/ `NOT_CONFIGURED`
- 扫描所有 `EnumerablePropertySource`，统计 `ENC()` 格式属性数量
- 属性名展示：最多展示 20 个加密属性名，超出时显示省略提示

条件激活：仅在 classpath 存在 `org.springframework.boot.health.contributor.HealthIndicator`（即引入 `spring-boot-health`）时生效。

### 3. CLI 加密工具

| 类 | 说明 |
|---|---|
| `ConfigCliTool` | Jasypt 配置加密 CLI 工具，提供 `main(String[] args)` 入口，支持 `encrypt` / `decrypt` 命令 |

默认参数（与 Jasypt 全局配置对齐）：

- 算法：`PBEWithHMACSHA512AndAES_256`（需 JCE unlimited strength，JDK 8u161+ 内置）
- 密钥派生迭代次数：1000
- 加密器池大小：4
- Provider：`SunJCE`
- Salt 生成器：`org.jasypt.salt.RandomSaltGenerator`
- IV 生成器：`org.jasypt.iv.RandomIvGenerator`
- 输出类型：`base64`

主密码获取顺序：命令行第 3 参数 → `JASYPT_ENCRYPTOR_PASSWORD` 环境变量。

### 4. JSON 配置合并工具

| 类 | 说明 |
|---|---|
| `ConfigMergeUtils` | 配置合并工具类（基于 `common-json` 的 `JsonMergePatch`，遵循 RFC 7396） |

合并规则（RFC 7396）：

- patch 中的字段覆盖 target 中的同名字段
- patch 中值为 `null` 的字段从 target 中删除
- patch 中不存在的字段保留 target 中的原值
- 嵌套对象递归合并（非整体替换）

支持场景：

- 基础配置 + 租户覆盖配置 → 最终生效配置
- 默认配置 + 环境覆盖配置 → 运行时配置
- Nacos 远程配置 + 本地 override → 合并配置

合并失败时降级返回 override 配置，不抛异常。

### 5. 自动配置

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `ConfigAutoConfiguration` | 始终激活（通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册） | `ConfigProperties` |
| ↑ | `spring-cloud-context` 在 classpath + `ydsz.config.change-monitor.enabled=true`（默认 true） | `ConfigChangeBridge` |
| ↑ | `spring-boot-health` 在 classpath + `ydsz.config.health.enabled=true`（默认 true） | `ConfigEncryptHealthIndicator` |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-config</artifactId>
</dependency>
```

> 模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动注册 `ConfigAutoConfiguration`，无需额外 `@EnableXxx` 注解。各增强能力按 classpath 与配置开关条件激活。

### 2. 引入可选依赖

根据需要的功能引入对应依赖：

```xml
<!-- 配置变更监听桥接（Nacos / Spring Cloud Config 场景） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-context</artifactId>
</dependency>

<!-- 加密健康检查（Spring Boot 4.x 健康检查模块） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-health</artifactId>
</dependency>

<!-- Jasypt 加解密（CLI 工具与健康检查依赖） -->
<dependency>
    <groupId>com.github.ulisesbocchio</groupId>
    <artifactId>jasypt-spring-boot-starter</artifactId>
</dependency>
```

### 3. 配置 Jasypt 主密码

```bash
# 推荐通过环境变量注入（生产环境）
export JASYPT_ENCRYPTOR_PASSWORD="master-password"
```

```yaml
# 或通过配置属性（开发环境，不推荐生产使用）
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}
    algorithm: PBEWithHMACSHA512AndAES_256
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.config.change-monitor.enabled` | true | 是否启用配置变更监听桥接 |
| `ydsz.config.change-monitor.snapshot-old-values` | true | 是否在变更通知前快照旧值（false 时 `oldValue` 为 null，减少内存开销） |
| `ydsz.config.cli.enabled` | true | 保留字段（CLI 工具通过 main 方法独立运行） |
| `ydsz.config.cli.algorithm` | `PBEWithHMACSHA512AndAES_256` | 加密算法（与 Jasypt 对齐） |
| `ydsz.config.cli.key-obtention-iterations` | 1000 | 密钥派生迭代次数 |
| `ydsz.config.cli.pool-size` | 4 | 加密器池大小 |
| `ydsz.config.health.enabled` | true | 是否启用加密健康检查 |
| `ydsz.config.health.cache-ttl-ms` | 5000 | 健康检查缓存 TTL（毫秒），设为 0 禁用缓存 |

> 加密相关配置（`jasypt.encryptor.password` / `jasypt.encryptor.algorithm`）使用 Jasypt 原生属性，不在 `ydsz.config` 前缀下。

## 使用示例

### 1. 实现 ConfigChangeListener（自动注册）

```java
import com.njydsz.common.config.hotreload.ConfigChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MyConfigListener implements ConfigChangeListener {

    @Override
    public void onChange(String key, String oldValue, String newValue) {
        log.info("配置变更: {} | {} -> {}", key, oldValue, newValue);
        if ("my.feature.enabled".equals(key)) {
            // 响应配置变更，刷新本地缓存等
        }
    }
}
```

### 2. 通过 @EventListener 监听 ConfigChangeEvent（替代批量处理）

```java
import com.njydsz.common.config.hotreload.ConfigChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigEventLogger {

    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        // 批量处理所有变更
        log.info("批量配置变更，共 {} 项", event.getChanges().size());
        event.getChanges().forEach(c ->
            log.info("  {} | {} -> {}", c.key(), c.oldValue(), c.newValue()));
    }
}
```

### 3. 手动注册监听器

```java
import com.njydsz.common.config.hotreload.ConfigChangeBridge;
import com.njydsz.common.config.hotreload.ConfigChangeListener;
import jakarta.annotation.Resource;

@Service
public class DynamicListenerService {

    @Resource
    private ConfigChangeBridge configChangeBridge;

    public void registerListener() {
        configChangeBridge.addListener((key, oldValue, newValue) -> {
            // 动态注册的监听器
        });
    }
}
```

### 4. CLI 加密 / 解密

```bash
# 加密（主密码作为第 3 参数）
java -cp ydsz-common-config.jar \
  com.njydsz.common.config.cli.ConfigCliTool \
  encrypt "my-db-password" "master-password"

# 输出: ENC(G8NkR6qVw2J3FpY0bXxC7A==)

# 解密（支持 ENC() 包装或纯密文）
java -cp ydsz-common-config.jar \
  com.njydsz.common.config.cli.ConfigCliTool \
  decrypt "ENC(G8NkR6qVw2J3FpY0bXxC7A==)" "master-password"

# 输出: my-db-password

# 使用环境变量提供主密码（推荐，避免密码出现在命令行历史）
export JASYPT_ENCRYPTOR_PASSWORD="master-password"
java -cp ydsz-common-config.jar \
  com.njydsz.common.config.cli.ConfigCliTool \
  encrypt "my-db-password"
```

### 5. JSON 配置合并

```java
import com.njydsz.common.config.hotreload.ConfigMergeUtils;

public class ConfigMergeExample {

    public void mergeConfigs() {
        String baseConfig = "{\"timeout\":30,\"retry\":3,\"pool\":{\"min\":1,\"max\":10}}";
        String override = "{\"retry\":5,\"pool\":{\"max\":20}}";

        // 单层合并
        String merged = ConfigMergeUtils.merge(baseConfig, override);
        // 结果: {"timeout":30,"retry":5,"pool":{"min":1,"max":20}}

        // 多层合并（按优先级从低到高）
        String defaults = "{\"timeout\":30}";
        String envConfig = "{\"timeout\":60}";
        String tenantConfig = "{\"retry\":5}";
        String finalConfig = ConfigMergeUtils.mergeLayers(defaults, envConfig, tenantConfig);
    }
}
```

### 6. 配置示例

```yaml
ydsz:
  config:
    change-monitor:
      enabled: true                    # 启用配置变更监听桥接
      snapshot-old-values: true        # 快照旧值（false 则 oldValue 为 null）
    cli:
      enabled: true                    # 启用 CLI 工具 Bean
      algorithm: PBEWithHMACSHA512AndAES_256
      key-obtention-iterations: 1000
      pool-size: 4
    health:
      enabled: true                    # 启用加密健康检查

# Jasypt 原生配置（非 ydsz.config 前缀）
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}
    algorithm: PBEWithHMACSHA512AndAES_256
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `ConfigChangeListener` | 配置变更监听器接口，业务模块实现后注册为 Spring Bean 即可自动接收配置变更通知；也可通过 `ConfigChangeBridge.addListener()` 动态注册 | 业务模块按需实现 |

### 自定义监听器示例

```java
import com.njydsz.common.config.hotreload.ConfigChangeListener;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagListener implements ConfigChangeListener {

    @Override
    public void onChange(String key, String oldValue, String newValue) {
        if (key.startsWith("feature.")) {
            // 特性开关变更，刷新本地特性标志缓存
            FeatureFlagManager.refresh(key, newValue);
        }
    }
}
```

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/config` | 配置加密健康检查 | `spring-boot-health` 在 classpath + `ydsz.config.health.enabled=true`（默认 true） |

`ConfigEncryptHealthIndicator` 暴露以下信息：

| 详情字段 | 说明 |
|---|---|
| `encryptorPasswordSource` | 主密码来源（`ENV_VARIABLE` / `CONFIG_PROPERTY` / `NOT_CONFIGURED`） |
| `encryptedPropertyCount` | `ENC()` 格式加密属性总数 |
| `encryptedProperties` | 脱敏后的加密属性名集合（最多展示 20 个，超出显示 `... (N more)`） |
| `error` | 失败原因（仅 DOWN 状态） |

健康状态判定：

| 状态 | 条件 |
|---|---|
| **UP** | 无加密属性（`encryptedPropertyCount=0`），或主密码已配置且存在加密属性 |
| **DOWN** | 存在 `ENC()` 加密属性但主密码未配置（`encryptorPasswordSource=NOT_CONFIGURED`） |

示例响应：

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

## 注意事项

1. **不重复实现加解密**：本模块作为 Jasypt 增强层，`ENC()` 格式的透明解密由 `jasypt-spring-boot-starter`（在 `common-web` 引入）全局处理，本模块不自行实现加密逻辑。
2. **Spring Cloud Context 依赖**：`ConfigChangeBridge` 仅在 classpath 存在 `EnvironmentChangeEvent` 时激活。未引入 `spring-cloud-context` 时配置变更桥接功能不生效，但不会影响其他增强能力。
3. **主密码安全**：生产环境必须通过 `JASYPT_ENCRYPTOR_PASSWORD` 环境变量注入主密码，避免硬编码在配置文件或命令行参数中（命令行参数会出现在进程列表与 shell 历史中）。
4. **快照内存开销**：`ydsz.config.change-monitor.snapshot-old-values=true`（默认）时，`ConfigChangeBridge` 会在 `RefreshEvent` 触发时快照当前 Environment 中所有可枚举属性值，属性数量较多时存在内存开销。若不需要 `oldValue`，可设为 `false` 减少 GC 压力。
5. **反射调用 getKeys()**：`ConfigChangeBridge` 通过反射调用 `EnvironmentChangeEvent#getKeys()` 提取变更键集合，避免编译期硬依赖 Spring Cloud。Spring Cloud 版本升级时需验证此方法签名兼容性。
6. **监听器异常隔离**：`ConfigChangeBridge` 通知监听器时使用 try-catch 包裹单个监听器回调，单个监听器异常不会影响其他监听器与事件发布，但会记录 WARN 日志。
7. **属性名脱敏**：健康检查暴露的加密属性名会自动脱敏为 `***.lastSegment` 形式（如 `spring.datasource.password` → `***.password`），避免泄露完整属性路径。
8. **CLI 工具独立运行**：`ConfigCliTool` 提供 `main(String[] args)` 入口，可独立打包运行，无需启动 Spring 容器；输出格式为 `ENC(密文)` 便于直接粘贴到 Nacos 配置中。
9. **JSON 合并降级**：`ConfigMergeUtils.merge()` 在合并失败（如 JSON 格式错误）时降级返回 override 配置，不抛异常，避免阻断配置加载流程。
10. **JCE 算法要求**：默认算法 `PBEWithHMACSHA512AndAES_256` 需 JCE unlimited strength（JDK 8u161+ 已内置）。降级方案：`PBEWithMD5AndDES`（弱但不需 JCE）。

## 变更记录

- **26.09.01**（2026-08-02）：补全接入方式、配置项表、使用示例、SPI 扩展点、健康检查、注意事项章节；完善配置变更桥接工作原理、加密健康检查暴露字段、CLI 工具默认参数、JSON 合并规则等核心能力描述
