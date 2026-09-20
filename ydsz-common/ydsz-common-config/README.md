# ydsz-common-config

> 配置变更桥接层（L5 业务服务层）— Jasypt 加密增强 + RefreshEvent 监听 + 监听器 SPI

桥接 Spring Cloud 配置刷新事件（`RefreshEvent` / `EnvironmentChangeEvent`），diff 变更后通知业务监听器；封装 Jasypt 配置加密健康检查。无 Spring Cloud 上下文时热重载 Bean 不注册，本地开发不受影响。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供配置热更新桥接、变更 diff、监听器 SPI、Jasypt 加密健康检查 |
| **依赖** | ydsz-common-json、ydsz-common-thread(optional)、spring-context、spring-boot、spring-boot-health；可选 spring-cloud-context、jasypt-spring-boot-starter、lombok、slf4j、jakarta.annotation |
| **版本** | 1.3.1 |

## 核心能力

### 1. 配置热更新桥接

| 类 | 说明 |
|---|---|
| `ConfigChangeBridge` | 核心桥接器，监听 Spring Cloud `RefreshEvent` 与 `EnvironmentChangeEvent`，通过增量快照计算属性变更 diff，逐项分发给 `ConfigChangeListener` 监听器列表（支持异步分发）。应用关闭时通过 `@PreDestroy` 优雅关闭异步线程池。 |
| `ConfigProperties` | 桥接器配置属性（`ydsz.config.*`） |
| `ConfigAutoConfiguration` | 自动配置入口，注册 Bridge + 健康检查 |

### 2. 配置变更事件

| 类 | 说明 |
|---|---|
| `ConfigChangeEvent` | 封装配置变更事件（key / oldValue / newValue / `changeType`（`ADDED` / `CHANGED` / `DELETED`） / `sourceNamespace` / `tenant`） |
| `ConfigChangeListener` **SPI** | 配置变更监听器接口，支持 `getOrder()` 优先级排序（升序执行） |
| `ConfigAuditPublisher` **SPI** | 配置审计发布器接口，默认实现为 `LogbackAuditPublisher`（打日志），业务可替换为 MQ 实现 |

### 3. JSON 配置工具

| 类 | 说明 |
|---|---|
| `ConfigMergeUtils` | JSON Merge Patch（RFC 7396）工具，支持对象深度合并 |

### 4. Jasypt 加密增强

| 类 | 说明 |
|---|---|
| `ConfigCliTool` | Jasypt 配置加解密 CLI 入口（`java -jar ... encrypt / decrypt / re-encrypt`），支持从 `ConfigProperties.Cli` 读取参数 |
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
| `ydsz.config.change-monitor.async-dispatch` | `true` | 是否异步分发监听器回调（不阻塞 Spring Cloud 刷新主线程） |
| `ydsz.config.change-monitor.async-core-pool-size` | `2` | 异步分发线程池核心线程数 |
| `ydsz.config.change-monitor.async-queue-capacity` | `256` | 异步分发线程池任务队列容量（满载时由调用线程执行） |
| `ydsz.config.change-monitor.audit-enabled` | `true` | 是否启用配置变更审计发布 |
| `ydsz.config.cli.enabled` | `true` | CLI 工具参数 Bean 是否注册（独立 main 运行不受此开关影响） |
| `ydsz.config.cli.algorithm` | `PBEWithHMACSHA512AndAES_256` | 加密算法 |
| `ydsz.config.cli.key-obtention-iterations` | `1000` | 密钥派生迭代次数 |
| `ydsz.config.cli.pool-size` | `4` | 加密器池大小 |
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

### 5. 自定义审计发布器

```java
@Component
public class KafkaConfigAuditPublisher implements ConfigAuditPublisher {
    @Override
    public void publish(ConfigChangeEvent event, String nodeId, int changeCount) {
        // 发送到 MQ / 数据库等审计存储
    }
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `ConfigChangeListener` **SPI** | 配置变更回调接口，支持 `getOrder()` 返回优先级（升序执行） | `@Component` 自动注册，或手动 `addListener()` |
| `ConfigAuditPublisher` **SPI** | 配置变更审计发布器 | `@Component` 替换默认实现 |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `ConfigAutoConfiguration` | 始终生效；Bridge 仅在 Spring Cloud `EnvironmentChangeEvent` 存在时激活 |
| `ConfigEncryptHealthIndicator` | `ydsz.config.health.enabled=true` 时激活（需 spring-boot-health 在 classpath） |

## 规范对齐

本模块遵循《云顶编码规范》以下规则：

| 规则 | 实现方式 |
|---|---|
| YDIZ-CONC-001 | 异步线程池通过 `ydsz-common-thread` 的 `ExecutorUtils.builder()` 创建 |
| YDIZ-OOP-006 | ConfigProperties 配置类字段带 `is` 前缀（OOP-006-EXEMPT） |
| YDIZ-ENG-002 | 所有默认值通过 `@ConfigurationProperties` 外部化（无硬编码 TTL/容量） |
| YDIZ-TEST-001 | 提供完整 JUnit 5 单元测试覆盖 |
| §24.2 | 统一配置变更监听 SPI，禁止业务自行监听 `EnvironmentChangeEvent` / `RefreshEvent` |
| §24.3 | 多层配置合并统一使用 `ConfigMergeUtils` |

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
                   │ (@EventLis.) │   │ (按Order排序)│   │              │
                   └──────────────┘   └──────────────┘   └──────────────┘
                                               │
                                               ▼
                                     ┌──────────────────┐
                                     │ ConfigAudit      │
                                     │ Publisher (SPI)  │
                                     └──────────────────┘
```

### 增量快照机制

1. **启动时**：一次性全量扫描 Environment 中所有可枚举属性值作为稳定视图基线（O(n)，仅执行一次）
2. **每次刷新**：仅遍历 `EnvironmentChangeEvent.getKeys()` 中的变更键，与稳定视图对比计算 oldValue / newValue（O(k)，k = changedKeys）
3. **刷新完成**：增量更新稳定视图（仅写入被变更的键）

时间复杂度从全量扫描 O(n) 降为 O(k)，配置项 > 500 时优势显著。

### 优雅停机

应用关闭时，`@PreDestroy shutdownAsyncExecutor()` 执行以下流程：

1. `shutdown()` 拒绝新任务
2. 等待最多 5 秒让已提交任务完成
3. 超时后 `shutdownNow()` 强制中断

## 注意事项

1. **无 Spring Cloud 不注册 Bridge**：未引入 spring-cloud-context 时 `ConfigChangeBridge` Bean 不生效，本地开发不受影响。
2. **监听器异常隔离**：各监听器独立捕获异常，单个监听器失败不影响其他监听器执行。
3. **监听器执行顺序**：通过 `getOrder()` 升序执行（小值先执行），默认 0。
4. **异步分发默认开启**：主刷新流程不因监听器慢调用阻塞，关键配置需实时响应可配置 `ydsz.config.change-monitor.async-dispatch=false`。
5. **Jasypt 增强**：底层加解密由 `jasypt-spring-boot-starter` 承担；本模块仅提供 CLI 工具与健康检查。
6. **健康检查缓存**：健康检查默认缓存 5 秒，避免高频请求触发全量属性扫描。
7. **配置变更审计**：默认通过 `LogbackAuditPublisher` 打日志，生产环境建议替换为 MQ 实现。
8. **ConfigMergeUtils 合并失效降级**：合并解析异常时降级返回 override，不抛异常。

## 变更记录

- **1.3.1**（2026-09-20）：
  - **P0 修复**：补齐单元测试覆盖（ConfigChangeBridge / ConfigMergeUtils / ConfigChangeEvent / ConfigEncryptHealthIndicator / ConfigProperties）
  - **P0 修复**：线程池接入 `ydsz-common-thread`（YDIZ-CONC-001 合规），通过 `ExecutorUtils.builder()` 创建
  - **P1 修复**：ConfigProperties 布尔字段补全 YDIZ-OOP-006-EXEMPT Javadoc 标注
  - **P1 修复**：ConfigCliTool 新增 `createEncryptor(String, ConfigProperties.Cli)` 重载，统一运行时参数来源
  - **P1 修复**：移除 README 中未实现功能（ConfigPatchUtils / ignore-prefixes / ignore-patterns）描述
  - **P2 优化**：`ConfigChangeBridge` 实现 `@PreDestroy` 优雅关闭异步线程池
  - **P2 优化**：`ConfigChangeListener` 新增 `getOrder()` 优先级排序支持
  - **P2 优化**：新增 `ConfigAuditPublisher` SPI 接口，支持配置变更审计发布器自定义
  - **P2 优化**：`ConfigChangeEvent` 新增 `sourceNamespace` / `tenant` 多租户扩展字段
  - **P2 优化**：新增 `additional-spring-configuration-metadata.json` 缺失的配置项描述
  - **依赖变更**：新增 `ydsz-common-thread`(optional)、`jakarta.annotation-api`(jakarta.annotation) 用于 `@PreDestroy` 和 `ExecutorUtils`
  - 更新 ydsz-system `SystemConfigChangeListener` 消除 `contains()` 字符串匹配歧义
- **1.3.0**（2026-09-20）：
  - 新增 `changeType` 枚举（ADDED / CHANGED / DELETED），明确区分属性新增、修改与删除
  - 新增 `async-dispatch` 异步分发机制（线程池 + CallerRunsPolicy）
  - 优化快照机制：启动时一次性全量采集 + 增量更新，避免重复全量扫描
- **1.2.0**（2026-09-04）：新增 CLI 加解密工具（`ConfigCliTool`）
- **1.1.0**（2026-08-20）：基于 Spring Cloud RefreshEvent 的首版桥接实现
- **1.0.0**（2026-08-02）：初始版本
