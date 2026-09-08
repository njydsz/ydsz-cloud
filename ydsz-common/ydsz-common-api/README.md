# ydsz-common-api

> YDSZ Feign 客户端契约包（L6 应用层）

仅含 FeignClient 接口定义、Assembler 转换器与 Fallback 回调，**不包含任何实现类**。根据 DDD 规范（YDIZ-DDD-005），本模块**禁止自建 `dto` / `vo` / `query` 子包**，跨服务数据结构通过显式依赖 `domain` 模块消费。上下游服务通过本模块的 Feign 接口契约进行解耦，实现编译期契约校验与运行时服务调用分离。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L6 应用层 |
| **类型** | 公共契约包（不独立部署，仅作为 JAR 被消费方引入） |
| **作用** | 定义 FeignClient 远程调用接口、请求/响应 Assembler、Fallback 降级逻辑 |
| **依赖** | 显式依赖 `ydsz-common-domain`（api → domain 契约引用） |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. API 版本注解

| 类 | 说明 |
|---|---|
| `@ApiVersion` | API 版本注解，标记 Controller 或方法所属的语义化版本（`value`），支持 `deprecated`（废弃标记）与 `sunset`（预计下线日期）属性；网关按 Header 重写 path 路由到对应实例 |

### 2. Feign 客户端契约

本模块为聚合模块，实际 FeignClient 接口由子业务 api 包（如 `ydsz-system-api`、`ydsz-userinfo-api`）各自定义。本模块仅提供公共的契约基础设施。

### 3. Assembler 与 Fallback

| 约定 | 说明 |
|---|---|
| `XxxAssembler` | 接口 DTO 与领域对象之间的转换器，提供 `toDTO()` / `toDomain()` 双向映射 |
| `XxxFeignClientFallback` | Feign 调用降级实现，服务不可用时返回兜底数据，避免级联故障 |

## DDD 规范约束

根据 **YDIZ-DDD-005** 规范，本模块严格遵守以下约束：

- **禁止自建 `dto` / `vo` / `query` 子包**：跨服务数据结构通过引入对应 `domain` 模块获取
- **api → domain 单向引用**：api 模块仅可依赖 domain 模块，不可反向依赖
- **无实现类**：本模块仅放置接口、注解、转换器和 fallback，不实现 FeignClient 逻辑

## 接入方式

### 1. 作为契约包被消费方引入

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-api</artifactId>
    <version>${ydsz.version}</version>
</dependency>
```

### 2. 声明 Feign 客户端接口

```java
import com.njydsz.common.api.annotation.ApiVersion;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "ydsz-system", fallback = SystemUserFeignFallback.class)
public interface SystemUserFeignClient {

    @ApiVersion("v1")
    @GetMapping("/api/users/{id}")
    UserDTO getUserById(@PathVariable("id") Long id);
}
```

### 3. 定义 Fallback 降级

```java
import org.springframework.stereotype.Component;

@Component
public class SystemUserFeignFallback implements SystemUserFeignClient {

    @Override
    public UserDTO getUserById(Long id) {
        // 返回兜底数据或抛出业务异常
        return UserDTO.empty();
    }
}
```

## 配置项

本模块无独立配置项。Feign 客户端相关配置由引入方的 `application.yml` 统一管理：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `spring.cloud.openfeign.client.config.default.connect-timeout` | `5000` | Feign 连接超时（毫秒） |
| `spring.cloud.openfeign.client.config.default.read-timeout` | `10000` | Feign 读超时（毫秒） |
| `feign.circuitbreaker.enabled` | `false` | 是否启用断路器（需配合 Resilience4j 等） |

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `feign.RequestInterceptor` | 自定义 Feign 请求拦截器（如注入 Token、TraceId） | 业务模块提供 `@Bean` |
| `feign.codec.ErrorDecoder` | 自定义 Feign 错误解码器 | 业务模块提供 `@Bean` |
| `@ApiVersion` | 语义化版本注解 | 框架内置，业务模块扩展 |
| `XxxAssembler` | 接口 DTO 与领域对象转换 | 业务模块在各自 api 模块中实现 |

## 健康检查

本模块无独立健康检查指标。Feign 客户端的健康状态由消费方的 Spring Boot Actuator 或断路器（Resilience4j / Sentinel）监控。

## 自动配置类

本模块无 `@AutoConfiguration` 类。装配由引入方通过 `@EnableFeignClients` 或 Spring Cloud OpenFeign 自动配置完成。

## 注意事项

1. **无实现原则**：本模块为纯契约包，严禁在 `src/main/java` 下放置任何 `@Service`、`@Component` 等 Spring Bean 实现。
2. **DDD 规范**：严格遵守 YDIZ-DDD-005 规范，不新建 `dto` / `vo` / `query` 子包；数据引用通过 `pom.xml` 显式声明 `domain` 依赖。
3. **版本兼容**：FeignClient 接口变更需保持向后兼容，新增参数使用 `@RequestParam(required = false)` 或新增重载方法，避免破坏已有消费方。
4. **Fallback 设计**：Fallback 实现应返回空数据或兜底值，不应抛出异常；异常场景由消费方业务逻辑处理。
5. **作用域隔离**：FeignClient 接口的 `@ApiVersion` 需与提供服务端的 `@ApiVersion` 注解保持一致，确保网关路由正确匹配。
6. **编译期校验**：消费方引入本模块后，编译期即可校验 FeignClient 接口签名与服务端一致性，建议开启 `spring.cloud.openfeign.lazy-attributes-resolution=true`。

## 变更记录

- **26.09.01**（2026-08）：初始版本，建立 Feign 客户端契约包骨架，包含 `@ApiVersion` 注解，遵循 YDIZ-DDD-005 规范约束。
