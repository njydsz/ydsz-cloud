# YDSZ-PMIS 编码规范（coding-standards）

> 本文档是 `ydsz-backend/pom.xml` 中 `requireUpperBoundDeps`、`dependencyConvergence`、`bannedDependencies`、
> JaCoCo `check`、SpotBugs `excludeFilterFile` 等质量门禁配置的权威说明。
> 当 pom.xml 注释出现“详见 coding-standards.md Section N”时，以本文对应章节为准。
>
> 关联文档：
> - 代码注释规范：[CODE_COMMENT_STANDARD.md](./CODE_COMMENT_STANDARD.md)
> - CheckStyle 规则：`ydsz-backend/checkstyle.xml`
> - SpotBugs 排除：`ydsz-backend/spotbugs-exclude.xml`
> - 架构约束：各模块 `ArchitectureRulesTest.java`（ArchUnit R1–R29）

---

## Section 1：代码风格规范

### 1.1 风格统一入口：EditorConfig

- 项目所有源码的**字符集、缩进、换行符、文件尾换行**统一由 EditorConfig 约定。
- 仓库根目录应放置 `.editorconfig`（若缺失，IDE 默认配置视为临时回退，新增/修改文件必须遵循下表）。

| 项 | Java / XML / properties | YAML / Markdown | 前端（ts/tsx/vue/js/mjs/css/scss） |
|---|---|---|---|
| charset | UTF-8 | UTF-8 | UTF-8 |
| indent_style | space | space | space |
| indent_size | 4 | 2 | 2 |
| end_of_line | lf | lf | lf |
| insert_final_newline | true | true | true |
| trim_trailing_whitespace | true | true | true |

### 1.2 与 CheckStyle 的边界

- **风格规则不进 CheckStyle**：`ydsz-backend/checkstyle.xml` 仅强制“正确性”与“安全性”规则（R1–R4），风格由 EditorConfig 统一。
- 设计原则：零误报 — CheckStyle 规则必须是确定性的、不依赖主观审美判断。

### 1.3 Java 基本风格要点

- 行宽建议 ≤ 120，硬上限 140（CheckStyle 不强制，IDE 默认警告即可）。
- import 顺序：`java.*` → `javax.*`/`jakarta.*` → 第三方 → 项目内 `com.njydsz.*`；**禁止通配符 import**（`import xxx.*`）。
- 一个顶层类一个文件，文件名与类名一致。
- 注释规范详见 [CODE_COMMENT_STANDARD.md](./CODE_COMMENT_STANDARD.md)。

---

## Section 2：命名规范

### 2.1 包命名

- 根包：`com.njydsz`。
- 子包按 DDD 分层：`api`（对外契约）/ `domain`（领域层）/ `app`（应用层）/ `web`（适配层）/ `infra`（基础设施）。
- 包名全小写、连续单词不拆分下划线，如 `com.njydsz.project.domain.evm`。

### 2.2 类型命名

| 类型 | 后缀/前缀 | 示例 |
|---|---|---|
| 领域聚合/实体 | `Entity` 或无后缀 | `ProjectEntity`、`Project` |
| 值对象 | `VO` / `Value` | `MoneyVO` |
| 数据传输对象 | `DTO` | `ProjectDTO` |
| 视图对象 | `VO` | `ProjectVO` |
| 领域服务 | `DomainService` | `EvmDomainService` |
| 应用服务 | `AppService` / `ApplicationService` | `ProjectAppService` |
| 适配器 | `Adapter` / `Controller` | `ProjectController` |
| 仓储 | `Repository` / `Mapper` | `ProjectRepository`、`ProjectMapper` |
| 枚举 | 无强制后缀，建议 `Enum` | `ProjectStatusEnum` |
| 常量类 | `Constants` / `Const` | `ProjectConstants` |
| 异常 | `Exception` / `Error` | `ProjectBizException` |
| 测试类 | `Test` / `IT` / `Benchmark` | `ProjectServiceTest`、`ProjectIT`、`UtilBenchmark` |

### 2.3 方法与变量

- 方法名小驼峰，动词开头：`calculateEvm`、`listByProjectId`。
- 布尔变量/方法以 `is`/`has`/`can`/`should` 开头：`isActive`、`hasPermission`。
- 常量全大写下划线：`MAX_RETRY_COUNT`。
- **禁止**使用拼音命名、缩写歧义命名（如 `a`、`b`、`temp1`）。

---

## Section 3：异常处理规范

### 3.1 异常分层

| 层次 | 类型 | 说明 |
|---|---|---|
| 系统异常 | `RuntimeException` / 框架异常 | 不可恢复，由全局兜底处理，**不吞** |
| 业务异常 | `BizException` / `XxxBizException` | 可预期业务规则违反，携带错误码 |
| 参数校验异常 | `IllegalArgumentException` / JSR-303 `ConstraintViolationException` | 入参不合法 |

### 3.2 强制规则（与 CheckStyle R2/R3 对齐）

- **R2**：禁止 `System.out` / `System.err` 直接输出，必须使用 SLF4J。
- **R3**：禁止 `printStackTrace()`，必须使用 SLF4J `log.error(msg, e)` 记录异常栈。
- **禁止空 catch**：`catch (Exception e) {}` 视为缺陷；如确需吞掉，必须注释说明原因并记录 WARN 日志。
- **禁止 catch 顶层 Throwable**：除非位于最外层兜底过滤器（如网关 `GlobalExceptionHandler`）。

### 3.3 错误码体系

- 错误码分段详见 [adr/001-错误码分段体系设计.md](./adr/001-错误码分段体系设计.md)。
- 业务异常必须携带分段错误码，禁止直接抛 `new RuntimeException("xxx")`。

---

## Section 4：测试规范

> pom.xml JaCoCo `check` 执行项注释引用本节：“覆盖率阈值检查（项目已全局禁用，详见 coding-standards.md Section 4）”。

### 4.1 单元测试覆盖率要求

覆盖率阈值由 `ydsz-backend/pom.xml` 的 JaCoCo `check` 执行项配置：

| 指标 | 阈值 | 配置属性 |
|---|---|---|
| 行覆盖率（LINE） | ≥ 60% | `jacoco.line.coverage` = 0.60 |
| 分支覆盖率（BRANCH） | ≥ 50% | `jacoco.branch.coverage` = 0.50 |

启用方式（本地默认跳过，CI 启用）：

```bash
# 本地构建（跳过覆盖率采集，加速开发）
mvn verify

# 生成各模块 target/site/jacoco 报告
mvn verify -DskipJacoco=false

# 启用覆盖率阈值检查（CI 集成）
mvn verify -DskipJacoco=false -DskipJacocoCheck=false
```

- `skipJacoco` / `skipJacocoCheck` 本地默认 `true`，CI 通过 `-DskipJacoco=false -DskipJacocoCheck=false` 启用。
- 当前项目已恢复单元测试（105+ 测试类分布于 `common-json` / `cache` / `queue` / `literule` / `agent` / `message` 等模块）。

### 4.2 测试基类使用

- **单元测试基类**：纯 JVM 单测，不启动 Spring 上下文，命名 `XxxTest`，由 `maven-surefire-plugin` 执行。
- **集成测试基类**：继承 `AbstractIntegrationTest`，自动启动 PostgreSQL / Redis 容器（Testcontainers），命名 `XxxIT`，由 `maven-failsafe-plugin` 执行。
  - Testcontainers 版本：`${testcontainers.version}` = 1.20.6。
  - 需要本地或 CI 有 Docker 环境。
- **基准测试**：使用 JMH（`${jmh.version}` = 1.37），命名 `XxxBenchmark`，如 `UtilBenchmark`。

### 4.3 集成测试规范

- 集成测试类名以 `IT` 结尾，与单测 `Test` 区分，确保 `surefire`（`*Test`）与 `failsafe`（`*IT`）执行边界清晰。
- 集成测试必须通过 `AbstractIntegrationTest` 获取容器化 PG/Redis，**禁止**直连本地开发库。
- 测试数据使用 `@Transactional` 回滚或 Testcontainers 一次性容器，**禁止**污染共享环境。

### 4.4 SpotBugs / 测试类排除

- `*Test` / `*IT` / `*Benchmark` 类在 SpotBugs 中全局排除（见 `spotbugs-exclude.xml`），因测试代码允许使用反射、可见性 hack 等非常规手段。

---

## Section 5：依赖管理规范

### 5.1 版本统一原则

- 所有第三方依赖版本**必须**在根 POM `<dependencyManagement>` 中统一声明，子模块**禁止**声明 `<version>`。
- 版本号通过 `<properties>` 集中管理，命名 `<artifact>.version`（如 `resilience4j.version`、`poi.version`）。

### 5.2 BOM 优先

- 优先通过 `<scope>import</scope>` 导入官方 BOM 管理全家桶版本：
  - Spring Boot BOM（parent：`spring-boot-starter-parent`）
  - Spring Cloud BOM、Spring Cloud Alibaba BOM
  - Resilience4j BOM（`resilience4j-bom`）
- 当 BOM 管理的版本与项目要求冲突时，在 `<dependencyManagement>` 中**显式覆盖**（项目声明优先于 BOM）。

### 5.3 Maven Enforcer 约束

- `requireUpperBoundDeps`：禁止依赖树中出现“被压低的上界”（dependencyManagement 强制低版本而传递依赖要求高版本）。
- `dependencyConvergence`：禁止同一依赖出现多个版本。
- `bannedDependencies`：禁止历史不安全/不兼容依赖（详见 Section 6）。
- `requireNoRepositories`：禁止子模块私自声明 Maven 仓库，统一使用 parent POM 的 aliyun / central。

### 5.4 竞品约束（与 ArchUnit R29 对齐）

- JSON 序列化统一使用 `ydsz-common-json`（`YdszJson`）。
- **禁止**直接声明 `gson` / `hutool-json` / `org.json` / `json-simple`（`searchTransitive=false`，仅检查直接声明）。
- `jackson-databind` 不在全局 ban（公共模块有第三方 SDK 合理使用场景），由 ArchUnit R29 在编译期约束业务代码不直接 import。

---

## Section 6：安全规范

### 6.1 禁止依赖（bannedDependencies）

| 依赖 | 原因 | 替代 |
|---|---|---|
| `com.alibaba:fastjson` < 2 | 历史反序列化安全漏洞 | `fastjson2`（2.x） |
| `commons-collections` 3.x | 反序列化漏洞（CVE-2015-7501） | `commons-collections4` |
| `org.codehaus.jackson:*`（Jackson 1.x） | 已停止维护 | `com.fasterxml.jackson` 2.x |
| `javax.servlet` / `javax.validation` / `javax.persistence` / `javax.annotation` / `javax.ws.rs` | Spring Boot 4 使用 `jakarta.*` | `jakarta.*` 对应包 |
| `log4j:log4j`（1.x） | 安全漏洞 | SLF4J + Logback |
| `org.apache.logging.log4j:log4j-core` | CVE-2021-44228（Log4Shell） | 由 Spring Boot 管理 log4j-to-slf4j 桥接 |
| `org.slf4j:slf4j-log4j12` | 绑定冲突 | SLF4J + Logback |

### 6.2 敏感信息

- 配置加密统一使用 Jasypt（`jasypt-spring-boot-starter`），**禁止**明文存储数据库密码、密钥。
- 密钥、Token、证书**禁止**提交到 Git；`.gitignore` 已排除 `*.p12` / `*.jks` / `application-local.yml`。

### 6.3 静态安全扫描

- **SpotBugs + FindSecBugs**（`spotbugs-maven-plugin`）：`effort=Max`、`threshold=Low`、`failOnError=true`，排除规则见 `spotbugs-exclude.xml`。
- **OWASP Dependency-Check**（`skipDependencyCheck` 本地默认跳过，CI 启用）：扫描已知 CVE。
- 检出 Critical / High 漏洞必须在发布前修复或加 `dependency-check-suppressions.xml` 白名单（须注释说明理由）。
