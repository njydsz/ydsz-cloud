# 云顶编码规范合规深度检查报告

> 检查对象：ydsz-cloud 全量模块（10 大服务 + ydsz-common 30 子模块）
> 检查依据：《云顶编码规范》v2.15 + `docs/checkstyle.xml` v3.0 + `pom.xml` Enforcer/Spotless 配置
> 检查方式：`mvn checkstyle:check`（全模块）、`mvn enforcer:enforce`、静态代码扫描
> 检查时间：2026-08-16

---

## 一、结论概览

| 指标 | 数值 |
|------|------|
| src/main Java 文件总数 | **2,970** |
| Maven 模块总数 | **81**（10 服务 + 30 common 子模块 + api/domain/infra/server/web 子层） |
| Checkstyle 违规总数 | **22,442** |
| 其中「工具配置缺陷导致的误报」 | **≈6,500**（详见 P0-1 / P0-2） |
| 真实违规（剔除误报后） | **≈15,900** |
| 构建状态 | **`mvn validate` / `compile` / `package` 当前全部失败** |

**核心结论**：项目在架构分层、JSON 生态红线、线程安全、命名等"结构性"规范上执行得较好；但 Checkstyle v3.0 配置存在 3 处缺陷（其中 1 处正则写错导致 6,432 条误报、1 处规则过宽、1 处 Enforcer 依赖加载失败），直接把 `validate` 阶段的门禁打断；同时代码与"全量 Javadoc / 魔法值 / 大括号 / 圈复杂度"等严格项之间存在约 1.6 万条的存量债务。

---

## 二、P0 —— 阻断性问题（需立即修复，构建已不可用）

### P0-1：Checkstyle「接口方法修饰符」规则正则写错，产生 6,432 条误报

`docs/checkstyle.xml` 第 18 节（560-567 行）的正则：

```
\b(public|abstract)\s+(public|abstract|void|[A-Z][a-zA-Z0-9_&lt;&gt;,\s\?]+)\s+\w+\s*\(
```

该正则会匹配**所有** `public <返回类型> 方法名(` 形式的方法声明（含普通类方法），并非只匹配接口中的冗余 `public abstract`。实测：

```java
// BeanSerializerInfo.java:94 —— 这是普通类的普通方法，却被判为"接口方法冗余修饰符"
public String getStringValue(Object obj) { ... }
```

- 单是 `ydsz-common-json` 一个模块就贡献 **291** 条此类误报；全项目合计 **6,432** 条（占违规总量 28.7%）。
- 该规则与第 378 行的 `RedundantModifier`（已能正确识别接口冗余 `public/abstract`）**完全重复且错误**。

**修复**：删除 checkstyle.xml 第 560-567 行的整个 `RegexpSinglelineJava` 模块，保留 `RedundantModifier` 即可。

### P0-2：Checkstyle「BigDecimal」IllegalType 规则过宽，且与规范自相矛盾

`docs/checkstyle.xml` 第 361-366 行：

```xml
<module name="IllegalType">
    <property name="illegalClassNames" value="java.math.BigDecimal"/>
    <message ... value="禁止 new BigDecimal(double/Float) 构造方法..."/>
</module>
```

`IllegalType` 检查的是**类型使用**（变量声明 / 方法返回类型 / 参数），**并非构造函数**。因此它会拦截所有 `BigDecimal` 返回类型 / 变量，例如：

```java
// JsonParser.java:392 —— 返回 BigDecimal 是规范 6.3 明确鼓励的做法，却被判违规
public BigDecimal getDecimalValue() { ... }
```

这与规范 6.3「禁止 double/float，一律使用 BigDecimal」**直接冲突**。全项目共 **92** 条此类误报。

**修复**：删除该 `IllegalType` 模块。真正要拦的 `new BigDecimal(0.1)` 已由第 17 节（548-554 行）`RegexpSinglelineJava` 正确覆盖。

### P0-3：Enforcer `bannedImports` 规则无法加载，`mvn validate` 独立失败

`pom.xml` 中 `maven-enforcer-plugin` 的 `enforce-json-ecosystem` 执行引用了 `bannedImports` 规则（依赖 `extra-enforcer-rules`）。实测运行报错：

```
Failed to create enforcer rules with name: bannedImports
or for class: org.apache.maven.plugins.enforcer.BannedImports
```

排查发现：本地仓库 `extra-enforcer-rules-1.8.0.jar`（52 KB）**仅含 19 个类**，缺少 `BannedImports`、`BanDuplicatePomDependencyVersions` 等规则类，疑似依赖下载不完整/损坏。

**修复**：
1. 删除本地 `~/.m2`（本项目为 `D:\Maven\njydsz-repo`）下的 `org/codehaus/mojo/extra-enforcer-rules/1.8.0` 目录，重新下载完整 jar；
2. 或确认 `extra-enforcer-rules` 版本与 `maven-enforcer-plugin 3.5.0` 兼容性，必要时升级到 1.9.0。

### P0 影响面

由于 `maven-checkstyle-plugin` 通过 `check-json-ecosystem` 执行绑定到 `validate` 阶段且 `failOnViolation=true`，当前 `mvn validate` / `compile` / `package` / `verify` **在第一个有源码模块（ydsz-common-json）即失败**。修复上述 3 处后，构建门禁才能恢复。

---

## 三、P1 —— 真实违规分布（剔除误报后 ≈15,900 条）

全模块违规类型分布（去重后近似值）：

| 规则 | 数量 | 对应规范 | 自动化程度 |
|------|-----|---------|-----------|
| JavadocMethod（public 方法缺 @param/@return） | ~3,823 | 10.2 | 手动补写 |
| MagicNumber（魔法值未定义常量） | ~1,846 | 3.1 | 手动/半自动 |
| NeedBraces（if/for 缺大括号） | ~810 | 4.1 | 手动（低风险） |
| OneStatementPerLine（一行多句） | ~192 | 4.4 | 手动 |
| LeftCurly（左大括号换行） | ~61（json 模块） | 4.1 | spotless 可修 |
| CyclomaticComplexity（>10） | 最高 47 | 复杂度 | 需重构 |
| CustomImportOrder（import 分组） | ~43（json） | 5.3 | spotless 可修 |
| FQN 行内全限定名 | ~24（json） | 5.1 | 手动 |
| ThreadLocal 未显式 remove 提醒 | ~10（json） | 15.1 | 核验 |
| IllegalType / MethodLength / UnusedImports / RightCurly / NestedIfDepth 等 | 少量 | — | 混合 |

**违规 TOP 15 模块**（重灾区集中在 `*-server` 应用服务层与自研引擎）：

| 模块 | 违规数 |
|------|-------|
| ydsz-literule-server | 2,374 |
| ydsz-common-json | 1,457 |
| ydsz-cronjob-server | 1,203 |
| ydsz-common-excel | 1,198 |
| ydsz-message-server | 1,034 |
| ydsz-common-safe | 940 |
| ydsz-common-notify | 742 |
| ydsz-common-file | 687 |
| ydsz-agent-server | 601 |
| ydsz-common-auth | 569 |

> 规律：`*-server` 层因 public 方法多、业务分支多，Javadoc + 魔法值 + 圈复杂度三类违规集中；`ydsz-common-json` 因是手写高性能 JSON 引擎（字符级解析），魔法值（ASCII 码）、圈复杂度（解析器 47）、无大括号热路径天然密集。

---

## 四、规范符合度正面结论（做得好的地方）

以下"结构性/红线"规范经抽查**合规度良好**，无需返工：

1. **DDD 五层架构**：8 大业务模块（system/userinfo/workflow/literule/message/cronjob/nextwiki/agent）均严格拆分为 `api / domain / infra / server / web` 五层，依赖方向单向收敛。
2. **JSON 生态红线**：业务代码 0 处 `import com.alibaba.fastjson.*`；`com.fasterxml.jackson` 仅出现在 `ydsz-common-json` 的 **test** 目录（benchmark 对比基准，属允许范围）。
3. **SimpleDateFormat 红线**：全项目 0 处实际使用，仅 3 处 Javadoc 注释提及（用于说明 DateTimeFormatter 线程安全）。
4. **System.out 红线**：业务代码无泄漏，仅 `ConfigCliTool`（CLI 命令行工具）与 Javadoc 示例使用，属合理豁免。
5. **printStackTrace 红线**：无 `e.printStackTrace()`（空参）泄漏；仅 2 处 `printStackTrace(PrintWriter)` 用于把堆栈捕获进日志（正确用法）。
6. **命名规范**：类/方法/变量/常量驼峰与 `UPPER_SNAKE_CASE` 抽查通过，无拼音命名。

---

## 五、下一步优化建议（分阶段路线图）

### S1（立即，半天内）：修复工具链，恢复构建门禁

1. 删除 checkstyle.xml 规则 18（接口修饰符正则）→ 消除 6,432 条误报。
2. 删除 checkstyle.xml 规则 6（BigDecimal IllegalType）→ 消除 92 条误报、解除与规范 6.3 的矛盾。
3. 修复 Enforcer `extra-enforcer-rules` 依赖（重新下载或升级 1.9.0）。
4. 修正 checkstyle.xml 头注释「对齐 v2.14」→ 对齐 v2.15（版本号已漂移）。
5. 复跑 `mvn checkstyle:check` 确认误报清零、`mvn validate` 通过。

### S2（1-2 周）：清理"硬伤"类违规（安全/可维护性相关）

按风险优先级，先修以下高价值项（数量可控、收益明显）：

1. **FQN 行内全限定名**（5.1）——全项目约几十处，逐文件 `import` 化。
2. **CyclomaticComplexity > 10**——重点重构 `ydsz-common-json` 解析器（复杂度 47、21、18）、各 `*-server` 编排方法，拆分为策略/子方法。
3. **NeedBraces / OneStatementPerLine**——低风险机械修复，可脚本批量 + 人工 review 热路径。
4. **MagicNumber**——按规范 3.3「按功能归类常量/枚举」，先清理业务模块，JSON 引擎的 ASCII 码可统一用 `CharCodes` 常量类收敛。
5. **自建线程池核验**——`common-audit`（`AuditAutoConfiguration` 的 `new ThreadPoolTaskExecutor`）、`common-cache`（`CacheThreadPoolManager`）、`common-event`（`OutboxProcessor`）、`agent-web`（`ScheduledThreadPoolExecutor`）需评估是否迁到 `ydsz-common-thread` 配置驱动，或补 `CHECKSTYLE.OFF: ThreadPoolCreate` 豁免注释。

### S3（2-4 周）：偿还文档/格式类债务（可自动化优先）

1. **import 排序 + 未用 import + 行宽**：直接 `mvn spotless:apply` 一键修复（LineLength、UnusedImports、CustomImportOrder 大部分）。
2. **Javadoc 补齐**：优先给 `*-api` 层（对外 Feign 契约）与 `*-web` Controller 补全；内部实现类可先放宽为"复杂逻辑补注释"。
3. **LeftCurly / WhitespaceAround / RightCurly**：spotless googleJavaFormat 可覆盖大部分。

### S4（持续）：把规范落地为 CI 门禁闭环

1. CI 接入 `mvn validate`（checkstyle + enforcer）作为 MR 强制门禁，新增代码零违规。
2. 引入**增量检查**（对 diff 行而非全库），避免存量债务阻塞迭代；存量违规用 `// CHECKSTYLE.OFF` 或基线文件白名单管理，分批消债。
3. 在 checkstyle.xml 补 `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@RequestMapping` 到 `JavadocMethod.allowedAnnotations`，避免 Controller 端点被误判（如确认需给端点写 Javadoc则保留）。
4. 将「附录 B Pre-PR Checklist」中"Checkstyle 通过"前置条件真正纳入 PR 机器人校验，而非仅文档约定。

---

## 六、附录：本次检查命令与产物

```bash
# 全模块 Checkstyle（fail-never，采集完整违规）
mvn checkstyle:check -fn -DskipTests

# Enforcer 依赖治理
mvn -pl <module> validate -Dcheckstyle.skip=true

# 一键格式化（S3 使用）
mvn spotless:apply
```

完整违规明细见：`checkstyle-full.log`（本次检查生成，可删除）。
