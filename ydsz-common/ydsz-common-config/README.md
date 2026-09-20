# ydsz-common-config

> 配置变更桥接层（L5 业务服务层）— Jasypt 加密增强 + RefreshEvent 监听 + 监听器 SPI

桥接 Spring Cloud 配置刷新事件（`RefreshEvent` / `EnvironmentChangeEvent`），diff 变更后通知业务监听器；封装 Jasypt 配置加密健康检查。无 Spring Cloud 上下文时热重载 Bean 不注册，本地开发不受影响。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供配置热更新桥接、变更 diff、监听器 SPI、Jasypt 加密健康检查 |
| **依赖** | ydsz-common-json、spring-context、spring-boot、spring-boot-health；可选 spring-cloud-context、jasypt-spring-boot-starter、lombok、slf4j |
| **版本** | 1.3.0 |

## 核心能力

### 1. 配置热更新桥接

| 类 | 说明 |
|---|---|
| `ConfigChangeBridge` | 核心桥接器，监听 Spring Cloud `RefreshEvent` 与 `EnvironmentChangeEvent`，通过增量快照计算属性变更 diff，逐项分发给 `ConfigChangeListener` 监听器列表（支持异步分发） |
| `ConfigProperties` | 桥接器配置属性（`ydsz.config.*`） |
| `ConfigAutoConfiguration` | 自动配置入口，注册 Bridge + 监听器 |

### 2. 配置变更事件

| 类 | 说明 |
|---|---|
| `ConfigChangeEvent` | 封装配置变更事件（key / oldValue / newValue / `changeType`（`ADDED` / `CHANGED` / `DELETED`）） |
| `ConfigChangeListener` **SPI** | 配置变更监听器接口，业务模块实现以响应配置热更新 |

### 3. JSON 配置工具

| 类 | 说明 |
|---|---|
| `ConfigMergeUtils` | JSON Merge Patch（RFC 7396）工具，支持对象深度合并 |
| `ConfigPatchUtils` | JSON Patch（RFC 6902）工具，支持数组操作与精细路径修改 |

### 4. Jasypt 加密增强

| 类 | 说明 |
|---|---|
| `ConfigCliTool` | Jasypt 配置加解密 CLI 入口（`java -jar ... encrypt / decrypt`） |
| `ConfigEncryptHealthIndicator` | Jasypt 加密健康检查（验证加密器初始化成功 + 解密采样验证） |

### 5. 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/configEncrypt` | Jasypt 加密器健康检查 | `spring-boot-health` + Jasypt 在 classpath |

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.config.change-monitor.enabled` | `true` | 是否启用配置变更桥接 |
| `ydsz.config.change-monitor.snapshot-old-values` | `true` | 是否在变更通知前计算旧值（关闭后 oldValue 为 null） |
| `ydsz.config.change-monitor.async-dispatch` | `true` | 是否异步分发监听器回调（不阻塞 Spring Cloud 刷新线程） |
| `ydsz.config.change-monitor.async-core-pool-size` | `2` | 异步分发线程池核心线程数 |
| `ydsz.config.change-monitor.async-queue-capacity` | `256` | 异步分发线程池任务队列容量（满载时由调用线程执行） |
| `ydsz.config.change-monitor.ignore-prefixes` | `["spring."]` | 忽略变更的前缀列表 |
| `ydsz.config.change-monitor.ignore-patterns` | `[]` | 忽略变更的正则模式列表 |
| `ydsz.config.cli.enabled` | `true` | 是否启用 CLI 工具 Bean |
| `ydsz.config.health.enabled` | `true` | 是否启用配置加密健康检查 |
| `ydsz.config.health.cache-ttl-ms` | `5000` | 健康检查缓存 TTL（毫秒） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-config</artifactId>
</dependency>
```

### 2. 业务模块实现监听器

```java
import com.njydsz.common.config.hotreload.ConfigChangeListener;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagChangeListener implements ConfigChangeListener {

    @Override
    public void onChange(String key, String oldValue, String newValue) {
        if (key.startsWith("ydsz.feature-flags.")) {
            // 清理本地缓存或刷新特性开关
        }
    }
}
```

### 3. 通过 Spring 事件监听变更

```java
import com.njydsz.common.config.hotreload.ConfigChangeEvent;
import com.njydsz.common.config.hotreload.ConfigChangeEvent.ChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigChangeEventHandler {

    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        for (ConfigChangeEvent.ConfigChange change : event.getChanges()) {
            if (change.changeType() == ChangeType.ADDED) {
                // 处理新增属性
            } else if (change.changeType() == ChangeType.DELETED) {
                // 处理删除属性
            } else {
                // 处理修改属性
            }
        }
    }
}
```

### 4. 健康检查配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

暴露端点：`/actuator/health/configEncrypt`（Jasypt 加密器初始化状态 + 解密采样结果）。

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `ConfigChangeListener` **SPI** | 配置变更回调接口，监听单个配置 key 的 ADDED / CHANGED / DELETED 事件 | `@Component` 自动注册，或手动通过 `ConfigChangeBridge.addListener()` 注册 |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `ConfigAutoConfiguration` | 始终生效；Bridge 仅在 Spring Cloud `EnvironmentChangeEvent` 存在时激活 |
| `ConfigCliTool` | `ydsz.config.cli.enabled=true` 时激活（需 Jasypt 在 classpath） |
| `ConfigEncryptHealthIndicator` | `ydsz.config.health.enabled=true` 时激活（需 spring-boot-health 在 classpath） |

## 工作原理

```
┌─────────────┐     RefreshEvent      ┌──────────────────┐
│   Nacos /   │ ──────────────────────▶│                  │
│   Apollo    │                        │ ConfigChangeBridge│
│   (推送)    │     EnvironmentChange  │ (增量快照 + diff) │
│             │ ──────────────────────▶│                  │
└─────────────┘                        └────────┬─────────┘
                                                │
                            ┌───────────────────┼───────────────────┐
                            │                   │                   │
                            ▼                   ▼                   ▼
                    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
                    │ ConfigChange │   │   系统模块   │   │   其他模块   │
                    │    Event     │   │   监听器     │   │   监听器     │
                    │ (@EventLis.) │   │ (异步/同步)  │   │ (异步/同步)  │
                    └──────────────┘   └──────────────┘   └──────────────┘
```

### 增量快照机制

1. **启动时**：一次性全量扫描 Environment 中所有可枚举属性值作为稳定视图基线（O(n)，仅执行一次）
2. **每次刷新**：仅遍历 `EnvironmentChangeEvent.getKeys()` 中的变更键，与稳定视图对比计算 oldValue / newValue（O(k)，k = changedKeys）
3. **刷新完成**：增量更新稳定视图（仅写入被变更的键）

时间复杂度从全量扫描 O(n) 降为 O(k)，配置项 > 500 时优势显著。

## 注意事项

1. **无 Spring Cloud 不注册 Bridge**：未引入 spring-cloud-context 时 `ConfigChangeBridge` Bean 不生效，本地开发不受影响。
2. **监听器异常隔离**：各监听器独立捕获异常，单个监听器失败不影响其他监听器执行。
3. **异步分发默认开启**：主刷新流程不因监听器慢调用阻塞，关键配置需实时响应可配置 `ydsz.config.change-monitor.async-dispatch=false`。
4. **Jasypt 增强**：底层加解密由 `jasypt-spring-boot-starter` 承担；本模块仅提供 CLI 工具与健康检查。
5. **健康检查缓存**：健康检查默认缓存 5 秒，避免高频请求触发全量属性扫描。

## 变更记录

- **1.3.0**（2026-09-20）：
  - 新增 `changeType` 枚举（ADDED / CHANGED / DELETED），明确区分属性新增、修改与删除
  - 新增 `async-dispatch` 异步分发机制（线程池 + CallerRunsPolicy）
  - 优化快照机制：启动时一次性全量采集 + 增量更新，避免重复全量扫描
  - 新增 JSON Patch（RFC 6902）工具 `ConfigPatchUtils`（规划中）
- **1.2.0**（2026-09-04）：新增 CLI 加解密工具（`ConfigCliTool`）；健康检查输出增强器名称和初始化状态。
- **1.1.0**（2026-08-20）：基于 Spring Cloud RefreshEvent 的首版桥接实现。
- **1.0.0**（2026-08-02）：初始版本。
