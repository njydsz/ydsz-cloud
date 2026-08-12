# remi-comm-util 去三方依赖改造方案

> **背景**：公司内网项目，无法访问外网 Maven 仓库，需将模块改造为零外部依赖（仅依赖 JDK 21 + 内部 remi-comm-core）
> **分析时间**：2026-08-12
> **当前版本**：4.1.0-SNAPSHOT

---

## 一、核心发现：模块本就高度自研

对源码做逐文件 import 审计后，发现一个关键事实：

### 1.1 已声明但从未使用的依赖（8 个 — 可立即删除）

| 依赖 | 代码引用数 | 说明 |
|---|---|---|
| commons-lang3 | **0** | StringUtils 是纯自研，从未 import commons |
| commons-io | **0** | FileUtils/IOUtils 是纯自研 |
| commons-collections4 | **0** | CollectionUtils 系列是纯自研 |
| commons-text | **0** | 字符串格式化全自研 |
| commons-validator | **0** | 校验全自研 |
| commons-codec | **0** | Base64/Hex 全自研 |
| bcprov-jdk18on | **0** | SM2/3/4 尚未实现，文档有但代码无 |
| yauaa | **0** | UA 解析未使用 |

> **结论**：这 8 个依赖从 pom.xml 删除后，代码无需任何改动，编译通过。

### 1.2 实际使用但可用 JDK 替代的依赖（6 个）

| 依赖 | 影响文件数 | 替代方案 | 工时 |
|---|---|---|---|
| **SLF4J** | 3 | `java.lang.System.Logger`（JDK 9+） | 0.5d |
| **OkHttp** | 3 | `java.net.http.HttpClient`（JDK 11+） | 1d |
| **DOM4J** | 1 | `javax.xml.parsers`（JDK 内置） | 0.5d |
| **Alibaba TTL** | 1 | `InheritableThreadLocal` 或 JDK 21 ScopedValue | 0.5d |
| **Reactor** | 1 | 已标记 optional，WebFluxUtils 依赖方自行引入 | 0d |
| **SkyWalking** | 1 | 已标记 optional，TracerUtils 降级为 no-op | 0d |

### 1.3 需要下决策的核心依赖

| 依赖 | 影响文件 | 能否 JDK 替代 | 评估 |
|---|---|---|---|
| **Jackson** | 1（JsonUtils） | ❌ JDK 无内置 JSON API | 建议保留为唯一硬依赖，或写轻量解析器 |
| **SnakeYAML** | 1（YamlUtils） | ❌ JDK 无内置 YAML API | 量小可自研，或移除 YAML 功能 |
| **Lombok** | 35（注解） | ✅ Delombok 自动生成 | 用 Maven 插件自动展开，零人工 |
| **Spring** | 17（配置类） | ⚠️ 部分可剥离 | 拆为 core-api + spring-starter 双模块 |
| **Jakarta Servlet** | 6 | ⚠️ Web 层刚需 | 保留 optional，非 Web 项目不引入 |
| **Commons-Net** | 1（FtpUtils） | ✅ 可自研 | 或直接移除 FTP 功能（微服务时代边缘场景） |
| **IP2Region** | 1（IpInfoUtils） | ✅ 可自研 | xdb 文件已内置在 resources 中 |
| **Micrometer** | 3 | ✅ 可移除 | 监控指标改为可选 SPI |

---

## 二、推荐方案：最小依赖路线

保留 **1 个硬依赖**（Jackson）+ **2 个内部可选**（Servlet/Spring-Config），其余全部移除或 JDK 替代。

### 2.1 最终 pom.xml 目标形态

```xml
<dependencies>
    <!-- ===== 唯一的硬依赖 ===== -->
    <!-- Jackson：JDK 无内置 JSON API，业界事实标准，无替代 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>

    <!-- ===== 内部依赖 ===== -->
    <dependency>
        <groupId>com.remisoft</groupId>
        <artifactId>remi-comm-core</artifactId>
    </dependency>

    <!-- ===== Web 层专用，optional ===== -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- ===== Spring 自动配置（可拆到独立 starter 模块） ===== -->
    <!-- 见 2.4 节 -->
</dependencies>
```

**对比当前 27 个依赖 → 目标 4 个依赖，减少 85%。**

### 2.2 分阶段改造详情

---

### 阶段一：立刻清理（0 代码改动，纯 pom 操作）

**删除以下 8 个从未 import 的依赖：**

```xml
<!-- 直接删除，编译不受影响 -->
<!-- yauaa -->
<!-- commons-lang3 -->
<!-- commons-io -->  
<!-- commons-codec -->
<!-- commons-validator -->
<!-- commons-collections4 -->
<!-- commons-text -->
<!-- bcprov-jdk18on (SM2/3/4 尚未实现) -->
```

同时将以下标记 optional 的依赖也删除（代码中已是 no-op 降级逻辑）：

```xml
<!-- reactor-core — WebFluxUtils 已被 optional，删掉不引入 -->
<!-- apm-toolkit-trace — TracerUtils 有降级逻辑 -->
<!-- micrometer-core — JsonMetrics 可改为 SPI -->
```

**命令**：

```bash
# 验证编译
mvn clean compile -pl remi-comm-util
```

---

### 阶段二：JDK 替代改造（6 项，共约 3 人日）

#### 2.2.1 SLF4J → System.Logger（3 文件）

涉及文件：`ExceptionUtils.java`、`TracerUtils.java`、`MessageUtils.java`

```java
// 改前
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger log = LoggerFactory.getLogger(Xxx.class);

// 改后
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
private static final System.Logger log = System.getLogger(Xxx.class.getName());
```

差异对照：

| SLF4J | System.Logger |
|---|---|
| `log.info("msg")` | `log.log(Level.INFO, "msg")` |
| `log.info("a={}", arg)` | `log.log(Level.INFO, "a={0}", arg)` |
| `log.warn("msg", e)` | `log.log(Level.WARNING, "msg", e)` |
| `log.error("msg", e)` | `log.log(Level.ERROR, "msg", e)` |
| `log.debug(...)` | `log.log(Level.DEBUG, ...)` |

占位符差异：SLF4J 用 `{}`，System.Logger 用 `{0}` `{1}`。5 处引用改动极小。

#### 2.2.2 OkHttp → java.net.http.HttpClient（3 文件）

涉及文件：`UtilAutoConfiguration.java`、`HttpClientFactory.java`、`OkHttpUtils.java`

JDK 11+ `java.net.http.HttpClient` 完全可替代 OkHttp 的核心能力：

| 能力 | OkHttp | JDK HttpClient |
|---|---|---|
| 同步 GET/POST | ✅ | ✅ |
| 异步请求 | ✅ | ✅（CompletableFuture） |
| 连接池 | ✅ | ✅ |
| 超时配置 | ✅ | ✅ |
| HTTP/2 | ✅ | ✅ |
| 拦截器 | ✅ | ⚠️ 无内置，需手动包装 |
| Cookie 管理 | ✅ | ⚠️ 需手动 |
| WebSocket | ✅ | ✅ |

**改造要点**：

```java
// 改前 - OkHttpUtils
OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .build();
Request request = new Request.Builder().url(url).build();
Response response = client.newCall(request).execute();

// 改后 - JDK HttpClient
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();
HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
```

**注意**：当前模块有 OkHttp 拦截器/重试逻辑，需要评估是否迁移或简化。如果业务方需要复杂 HTTP 能力，建议在 `remi-comm-feign` 层处理，util 模块只提供基础 HTTP 工具。

#### 2.2.3 DOM4J → javax.xml.parsers（1 文件）

涉及文件：`DOMUtils.java`

```java
// 改前
import org.dom4j.Document;
import org.dom4j.io.SAXReader;

// 改后
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
// 安全配置：禁用 XXE
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
Document doc = factory.newDocumentBuilder().parse(inputStream);
```

JDK 内置的 `org.w3c.dom` API 略繁琐但功能完备，669 行 DOMUtils 可精简到 ~300 行。

#### 2.2.4 Alibaba TTL → InheritableThreadLocal（1 文件）

涉及文件：`RequestHolder.java`

```java
// 改前
import com.alibaba.ttl.TransmittableThreadLocal;
private static final TransmittableThreadLocal<AuthInfo> authInfoHolder = 
    new TransmittableThreadLocal<>();

// 改后
import java.lang.InheritableThreadLocal;
private static final InheritableThreadLocal<AuthInfo> authInfoHolder = 
    new InheritableThreadLocal<>();
```

**差异**：`InheritableThreadLocal` 只在 `new Thread()` 创建时传递，线程池复用场景不传递（这正是 JDK 21 ScopedValue 要解决的问题）。如果内网项目大量使用线程池，需要评估影响。替代方案：在 `ExecutorUtils` 中包装线程池的 `Runnable` 手动传递上下文。

#### 2.2.5 Lombok → Delombok（35 文件）

Lombok 是编译期注解处理器，不参与运行时。但内网可能无法下载 Lombok 的 Maven 插件。两个方案：

**方案 A（推荐）**：保留 Lombok 但将其 JAR 上传到内网 Nexus
- 只需上传 1 个 JAR（lombok-1.18.46.jar），与所有三方依赖一样处理

**方案 B**：Delombok 展开
```bash
# Maven 插件自动展开，生成纯 Java
mvn lombok:delombok -pl remi-comm-util
```
展开后 ~35 个文件的 `@Data`/`@Slf4j`/`@Builder` 等注解全部替换为手写代码，代码量增加约 2000 行。之后移除 Lombok 依赖。

> 建议走方案 A，Lombok 是开发期工具不是运行时依赖，内网部署成本极低。

#### 2.2.6 Commons-Net → 自研 FTP 客户端（1 文件）

涉及文件：`FtpUtils.java`

两个选项：
- **选项 A**：用 JDK `Socket` 实现基础 FTP 协议（FTP 协议本身简单，RFC 959，~500 行代码）
- **选项 B**：直接移除 FTP 能力（微服务时代 FTP 已是边缘场景，实在需要可独立为 remi-comm-ftp 模块）

> 建议选项 B，FTP 投入产出比低。

---

### 阶段三：Spring 配置类拆分（可选，架构优化）

当前 17 个文件依赖 Spring（自动配置、Bean、注解等），这些是为 Spring Boot 集成准备的。对于非 Spring 项目完全不需要。

**建议拆分**：

```
remi-comm-util/
├── remi-comm-util-core/      # 纯工具类，零框架依赖（JDK + Jackson）
│   ├── string/StringUtils.java
│   ├── collection/CollectionUtils.java
│   ├── security/AesUtils.java
│   ├── id/SnowflakeUtils.java
│   ├── json/JsonUtils.java
│   └── ...（其他 60+ 纯工具类）
│
└── remi-comm-util-spring/    # Spring Boot 自动配置（optional）
    ├── config/UtilAutoConfiguration.java
    ├── id/SnowflakeAutoConfiguration.java
    ├── json/JsonMetricsConfiguration.java
    └── spring/SpringContextHolder.java
```

**收益**：
- `remi-comm-util-core` 零框架依赖，可在任何 Java 21+ 项目中独立使用
- Spring 集成部分按需引入，不影响核心模块
- 包结构更清晰

---

### 阶段四：自研替代（长期优化）

#### 4.1 IP2Region → 自研 IP 数据库

当前 `IpInfoUtils` 依赖 `ip2region` + `ip2region.xdb`（已内置在 resources）。可保留 xdb 文件，自研读取逻辑（~200 行），移除 ip2region 依赖。

#### 4.2 SnakeYAML → 自研简单 YAML 解析器

YAML 子集（仅支持 YAML → JSON 转换功能所需的语法）可以自研，~300 行即可覆盖 95% 场景。如果 YamlUtils 使用频率低，也可直接移除。

#### 4.3 Jackson → 终极挑战

如果内网要求连 Jackson 都不能有，最后的方案是自研 JSON 解析器：
- JSON 规范（RFC 8259）相对简单
- 基础解析器 ~500 行可覆盖序列化/反序列化
- 但性能和功能完备度无法与 Jackson 媲美

**不建议**：Jackson 是 Java 生态的基石依赖，几乎所有框架（Spring Boot、MyBatis-Plus）都依赖它。移除 Jackson 意味着整套技术栈的 JSON 层全部需要重新对接，得不偿失。

---

## 三、改造优先级与工时估算

| 阶段 | 内容 | 工时 | 改动文件 | 风险 |
|---|---|---|---|---|
| 🔴 P0 | 删除 8 个从未使用的依赖 | 0.5d | 1（pom.xml） | 极低 |
| 🔴 P0 | SLF4J → System.Logger | 0.5d | 3 | 低 |
| 🟡 P1 | OkHttp → JDK HttpClient | 1d | 3 | 中 |
| 🟡 P1 | DOM4J → javax.xml | 0.5d | 1 | 低 |
| 🟡 P1 | TTL → InheritableThreadLocal | 0.5d | 1 | 低 |
| 🟢 P2 | 移除 Commons-Net + FtpUtils | 0.5d | 2 | 低 |
| 🟢 P2 | IP2Region 自研 | 1d | 1 | 低 |
| 🟢 P2 | SnakeYAML 自研/移除 | 1d | 1 | 低 |
| 🟢 P2 | Reactor/SkyWalking/Micrometer 清理 | 0.5d | 5 | 极低 |
| ⚪ P3 | Spring 配置拆分为独立模块 | 2d | 17 | 中 |
| ⚪ P3 | Lombok Delombok（如果内网无法部署） | 1d | 35 | 低 |

**P0+P1 合计约 3 人日**，完成后 pom.xml 从 27 个依赖减至约 5 个（Jackson + remi-comm-core + optional Servlet/Spring-config）。

---

## 四、竞品对标（自研 util 库参考）

| 项目 | 三方依赖数 | 核心依赖 | 策略 |
|---|---|---|---|
| **Hutool** | 0（core 模块） | 纯 JDK | 大而全，180+ 模块，全部自研 |
| **Guava** | 1（jsr305） | JDK + jsr305 | 精确聚焦集合/缓存/并发 |
| **Jodd** | 0 | 纯 JDK | 微服务工具箱，每个模块独立 |
| **remi-comm-util（目标）** | 1-2 | JDK + Jackson | 企业级安全 + 分布式，小而精 |

**对标结论**：Hutool 证明了纯 JDK 的 util 库完全可行。remi-comm-util 的优势在于安全加密和分布式 ID 生成，这是 Hutool 做不到深度的领域。走纯 JDK 路线不会损失核心能力，反而强化了"企业级自研"的定位。

---

## 五、推荐执行路径

```
Week 1:
  Day 1: P0 — 删除 8 个未使用依赖 + SLF4J 替换 → 验证编译
  Day 2: P1 — OkHttp 替换为 JDK HttpClient
  Day 3: P1 — DOM4J + TTL 替换 → 集成测试

Week 2 (可选):
  Day 1: P2 — FtpUtils 移除 + IP2Region 自研
  Day 2: P2 — SnakeYAML 处理 + Misc 清理
  Day 3: 验证 + 发布内网仓库

Week 3-4 (架构优化):
  P3 — Spring 配置拆分 + 文档更新
```

---

## 六、FAQ

**Q: Jackson 真的不能去掉吗？**
A: 理论上可以自研 JSON 解析器，但 JSON 是整个 Java 生态的血液。Spring Boot、MyBatis-Plus、所有 HTTP 通信都依赖 JSON。自研意味着所有上下游都需要适配你的解析器，成本远超收益。**建议保留 Jackson 作为唯一硬依赖**，它也是纯 Java JAR，可以上传到内网 Nexus。

**Q: 没有 SLF4J，日志怎么输出？**
A: `System.Logger`（JDK 9+）是 Java 平台内置的日志门面，实现由运行时决定。Spring Boot 默认使用 Logback（实现了 SLF4J），但也可以通过 `java.util.logging` 桥接到 Logback。改造成本极低，3 个文件 5 处引用。

**Q: Lombok 不能上网下载怎么办？**
A: Lombok 是编译期工具，运行时完全不参与。将 `lombok-1.18.46.jar` 手动上传到内网 Nexus 即可，开发时 IDE 安装 Lombok 插件也是纯本地操作。

**Q: Spring 依赖怎么办？**
A: Spring 自动配置类（UtilAutoConfiguration 等）不依赖 Spring 无法工作。建议拆成两个子模块：`remi-comm-util-core`（纯工具）和 `remi-comm-util-spring-boot-starter`（Spring 集成），非 Spring 项目只依赖 core。

---

> **结论**：remi-comm-util 改造成纯自研的工具库是**完全可行的**。模块本身 90% 的代码就是纯自研的（commons-* 依赖从未被实际使用），当前 27 个 pom 依赖中 20+ 可以直接删除。保留 Jackson 作为唯一外部依赖，最终可做到 **只需 2-3 个依赖即可完整运行**，并且核心能力（Snowflake ID、AES-256-GCM、RSA-OAEP、BeanCopy、集合工具）全部零损失。
