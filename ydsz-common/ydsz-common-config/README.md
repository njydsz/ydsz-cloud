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
| **版本** | 1.2.0 |

## 核心能力

### 1. 配置热更新桥接

| 类 | 说明 |
|---|---|
| `ConfigChangeBridge` | 核心桥接器，监听 Spring Cloud `RefreshEvent` 与 `EnvironmentChangeEvent`，通过 `ConfigProperties` 识别实际变更的配置 diff，逐项分发给 `ConfigChangeListener` 监听器列表 |
| `ConfigProperties` | 桥接器配置属性（`ydsz.config.*`） |
| `ConfigAutoConfiguration` | 自动配置入口，注册 Bridge + 监听器 |

### 2. 配置变更事件

| 类 | 说明 |
|---|---|
| `ConfigChangeEvent` | 封装配置变更事件（key / oldValue / newValue / changeType（ADDED / CHANGED / DELETED）） |
| `ConfigChangeListener` **SPI** | 配置变更监听器接口，业务模块实现以响应配置热更新 |

### 3. Jasypt 加密增强

| 类 | 说明 |
|---|---|
| `ConfigCliTool` | Jasypt 配置加解密 CLI 入口（`java -jar ... encrypt / decrypt`） |
| `ConfigEncryptHealthIndicator` | Jasypt 加密健康检查（验证加密器初始化成功） |

### 4. 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/configEncrypt` | Jasypt 加密器健康检查 | `spring-boot-health` + Jasypt 在 classpath |

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.config.bridge.enabled` | true | 是否启用 ConfigChangeBridge |
| `ydsz.config.bridge.ignore-patterns` | - | 忽略变更的正则模式列表（如密码相关 key） |
| `ydsz.config.bridge.ignore-prefixes` | `spring.` | 忽略变更的前缀列表 |
| `ydsz.config.bridge.async-dispatch` | true | 是否异步分发变更事件（不阻塞主线程） |
| `ydsz.config.binder-bridge.enabled` | true | 是否启用 BinderBridge 模式开关（逐项分发） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>comnidsz</groupId>
    <artifactId>ydsz-common-config</artifactId>
</dependency>
```

### 2. 业务模块实现监听器

```java
import com.njydsz.common.config.hotreload.ConfigChangeListener;
import com.njydsz.common.config.hotreload.ConfigChangeEvent;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagChangeListener implements ConfigChangeListener {

    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        if (event.getKey().startsWith("ydsz.feature-flags.")) {
            // 清理本地缓存或刷新特性开关
        }
    }

    @Override
    public String getName() {
        return "featureFlagChangeListener";
    }
}
```

### 3. 健康检查配置

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

暴露端点：`/actuator/health/configEncrypt`（OUTPUT/INPUT ENCRYPTOR 初始化状态）。

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `ConfigChangeListener` **SPI** | 配置变更回调接口，监听单个配置 key 的 ADDED / CHANGED / DELETED 事件 | `@Component` 或手动通过 `ConfigChangeBridge.registerListener()` 注册 |

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `ConfigAutoConfiguration` | Spring Cloud 在 classpath |

## 注意事项

1. **无 Spring Cloud 不注册**：未引入 spring-cloud-context 时 `ConfigAutoConfiguration` 不生效，避免本地开发空指针。
2. **监听器异常隔离**：各监听器独立捕获异常，单个监听器失败不影响其他监听器执行。
3. **Jasypt 增强**：底层加解密由 `jasypt-spring-boot-starter` 承担；本模块仅提供 CLI 工具与健康检查。
4. **异步分发默认开启**：主刷新流程不因监听器慢调用阻塞，关键配置需实时响应可配置 `ydsz.config.bridge.async-dispatch=false`。

## 变更记录

- **1.2.0**（2026-09-04）：新增 BinderBridge 模式（`ConfigProperty` 感知分发），新增 CLI 加解密工具（`ConfigCliTool`）；健康检查输出增强器名称和初始化状态。
- **1.1.0**（2026-08-20）：基于 Spring Cloud RefreshEvent 的首版桥接实现。
- **1.0.0**（2026-08-02）：初始版本。
