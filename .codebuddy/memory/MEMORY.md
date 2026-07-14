# 项目长期记忆

## LiteRule 规则引擎

### 架构概要
- 模块: ydsz-pmis-literule (119个Java文件, 12个子模块)
- 子模块: api(17) + core(10) + impl(7) + orchestrator(10) + config(9) + ai(10) + distributed(9) + expr + cep + spi + dsl + event/calc
- 5种规则实现: Expression/DecisionTable/Scorecard/DecisionTree/Script + StaticRule
- 编排: 8种链类型(THEN/WHEN/IF/ELIF/SWITCH/FOR/WHILE/BREAK) + 画布图双向转换
- Bean装配: LiteRuleAutoConfiguration, 配置前缀 pmis.literule.*
- 管理入口: project模块的RuleAdminController (48+ REST端点)

### 独有优势（竞品不具备）
- 规则冲突检测(7种)、A/B测试、审批工作流(5状态机)、熔断+灰度、AI三件套(NL2Rule+健康度+规则推荐)、执行回放差异分析、多租户隔离、规则集市场

### 已知代码缺陷（已修复 P0-4）
- ~~DecisionTableRule.java 第231行: `!=null`条件匹配bug~~ → 已修复，第258行正确处理 `!=null` 
- ~~ChainGraphConverter.java: WHEN边类型未区分~~ → 已修复，第128-130行 WHEN 使用独立边类型
- ~~ChainGraphConverter.java: CHAIN/GROUP反向解析未实现~~ → 已修复，第530行 SINGLE 兜底为 THEN 链
- ~~ChainGraphConverter.java: IF/SWITCH条件还原为启发式~~ → 已修复，第549-556行优先从 metadata/condition 精确还原
- ~~RuleChain.java: transient字段线程安全隐患~~ → 已修复 P0-4，getExpressionCache() 使用双重检查锁
- ~~ScriptRule.java: 第三层超时防御未实现~~ → 已实现，第253-274行 FutureTask + 超时中断

### 核心短板（对标竞品）
- 缺RETE算法(线性扫描)、缺DSL文本语法、脚本沙箱不完整、多数据源支持不足(仅DB)、缺表达式级追踪/归因、缺交叉决策表/复杂评分卡

## 公司代码规范（强制）

### 禁止行内全限定类名（FQN）
- **规则**：Java 代码中不允许出现行内 FQN 用法，必须使用标准 `import` 语句后在代码中直接引用简单类名。
- **触发案例**：
  - `InitiationFeignClient.java` 中 `Result<String> create(@RequestBody com.njydsz.pmis.project.api.dto.InitiationCreateDTO dto)` 违规，已修复为 import + 简单类名。
  - `JobService.java` / `JobDagService.java` 等 8 个文件中 Javadoc `@throws com.njydsz.pmis.common.exception.SysException` 违规，已修复为 import + `@throws SysException`。
  - `IFileStorage.java` / `LogicalDeleteConfiguration.java` 等 10 个文件中 Javadoc `@see com.njydsz.pmis.xxx.XxxClass` 违规，已修复为 import + `@see XxxClass`。
  - `EnableAudit.java` 中 `@Import(com.njydsz.pmis.common.audit.config.AuditAutoConfiguration.class)` 违规，已修复为 import + `@Import(AuditAutoConfiguration.class)`。
  - `NotifyChannelStrategy.java` 中方法参数 `com.njydsz.pmis.common.notify.template.TemplateEngine templateEngine` 违规，已修复为 import + `TemplateEngine templateEngine`。
- **覆盖范围**：类型引用、`.class` 字面量、注解（含 `@Import` 等注解参数）、静态方法调用、`new` 表达式、`instanceof` 检查、方法引用（`::`）、Javadoc `@throws`/`@see`/`@param`/`@return` 标签中的类型名。
- **唯一例外**：字符串字面量中的 FQN（如反射类名）、Javadoc `{@link FQN}` 引用（仅 `{@link}` 标签，不含 `@throws`/`@see` 等）可保留完整路径。但如果该类已被 import，则必须使用简单类名。
- **规则文件**：`.trae/rules/no-inline-fqn.md`（`alwaysApply: true`）。
- **详细文档**：`deploy/docs/architecture/coding-standards.md`。
- **修复历史**：
  - (1) 2026-07-12 第一轮修复。
  - (2) 2026-07-12 第二轮修复：清理 `@throws`/`@see`/`@Import`/方法参数 FQN 违规。
  - (3) 2026-07-13 第三轮修复：全面清理 `java.util.*`（List/Map/Set/ArrayList/HashMap/Collections/Arrays/Collection 等）、`java.time.*`（LocalDateTime/Duration）、`java.math.*`（BigDecimal/RoundingMode）、`java.net.*`（URLEncoder）、`java.nio.charset.*`（StandardCharsets）、`java.lang.reflect.*`（Field/Method/Proxy/Array）、`java.util.concurrent.*`（TimeUnit）、`java.util.function.*`（Consumer）、`javax.sql.*`（DataSource）等所有 `java.*`/`javax.*` 行内 FQN，共修复 150+ 个文件。
  - (4) 2026-07-13 第四轮修复（FQN 标准化工程体系）：
    - **P0**：修复 `coding-standards.md` 文档矛盾（`@throws`/`@see` 中的 FQN 均属违规，仅 `{@link}` 可保留）；重写 `check-inline-fqn.sh` 脚本（修复 5 个 Bug：子 shell 变量丢失、错误跳过 @throws、无法检测注解 FQN、字符串检测漏洞、未检测 catch/instanceof）；将 FQN 检查集成到 CI 流水线 `backend-ci.yml`（`--strict` 模式阻断 PR）。
    - **P1**：批量修复所有残留违规——`common-file`（IFileStorage @see 7处、FileUploader 方法参数 6处、FileManager 返回类型 1处、FileLifecycleManager 4处、FileUploadValidator catch 1处、FileConfiguration new 1处、AbstractFileStorage 参数 1处、RedisMultipartContextStore 5处、UploadConcurrencyGuard 1处、CosStorage/ObsStorage/OssStorage 同名冲突 7处标记 FQN-OK）、`common-jdbc` + `common-exception`（@see FQN 15处）、`common-excel`（ConverterRegistry 11处 new 表达式）、`common-audit`（@Import 1处、DefaultAuditStorage new 1处、JdbcTemplate new 2处、CustomizableThreadFactory new 1处、SleepingWaitStrategy new 1处）、`common-redis`（FailOpenPolicy 5处、RedisKeysEnum 参数 1处、Metrics 参数 2处）、`common-web`（ApplicationContext 参数 1处、@see 4处）、`common-safe`（@Value 注解 2处、RemovalCause lambda 1处、@see 1处）、`common-socket`（WebSocketClusterPublisher 参数 1处、WebSocketClusterMessage 方法 1处）、`common-lock`（RedisConnection 变量 1处）、`common-docs`（PDDocumentInformation 变量 1处）、`common-queue`（RedisService 参数 1处）、`common-jdbc`（DataScopeType 变量 1处）、`common-util`（@see 2处转 {@link}）、`common-base`（@ConditionalOnMissingBean 注解 1处）、`common-exception`（@RestControllerAdvice 注解 1处）、`common-auth`（@NonNull/@Nullable 注解 2处）；业务模块——`literule`（@PathVariable 1处）、`cronjob`（LambdaQueryWrapper new 1处、OperatingSystemMXBean instanceof 2处标记 FQN-OK、@throws 1处）、`project`（LambdaQueryWrapper new 2处）。修复 Checkstyle severity 从 `warning` 改为 `error`。
    - **P2**：引入 Spotless + Google Java Format 插件（`pom.xml` 配置，`mvn spotless:apply` 自动修复）；配置 Git Pre-commit Hook（`deploy/scripts/pre-commit` + `install-hooks.sh`，仅检查暂存文件）。
    - **P3**：导出 IntelliJ IDEA 检查配置（`.idea/inspectionProfiles/Project_Default.xml`，启用 UnusedImport/RedundantImport/WildcardImport/JavaDocReference 等检查项）。
- **工程化防线（5 层）**：
  1. IDE 检查（IntelliJ IDEA Inspection Profile）
  2. Pre-commit Hook（`deploy/scripts/pre-commit`，仅检查暂存文件）
  3. Checkstyle（`checkstyle.xml`，severity=error，`mvn validate` 阶段执行）
  4. Spotless（`pom.xml`，Google Java Format，`mvn validate` 阶段执行）
  5. CI 流水线（`backend-ci.yml`，`check-inline-fqn.sh --strict` 阻断 PR 合并）
- **同名类冲突处理**：当两个类简单名相同（如 `com.sun.management.OperatingSystemMXBean` 与 `java.lang.management.OperatingSystemMXBean`，或云存储 SDK 的 `ObjectMetadata` 与项目域模型 `ObjectMetadata`），对其中一个使用 FQN 并在行尾添加 `// FQN-OK: name conflict with <ClassName>` 注释。检测脚本自动跳过带 `FQN-OK` 注释的行。

### 禁止使用 @SuppressWarnings 注解
- **规则**：Java 代码中不允许出现 `@SuppressWarnings` 注解。该注解会压制编译器警告，掩盖潜在的类型安全、未使用代码、弃用 API 等问题，违反项目「零警告」原则。所有警告必须从根源修复，而非压制。
- **覆盖范围**：所有 `src/main/java` 和 `src/test/java` 下的 Java 源文件，无例外。
- **常见修复指引**：
  - `unchecked`：使用 `TypeReference`、泛型方法签名、或重设计 API 避免 unchecked 转换。
  - `unused`：删除死代码（字段/方法/参数）。
  - `rawtypes`：始终指定泛型参数（如 `Map<String, Object>` 而非 `Map`）。
  - `deprecation`：迁移到推荐的新 API。
  - `all`：逐个分析和修复每个警告。
- **规则文件**：`.trae/rules/no-inline-fqn.md`（`alwaysApply: true`，与 FQN 规则同一文件）。
- **详细文档**：`deploy/docs/architecture/coding-standards.md`（Section 2）。
- **检测脚本**：`deploy/scripts/check-inline-fqn.sh` 同时检测 `@SuppressWarnings`，`--strict` 模式阻断 PR。
- **现有存量**：截至 2026-07-13，项目中存在约 208 处 `@SuppressWarnings` 用法，分布在 workflow、common-util、message、project 等模块，待后续批量修复。

### 脚本执行优先使用 Python 而非 PowerShell
- **规则**：在 ydsz-pmis 项目中执行脚本命令时（批量文件处理、文本替换、代码生成、数据转换、文件读写等），**必须优先使用 Python**，禁止使用 PowerShell。
- **原因**：
  1. **编码损坏**：PowerShell 默认使用 UTF-16 LE BOM 或系统 ANSI 编码读写文件，处理 UTF-8 无 BOM 的源代码文件时会转换为乱码。
  2. **BOM 污染**：PowerShell 的 `Out-File`、`Set-Content` 等 cmdlet 默认添加 BOM 前缀，导致 Java 编译器、Git diff、Spotless 等工具出现兼容性问题。
  3. **转义陷阱**：PowerShell 的引号转义规则与正则表达式交互混乱。
  4. **跨平台不一致**：Windows PowerShell 5.x 与 PowerShell 7+ 行为差异大，脚本可移植性差。
- **正确做法**：使用 Python `pathlib`、`io` 模块，固定 `encoding="utf-8"`，跨平台一致。
- **规则文件**：`.trae/rules/prefer-python-over-powershell.md`（`alwaysApply: true`）。
- **详细文档**：`deploy/docs/architecture/coding-standards.md`（Section 3）。
- **新脚本约束**：所有新增脚本工具（`deploy/scripts/`、`scripts/`）默认使用 Python 实现。**既有 `.ps1` 脚本（如 `check-bom.ps1`、`strip-bom.ps1`、`build-images.ps1` 等）逐步迁移到 `.py`**，迁移完成前可保留作为 Windows 兼容入口；即便在例外场景下，所有涉及文件读写、文本处理的操作也必须通过 Python 包装执行。
