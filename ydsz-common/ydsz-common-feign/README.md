# ydsz-common-feign
企业级OpenFeign统一增强模块，仅保留核心公共能力，简化接入复杂度，降低维护成本。

---
## 一、快速接入
### 1. 引入依赖
在业务模块pom.xml中添加依赖：
```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-feign</artifactId>
</dependency>
```

### 2. 启用Feign客户端
在启动类上添加Spring Cloud原生注解，指定Feign客户端扫描包路径：
```java
@SpringBootApplication
@EnableFeignClients(basePackages = {"com.njydsz.xxx.feign"})
public class XxxApplication {
    public static void main(String[] args) {
        SpringApplication.run(XxxApplication.class, args);
    }
}
```

### 3. 自定义配置（可选）
在application.yml中添加配置，不配置则使用默认值：
```yaml
ydsz:
  feign:
    enabled: true # 总开关，默认true
    logger-level: BASIC # 日志级别，默认BASIC，可选NONE/HEADERS/FULL
    propagation:
      enabled: true # 核心请求头透传开关，默认true
    retry:
      enabled: true # 重试开关，默认true
      max-attempts: 3 # 最大重试次数，默认3
    timeout:
      connect: 5000 # 连接超时（毫秒），默认5000
      read: 10000 # 读取超时（毫秒），默认10000
    trace:
      enabled: true # W3C链路追踪头透传开关，默认true
    metrics:
      enabled: true # 监控指标采集开关，默认true
    circuit-breaker:
      enabled: false # 熔断器开关，默认false，开启后需自行引入Resilience4j依赖
```

---
## 二、核心能力说明
### 1. 核心请求头透传
默认自动透传4个核心请求头，保证链路可追溯、租户上下文透传：
- `traceparent`：W3C标准链路追踪头，自动从当前上下文获取
- `X-Tenant-Id`：租户上下文标识，从当前请求头获取
- `X-Access-Token`：用户访问令牌，从当前请求头获取
- `X-Request-Id`：请求唯一标识，不存在时自动生成

### 2. 请求重试
默认开启重试，仅对GET请求生效，最大重试3次，避免重试风暴。

### 3. 链路追踪
默认开启W3C traceparent协议头透传，兼容SkyWalking、Zipkin等主流链路追踪系统。

### 4. 监控指标
默认开启Feign调用指标采集，可对接Prometheus、Grafana等监控系统。

### 5. 熔断能力
默认关闭，开启后需自行引入Resilience4j依赖，熔断规则使用Resilience4j原生配置即可。

---
## 三、常见问题
### Q1：如何自定义透传的请求头？
在配置中修改`ydsz.feign.propagation.headers`即可，例如：
```yaml
ydsz:
  feign:
    propagation:
      headers:
        - traceparent
        - X-Tenant-Id
        - X-Access-Token
        - X-Request-Id
        - X-Custom-Header # 自定义头
```

### Q2：如何关闭某个Feign客户端的重试？
在对应FeignClient的configuration中自定义Retryer即可，例如：
```java
@FeignClient(name = "xxx", configuration = NoRetryConfig.class)
public interface XxxClient {
    // 接口方法
}

public class NoRetryConfig {
    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
```

### Q3：如何开启熔断能力？
1. 在配置中开启熔断开关：`ydsz.feign.circuit-breaker.enabled=true`
2. 在业务模块中引入Resilience4j依赖：
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```
3. 在application.yml中添加Resilience4j原生配置即可。

### Q4：如何关闭整个Feign增强模块？
在配置中添加：`ydsz.feign.enabled=false`即可，关闭后所有增强能力失效，Feign客户端使用Spring Cloud原生能力。