# 基座分层优化建议：ydsz-common-base 与 ydsz-common-web 重构方案

> 文档状态：建议稿（Draft）
> 适用范围：所有使用 `ydsz-common-base` 或 `ydsz-common-web` 作为启动基座的业务模块
> 作者：ydsz-team
> 最后更新：2026-09-01

---

## 一、现状诊断

### 1.1 模块定位说明

| 模块 | 现有定位（pom description） | 实际层（模块声明层） |
|------|---------------------------|---------------------|
| `ydsz-common-base` | "Web/App 公共 HTTP 基座模块，承载 CORS、时区、安全头、日志拦截、上下文清理、API 文档、文档导出等共享逻辑" | L6 应用层 |
| `ydsz-common-web` | "PC Web 端基座模块" | L6 应用层 |

### 1.2 依赖树对比

#### ydsz-common-base 传递依赖（直接声明）

```
spring-boot-starter-web                [非 optional]
  └─ 引入 servlet/tomcat/WebMVC/WebFlux 运行时
ydsz-common-core                       [核心响应码/工具类]
ydsz-common-util                       [工具函数库]
ydsz-common-safe                       [IP 解析/安全基类]
ydsz-common-exception                  [业务异常体系]
ydsz-common-auth                       [认证模型 BaseAuthInfo]
ydsz-common-locales                    [国际化配置]
ydsz-common-config                     [Jasypt 配置加密热加载]
ydsz-common-json                       [JSON 引擎]
micrometer-core                        [optional]  [指标采集]
spring-boot-actuator                   [optional]  [健康检查]
spring-boot-health                     [非 optional] [健康抽象层]
springdoc-openapi-starter-webmvc-api   [optional]  [OpenAPI 文档]
knife4j-openapi3-spring-boot-starter  [optional]  [Knife4j UI]
```

#### ydsz-common-web 传递依赖（直接声明 = base 全部 + 下列增量）

```
ydsz-common-base                       [完整传递包含]
ydsz-common-domain                     [领域模型]
ydsz-common-core                       [显式再声明]
ydsz-common-util                       [显式再声明]
swagger-annotations-jakarta            [OpenAPI 注解]
ydsz-common-auth                       [显式再声明]
ydsz-common-safe                       [显式再声明]
ydsz-common-exception                  [显式再声明]
spring-boot-starter-security           [optional]
spring-boot-starter-actuator           [非 optional] [base 中为 optional]
spring-boot-health                     [非 optional]  [base 中已引入]
micrometer-core                        [非 optional]  [base 中为 optional]
mybatis-plus-core                      [optional]
jasypt-spring-boot-starter             [optional]
redisson-spring-boot-starter           [optional]
ydsz-common-excel                      [optional]
HikariCP                               [optional]
yauaa                                  [非 optional]  [User-Agent 解析]
jakarta.servlet-api                    [provided]
ydsz-common-redis                      [非 optional]  ← 关键问题
ydsz-common-json                       [显式再声明]
ydsz-common-locales                    [显式再声明]
```

### 1.3 自动配置类清单

#### base 模块的 @AutoConfiguration 类

| 类名 | 文件位置 | 提供的主要 Bean |
|------|---------|----------------|
| `YdszAutoConfiguration` | `config/YdszAutoConfiguration.java` | `RequestBodySizeLimitFilter`, `TraceFilter`, `SecurityHeadersFilter`(fallback), `RequestContextCleanupFilter`, `YdszHealthIndicator`, `CoreHealthIndicator` |
| `OpenApiAutoConfiguration` | `config/OpenApiAutoConfiguration.java` | OpenApi Schema/Group 相关 Bean |
| `Knife4jAutoConfiguration` | `config/Knife4jAutoConfiguration.java` | Knife4j UI 资源注册 |
| `DocAutoConfiguration` | `config/DocAutoConfiguration.java` | `DocExporter` 文档导出（Markdown/默认） |
| `DocSecurityConfiguration` | `config/DocSecurityConfiguration.java` | 文档访问安全拦截器 |
| `BaseI18nConfiguration` | `config/BaseI18nConfiguration.java` | `MessageResolverHolder`, `MessageResolverRegistry`, `SpringMessageResolver` |
| `I18nMetadataAutoConfiguration` | `config/I18nMetadataAutoConfiguration.java` | `I18nMetadataActuatorEndpoint` |
| `BaseTimezoneConfiguration` | `config/BaseTimezoneConfiguration.java` | 时区统一 Jackson 序列化器 |
| `BaseOpenApiConfiguration` | `config/BaseOpenApiConfiguration.java` | `ApiVersionOpenApiCustomizer` |

#### web 模块的 @AutoConfiguration 类

| 类名 | 文件位置 | 提供的主要 Bean |
|------|---------|----------------|
| `WebMvcConfiguration` | `config/WebMvcConfiguration.java` (继承 BaseMvcConfiguration) | `CORS Filter`(override), `RequestLogInterceptor`, `ApiVersionInterceptor`, `GlobalResponseAdvice`, `ContentCachingFilter`, `WebAuthFilter`, `SecurityHeaderFilter`, `TraceIdResponseFilter`, `WebMetrics`, `WebHealthIndicator` |
| `WebCoreAutoConfiguration` | `config/WebCoreAutoConfiguration.java` | `TenantMdcFilter` |
| `WebSessionAutoConfiguration` | `config/WebSessionAutoConfiguration.java` | Redis HttpSession（条件启用） |
| `WebGracefulShutdownAutoConfiguration` | `config/WebGracefulShutdownAutoConfiguration.java` | `ShutdownEventListener`（启动/停机摘要日志） |
| `WebI18nConfiguration` | `config/WebI18nConfiguration.java` | Web 端 i18n 覆盖 |
| `WebTimezoneConfiguration` | `config/WebTimezoneConfiguration.java` | Web 端时区覆盖 |
| `WebSecurityConfiguration` | `config/WebSecurityConfiguration.java` | `WebAuthenticationEntryPoint`, `WebAccessDeniedHandler` |
| `WebMultipartAutoConfiguration` | `config/WebMultipartAutoConfiguration.java` | 文件上传配置属性绑定 |
| `WebOpenApiConfiguration` | `config/WebOpenApiConfiguration.java` | Web 端 OpenAPI 覆盖 |
| `InternalSignatureAutoConfiguration` | `config/InternalSignatureAutoConfiguration.java` | `InternalSignatureFilter`（内部签名验签） |
| `UserAgentConfiguration` | `config/UserAgentConfiguration.java` | `UserAgentAnalyzer`（Yauaa） |

### 1.4 核心发现

1. **`ydsz-common-base` 直接依赖 `spring-boot-starter-web`，定位名不副实**：描述声称 "Web/App 公共基座"，但引入了完整的 Servlet 栈，导致所有引用 base 的非 Web 入口（Job/Scheduler/App）也强制获得 Servlet 容器依赖。
2. **`ydsz-common-web` 完整包含 `ydsz-common-base`**：通过 `<dependency>ydsz-common-base</artifactId>` 传递获得 base 的全部能力，任何引入 `ydsz-common-web` 的模块已经隐含获得 `ydsz-common-base`。
3. **MVC 配置双向覆盖**：`BaseMvcConfiguration` 注册 `CorsFilter`；`WebMvcConfiguration` 继承它并重写以使用 `WebCorsProperties`。两者通过 `FilterRegistrationBean` 同名 Bean（`corsFilter`）实现覆盖，运行时依赖 Spring 的后注册覆盖先注册，脆弱且不可静态分析。
4. **`ydsz-common-web` 强制引入 Redis**：pom 中 `ydsz-common-redis` 声明为非 optional，所有引入 web 的模块即使不启用 Session，也会将 redis 客户端 jar 带入 classpath。
5. **`ydsz-common-web` 强制引入 `yauaa`**：UA 解析库（体积较大）作为非 optional 依赖，所有 web 模块必须接收，即使纯 API 后端不需要。

---

## 二、问题清单

### 问题 1：POM 冗余声明普遍存在（所有 Web 类业务模块）

**现象**：几乎每个 `*-web` / `*-api` 业务子模块的 pom.xml 同时声明：

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-base</artifactId>
</dependency>
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-web</artifactId>
</dependency>
```

**后果**：
- `ydsz-common-base` 被重复声明：`web` 通过 Maven 传递依赖已包含 `base`，显式声明造成语义混淆（无法区分"我依赖 base 的 API"还是"我依赖 base 的传递"）。
- 版本升级时容易遗漏：业务模块可能 lock 了 `base` 的版本但未 lock `web`，反之亦然。
- IDE 和 `mvn dependency:tree` 分析显示节点冗余，干扰依赖冲突排查。

### 问题 2：base 与 web 职责边界模糊——双 MVC 配置类共生于同层

**现象**：
- `BaseMvcConfiguration` 是一个 `@Bean` 注册类（非 auto-configured），通过 `@Bean corsFilter()` 显式声明 CORS 过滤器。
- `WebMvcConfiguration extends BaseMvcConfiguration`，通过 `addInterceptors` 添加 web 端拦截器，同时通过继承再次暴露 `corsFilter()` 方法（使用子类属性）。
- `YdszAutoConfiguration` 注册一组 `FilterRegistrationBean`；`WebMvcConfiguration` 注册另一组；两者通过 order 避免运行时冲突，但静态上职责交叉。

**后果**：
- `ydsz-common-base` 同时出现在 Web 和非 Web 入口，但其内部 `BaseMvcConfiguration` 使用 `FilterRegistrationBean`（Servlet 专属），非 Web 启动路径下产生无用 Bean 注册。
- 自动配置优先级控制困难：`WebMvcConfiguration` 用 `@AutoConfigureBefore(YdszAutoConfiguration.class)` 控制顺序，但这种"反向控制"违反直觉。

### 问题 3：Redis 通过 web 基座强制引入，无需 Redis 的模块被迫携带

**现象**：`ydsz-common-web` pom 中 `ydsz-common-redis` 非 optional：

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-redis</artifactId>
</dependency>
```

**后果**：
- 纯后台 Web 服务（如 actuator-only、内部工具）即使不使用 Session/Cache，也必须持有 `ydsz-common-redis` 及其底层 Redisson/jedis 依赖。
- 增加 jar 包体积、启动时间、潜在的 Redis 连接池初始化超时风险。
- 对于"只需要统一响应格式 + CORS"的轻量模块，属于典型的"肥基座"反模式。

### 问题 4：传递依赖膨胀——web 基座聚合过广

**现象**：`ydsz-common-web` 的增量依赖涵盖：

| 能力 | 是否应为 web 基座自带 |
|------|---------------------|
| `ydsz-common-redis` | 否（应为可选/独立引入） |
| `yauaa`（UA 解析） | 否（体积大，并非所有 Web 都需要） |
| `mybatis-plus-core` | 否（ORM 应由 DAO 层决定） |
| `jasypt-spring-boot-starter` | 否（加密模块独立） |
| `redisson-spring-boot-starter` | 否（分布式锁/缓存独立） |
| `HikariCP` | 否（连接池独立） |
| `ydsz-common-excel` | 否（导出独立） |
| `spring-boot-starter-actuator` | 可选（需区分运维监控基座与 Web 基座） |
| `micrometer-core` | 可选（监控独立） |

**后果**：基座模块的依赖聚合度过高，形成 "God Starter" 反模式，新业务模块引入容易依赖混乱。

### 问题 5：base 非 optional 引入 `spring-boot-health` 造成非 Web 入口负担

**现象**：`ydsz-common-base` 中 `spring-boot-health` 非 optional，且 `YdszAutoConfiguration` 使用 `@ConditionalOnClass` 探测 `HealthIndicator`。

**后果**：基础设置本可以忽略 health 模块，但通过基座依赖被强制引入。虽然体积有限，但与 `spring-boot-starter-web` 组合明显泄漏了 Servlet 层依赖。

### 问题 6：`@AutoConfigureBefore` 反向控制——执行顺序脆弱

**现象**：

```java
@AutoConfigureBefore({YdszAutoConfiguration.class, SafeConfiguration.class})
public class WebMvcConfiguration extends BaseMvcConfiguration { ... }
```

`WebMvcConfiguration` 注册在 `@AutoConfiguration` imports 列表中（因为 web 模块的 `spring/` 文件声明它），同时使用 `@AutoConfigureBefore` 声明先于 `YdszAutoConfiguration` 执行。

**后果**：执行顺序依赖隐式注册约定，一旦 `BaseMvcConfiguration` 中 Bean 定义变更，web 模块的自动装配可能悄无声息地失效或行为异常。

---

## 三、重构目标

### 3.1 目标架构层次

重构后，`ydsz-common` 的 L6 层拆分为三层入口：

```
L6a  ydsz-common-base-core    纯启动基座
     │
     ├─ 不含: spring-web / servlet / redis / actuator / health / cors
     ├─ 包含: exception / auth-model / i18n-core / json / config-core / util
     ├─ 适用: 所有入口 (web / app / job / scheduler / lambda)

L6b  ydsz-common-base-web     HTTP 层基座
     │
     ├─ 依赖: base-core
     ├─ 包含: cors / interceptor / filter / actuator / health / security / openapi / doc
     ├─ 可选: redis-session (opt-in via ydsz-common-session-web)

L6c  ydsz-common-base-app     App 端基座 (已有雏形)
     │
     ├─ 依赖: base-core
     ├─ 包含: AppMVC / AppAuth / AppRequestId
```

### 3.2 模块职责矩阵（重构后）

| 关注点 | base-core | base-web | base-app | 独立模块 |
|--------|-----------|----------|----------|---------|
| 公共响应码(YdszResult) | Y | - | - | core |
| 统一异常模型 | Y | - | - | exception |
| JSON 引擎 | Y | - | - | json |
| 国际化 | Y | - | - | locales |
| 配置热加载加密 | Y | - | - | config |
| 时区配置 | Y | - | - | (common) |
| CORS | - | Y | Y (自选) | - |
| 请求日志拦截器 | - | Y | Y | - |
| 安全头过滤器 | - | Y | - | safe |
| 认证鉴权 | - | Y | Y | auth |
| 上下文清理 | - | Y | Y | - |
| 健康检查 | - | Y | - | actuator |
| OpenAPI/文档 | - | Y | - | springdoc |
| Redis Session | - | - | - | session-web |
| UA 解析 | - | - | - | web-ua |
| 内部签名 | - | Y | Y | - |
| 优雅停机 | - | Y | Y | - |

### 3.3 迁移原则

1. **基座唯一归属**：每个业务模块只能声明*一个* L6 入口基座（`base-web` 或 `base-app`），不得同时声明 `base-core` + `base-web`。
2. **能力下沉为 optional**：凡不是每个 web 模块都需要的（Redis、yauaa、actuator、mybatis），下沉到独立模块由业务侧按需引入。
3. **传递不重复**：`base-web` → `base-core` 传递依赖；业务模块声明 `base-web` 即隐含获得 `base-core`，无需再次声明。

---

## 四、迁移路径

### 阶段 1（低风险）：仅修改 POM 声明

**目标**：清理历史冗余，消除业务模块同时声明 `base` + `web` 的问题。

**步骤**：

1. 扫描所有业务模块 pom，识别同时声明 `ydsz-common-base` 和 `ydsz-common-web` 的模块：
   ```bash
   grep -rl "ydsz-common-web" --include=pom.xml | xargs grep -l "ydsz-common-base"
   ```
2. 在这些模块中**删除** `ydsz-common-base` 的显式声明（保留 `ydsz-common-web`）。
3. 对仅使用 base 中 util/core 能力（不使用 web 自动配置）的模块：
   - 评估是否有必要升级引入 `base-web`；
   - 若无 HTTP 能力需求，降级为直接依赖 `ydsz-common-core` + `ydsz-common-util`。
4. CI/Gate 门禁检查：PR 中新增同时声明两者的 pom 自动拒绝合并。

**收益**：立竿见影，零代码改动。依赖树清晰化，消除版本锁冲突风险。

**回退**：声明级别变更，恢复 pom 即可。

---

### 阶段 2（中等风险）：拆分 web 中的 Redis 和可选能力

**目标**：解除 `ydsz-common-web` 对 Redis 的强制依赖，将大小体积非普适依赖标为 optional 或迁出。

**步骤**：

1. **Redis 依赖迁移**：
   - 从 `ydsz-common-web` pom 移除 `ydsz-common-redis` 直接声明；
   - 创建新模块 `ydsz-common-session-web`，封装 `WebSessionAutoConfiguration` 和 `RedisHttpSessionImportSelector`；
   - 业务模块若需要 Redis Session，显式引入 `ydsz-common-session-web`。

2. **yauaa 迁移**：
   - 从 `ydsz-common-web` pom 移除 `yauaa`；
   - 将 `UserAgentConfiguration` 迁移到 `ydsz-common-web-ua`（可选扩展模块）；
   - `WebHealthIndicator` 中 UA 健康检查改为反射探测后降级。

3. **actuator / microneter 调整为 optional**：
   - `ydsz-common-web` 中将 `spring-boot-starter-actuator` 和 `micrometer-core` 改为 optional；
   - `WebMetrics`、`WebHealthIndicator`、`ShutdownEventListener` 增加更强的 `@ConditionalOnClass` 守卫。

4. **mybatis-plus / jasypt / redisson / HikariCP / excel 全部标为 optional**（当前部分已是 optional，确认并补充完整）。

5. **补充 ArchUnit 测试**：新建架构测试类 `BaseWebDependencyArchTest`，断言：
   - `ydsz-common-web` 不直接依赖 `ydsz-common-redis`；
   - `ydsz-common-web` 不直接依赖 `yauaa`。

**收益**：轻量级 Web 模块（如文件服务、回调入口）不再被迫携带 Redis 客户端和 UA 解析器体积。启动速度提升。

**回退**：需修改 1 个 pom + 1-2 个迁移类的路径。影响范围限于 web 模块。

---

### 阶段 3（长期）：base 分层彻底细化

**目标**：实现 3.1 所示 `base-core` / `base-web` / `base-app` 三入口架构。

**步骤**：

1. **新建 `ydsz-common-base-core`**：
   - 从 `ydsz-common-base` 移出全部非 Web 能力：`BaseTimezoneConfiguration`、`BaseI18nConfiguration`（纯非 Servlet 部分）、i18n actuator、配置加密核心。
   - 移除 `spring-boot-starter-web` 依赖。
   - 添加 `archunit` 测试断言：不存在 `jakarta.servlet` 或 `org.springframework.web` 的类引用。

2. **从 `ydsz-common-base` 中分离 `BaseMvcConfiguration`**：
   - `BaseMvcConfiguration`（持有 CorsFilter 注册）迁移到 `ydsz-common-base-web`；
   - 新版本 `ydsz-common-base` 不再包含任何 `FilterRegistrationBean` / `WebMvcConfigurer`。

3. **`ydsz-common-base-web` 依赖 `ydsz-common-base-core`**：
   - 在 module `ydsz-common-base-web` pom 中对 `ydsz-common-base-core` 声明依赖；
   - 原 `ydsz-common-web` 中的 Servlet 层逻辑保持不变（阶段 2 已完成 optional 化）。

4. **保留兼容性别名（过渡期）**：
   - 短期内在 `ydsz-common-base-web` 中保留对 `ydsz-common-web` classpath 的兼容性（可通过 relocation pom 或 deprecated artifact 实现）；
   - 6 个月后彻底删除旧 `ydsz-common-web` 坐标。

5. **`ydsz-common-app` 同步对齐**：
   - `AppMvcConfiguration` 在 `ydsz-common-app` 中重新基于 `BaseMvcConfiguration`（此时已迁至 base-web）上游独立构建；
   - 或 App 端自建纯 App 入口不继承 Web 基类。

**收益**：彻底解决基座职责混乱；Job/Scheduler 入口可安全使用 `base-core` 而不引入 Servlet 栈；Web 入口只携带 HTTP 层依赖。

**风险**：需跨模块大规模拆包；auto-config imports 需要更新；迁移期需维护两套 module 坐标。

---

## 五、规范条文建议

### 条文 1：基座唯一归属规则（基座模块层规范）

> **[基座-001] 基座模块唯一归属原则**
>
> 业务子模块（`ydsz-web-*`、`ydsz-app-*`、`ydsz-job-*`、`ydsz-service-*` 等）在同一个 `pom.xml` 中只能声明**至多一个** L6 基座模块作为直接依赖。
>
> | 入口类型 | 可选基座 |
> |---------|---------|
> | Web 入口（Spring MVC /RestController） | `ydsz-common-web`（重构后） |
> | App 入口（移动端 API） | `ydsz-common-app` |
> | Job/Scheduler / 非 HTTP 入口 | `ydsz-common-base`（不得声明 web/app） |
>
> 同时声明多个 L6 基座、或同时声明 L6 基座与其下层模块（如 `base-core` + `base-web`），均视为 P1 编译期违规。

### 条文 2：web 模块基座声明规范

> **[基座-002] Web 模块基座声明规范**
>
> 所有提供 HTTP/Servlet 接口的业务模块，必须**仅**通过 `ydsz-common-web` 继承基座能力，不得同时显式声明 `ydsz-common-base`。
>
> 正确：
> ```xml
> <dependency>
>     <groupId>com.njydsz</groupId>
>     <artifactId>ydsz-common-web</artifactId>
> </dependency>
> ```
>
> 错误：
> ```xml
> <dependency>
>     <groupId>com.njydsz</groupId>
>     <artifactId>ydsz-common-base</artifactId>
> </dependency>
> <dependency>
>     <groupId>com.njydsz</groupId>
>     <artifactId>ydsz-common-web</artifactId>
> </dependency>
> ```
>
> 若业务模块需要的能力**不**被 `ydsz-common-web` 覆盖（如 JDBC、Redis、MQ），应直接声明对应基础能力模块（`ydsz-common-redis`、`ydsz-common-jdbc` 等），而非使用更低的基座层。

### 条文 3：基座能力扩展申请流程

> **[基座-003] 基座能力扩展申请流程**
>
> 当业务方认为某能力应在基座层（L6）统一提供时，需经过以下评审流程：
>
> 1. **发起提案**：在 `ydsz-common` 模块 RFC 目录提交 `rfc-base-capability-xxx.md`，说明：
>    - 能力描述与使用场景；
>    - 依赖的第三方库及其体积；
>    - 是否可以标记为 optional；
>    - 不采纳的成本（各模块重复实现代价）。
> 2. **基座评审委员会**（至少 2 名 core committer）评审：
>    - 是否为**所有** web/app 模块普适的能力？→ 若否，不应进基座，应独立模块；
>    - 引入的传递依赖是否均可标为 optional？→ 若不可，拒绝进基座；
>    - 是否会导致基座职责跨越层边界？→ 若是，拒绝进基座。
> 3. **通过条件**：评审通过后方可合入基座；基座合入后需在 `ydsz-common` CHANGELOG 记录条目并 update 能力矩阵（`capability-matrix.md`）。
>
> 违反本条文的基座扩展 PR 自动触发 review 拒绝。

### 条文 4：基座依赖 optional 化原则

> **[基座-004] 基座依赖 optional 化原则**
>
> `ydsz-common-base` 和 `ydsz-common-web` 的所有第三节依赖（third-party dependency），除 `jakarta.servlet-api` 必须使用 `provided` scope 外，凡非**本基座所有使用场景均需要**的库，必须声明为 `<optional>true</optional>`。
>
> 判断标准：
> - `yauaa`：并非所有 web 模块都需要 UA 解析 → optional 或迁出
> - `spring-boot-starter-actuator`：并非所有 web 模块都需要健康检查 → optional
> - `redisson-spring-boot-starter`：分布式锁/布隆过滤器按需引入 → optional
> - `mybatis-plus-core`：ORM 能力与基座无关 → optional
> - `jasypt-spring-boot-starter`：加密按配置决定 → optional

### 条文 5：基座自动配置层控制约束

> **[基座-005] 基座自动配置层控制约束**
>
> 基座模块中的 `@AutoConfiguration` 类必须遵循以下规则：
>
> 1. 使用 `@ConditionalOnWebApplication` 或 `@ConditionalOnNotWebApplication` 明确运行场景；
> 2. 使用 `@ConditionalOnClass` 守卫所有 optional 依赖提供的类型引用；
> 3. **禁止使用** `@AutoConfigureBefore` / `@AutoConfigureAfter` 控制基座内部配置顺序（复杂度过高），改用 `@DependsOn` 或拆分到不同 `@AutoConfiguration` 类，以 `@Order` / Bean 依赖隐式控制；
> 4. 过滤器/拦截器注册必须在 `@ConditionalOnProperty(matchIfMissing=true)` 下默认开启，并提供显式关闭开关；
> 5. 每个 `@AutoConfiguration` 类必须包含 Javadoc，说明生效条件和提供的 Bean。

---

## 六、Pre-PR 自查清单

开发者在提交涉及基座依赖变更的 PR 时，必须逐项核对以下清单：

### 6.1 依赖声明

- [ ] 我的 pom 中没有同时声明 `ydsz-common-base` 和 `ydsz-common-web`（阶段 1 后禁止）
- [ ] 如果我声明 `ydsz-common-web`，没有同时声明它传递依赖中的 L1~L5 模块（core/util/safe/auth/exception/locales/json/config）
- [ ] 如果我需要 JDBC/Redis/MQ 相关能力，我直接声明对应的 `ydsz-common-*` 模块而非依赖基座传递
- [ ] 所有第三方依赖在基座中满足 optional / provided 要求

### 6.2 自动配置

- [ ] 新增的 `@AutoConfiguration` 类有正确的 `@ConditionalOnXXX` 守卫
- [ ] 没有使用 `@AutoConfigureBefore` / `@AutoConfigureAfter` 控制基座内部顺序
- [ ] 过滤器注册 Bean 名称在整个 `ydsz-common` 范围内唯一，不冲突
- [ ] `FilterRegistrationBean` 注册的 URL pattern 正确（`/*` 或更精确的 `/api/**`）

### 6.3 兼容性与测试

- [ ] 单元测试通过（`mvn test`）
- [ ] 如果你修改了 `BaseMvcConfiguration` 或其子类，已在至少 2 个实际业务模块中启动验证
- [ ] ArchUnit 测试（`ThreadPoolArchitectureTest` 等新增约束）通过
- [ ] 基座模块自身可编译（`mvn -pl ydsz-common-base -am package`）

### 6.4 文档与变更记录

- [ ] CHANGELOG.md 描述本次变更的模块和动机
- [ ] 若变更基座层能力，已同步更新 `docs/capability-matrix.md`
- [ ] 基座影响面已在 PR 描述中列举（哪些业务模块可能需要联动测试）

### 6.5 版本与发布

- [ ] 基座模块版本号遵循 `26.09.01-SNAPSHOT` 规范
- [ ] 若变更基座 API，已评估是否需要 shared module 版本号升级（见 `docs/云顶版本规范.md`）
- [ ] SNAPSHOT 版本推送至内部 Nexus 后下游模块能正常解析

---

## 附录 A：当前基座依赖关系图（简化）

```
                    ┌────────────────────┐
                    │  ydsz-common-base  │  (L6 - 公共 HTTP 基座)
                    ├────────────────────┤
                    │ spring-boot-web ✗  │  ← 不应基座直接包含
                    │ spring-boot-health │
                    │ springdoc(opt)     │
                    │ knife4j(opt)       │
                    │ core / util / safe │
                    │ exception / auth   │
                    │ locales / config   │
                    │ json / actuator(opt│
                    └─────────┬──────────┘
                              │ 传递依赖
                              ▼
                    ┌────────────────────┐
                    │  ydsz-common-web   │  (L6 - Web 端基座)
                    ├────────────────────┤
                    │ base全部          │
                    │ + domain / redis  │  ← redis 应迁出
                    │ + yauaa           │  ← UA 解析应迁出
                    │ + servlet-api     │
                    │ + security        │
                    │ + actuator / health│ ← 可选化
                    │ + mybatis(jar,opt)│
                    │ + jasypt(opt)     │
                    │ + redisson(opt)   │
                    └────────────────────┘
```

## 附录 B：重构后目标依赖关系图

```
                    ┌────────────────────────┐
                    │ ydsz-common-base-core  │  (L6a - 纯启动基座)
                    ├────────────────────────┤
                    │ core / util / safe     │
                    │ / exception / auth     │
                    │ / locales / config     │
                    │ / json / i18n          │
                    │ (NO servlet / NO redis)│
                    └─────┬──────────┬───────┘
                          │          │
              ┌───────────┘          └────────────┐
              ▼                                   ▼
┌──────────────────────────┐          ┌──────────────────────────┐
│ ydsz-common-base-web     │          │ ydsz-common-base-app     │
│ (L6b - HTTP 层基座)      │          │ (L6c - App 端基座)       │
├──────────────────────────┤          ├──────────────────────────┤
│ base-core                │          │ base-core                │
│ spring-boot-web          │          │ AppMVC / AppAuth        │
│ actuator(opt)            │          │ AppRequestId            │
│ health(opt)              │          └──────────────────────────┘
│ springdoc(opt)           │
│ knife4j(opt)             │          ┌──────────────────────────┐
│ internal-sign            │          │ ydzs-common-session-web  │
└──────────────────────────┘          │ (L7 - Redis Session)     │
                                      ├──────────────────────────┤
                                      │ base-web                 │
                                      │ ydzs-common-redis        │
                                      └──────────────────────────┘
```

---

*本文档为基座分层优化的工程建议，具体实施计划和版本规划由架构委员会评估后纳入迭代。*
