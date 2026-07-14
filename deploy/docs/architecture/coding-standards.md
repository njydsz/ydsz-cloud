# ydsz-pmis 编码规范

> 本文档收录 ydsz-pmis 项目强制遵守的公司代码规范。所有提交代码（含 AI 生成代码）必须符合以下规则。

---

## 1. 禁止行内全限定类名（FQN）

**级别**：强制（P0）  
**生效范围**：所有 Java 源文件（`src/main/java`、`src/test/java`）  
**规则文件**：[`.trae/rules/no-inline-fqn.md`](../../../.trae/rules/no-inline-fqn.md)

### 1.1 规则定义

代码中不允许出现行内 FQN（Fully Qualified Name）用法。所有类型引用必须通过标准 `import` 语句导入后，在代码中使用简单类名。

### 1.2 覆盖范围

以下场景禁止使用行内 FQN，必须先 `import` 再用简单类名：

| # | 场景 | 违规示例 | 正确写法 |
|---|------|----------|----------|
| 1 | **参数类型** | `create(@RequestBody com.njydsz.pmis.project.api.dto.InitiationCreateDTO dto)` | `import ...InitiationCreateDTO;` → `create(@RequestBody InitiationCreateDTO dto)` |
| 2 | **方法引用** | `wrapper.eq(com.njydsz.pmis.cronjob.domain.entity.job.JobDO::getStatus, "NORMAL")` | `import ...JobDO;` → `wrapper.eq(JobDO::getStatus, "NORMAL")` |
| 3 | **new 表达式** | `new com.njydsz.pmis.literule.server.replay.ExecutionReplayService(...)` | `import ...ExecutionReplayService;` → `new ExecutionReplayService(...)` |
| 4 | **`.class` 字面量** | `com.njydsz.pmis.common.exception.SysException.class` | `SysException.class` |
| 5 | **注解** | `@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(...)` | `@ConditionalOnBean(...)` |
| 6 | **静态方法调用** | `org.junit.jupiter.api.Assertions.assertThrows(...)` | `import static ...assertThrows;` → `assertThrows(...)` |
| 7 | **instanceof 检查** | `x instanceof com.njydsz.pmis.common.exception.SysException` | `x instanceof SysException` |
| 8 | **泛型类型参数** | `new LambdaQueryWrapper<com.njydsz.pmis.cronjob.domain.entity.job.JobDO>()` | `new LambdaQueryWrapper<JobDO>()` |
| 9 | **for-each 变量类型** | `for (com.njydsz.pmis.literule.api.Rule rule : ...)` | `for (Rule rule : ...)` |

### 1.3 例外

1. **字符串字面量**中的 FQN（如反射类名 `"com.njydsz.pmis.literule.core.MicrometerRuleMetrics"`）可保留完整路径。
2. **Javadoc `{@link FQN}` 引用**（仅 `{@link}` 标签）可保留完整路径（当目标类未在代码中使用、仅作 Javadoc 交叉引用时）。但如果该类已被 import，则必须使用简单类名 `{@link SimpleName}`。**注意：此例外仅适用于 `{@link}` 标签，不适用于 `@throws`、`@see`、`@param`、`@return` 等其他 Javadoc 标签——这些标签中的 FQN 均属违规。**
3. **同名类冲突**（Java 语言限制）：当当前类与目标类简单名相同（如 `com.njydsz.pmis.cronjob.server.core.dag.DagEdge` 与 `com.njydsz.pmis.common.dag.DagEdge`），Java 不允许同时 import 两个同名类，此时对其中一个使用 FQN 是合法的。此类 FQN 必须在行尾添加 `// FQN-OK: name conflict with <ClassName>` 注释说明原因。
4. **`@ConditionalOnClass(name = "FQN")` 注解**：Spring 的 `@ConditionalOnClass` 的 `name` 参数是字符串类型，属于字符串字面量例外，不算违规。

### 1.4 执行机制

1. **IDE 规则**：`.trae/rules/no-inline-fqn.md` 设置 `alwaysApply: true`，AI 代码生成阶段自动遵守。
2. **Code Review**：PR 审查必须检查行内 FQN，发现即打回。
3. **CI 检测（强制）**：CI 流水线 `backend-ci.yml` 的 `build` job 中集成 `deploy/scripts/check-inline-fqn.sh --strict`，有违规即 `exit 1` 阻断 PR 合并。

### 1.5 真实违规案例

以下写法在本项目中出现过并被标记为违规：

```java
// ❌ InitiationFeignClient.java — 方法参数使用行内 FQN
Result<String> create(@RequestBody com.njydsz.pmis.project.api.dto.InitiationCreateDTO dto);

// ❌ InitiationFeignClientFallbackFactory.java — 方法参数使用行内 FQN
public Result<String> create(com.njydsz.pmis.project.api.dto.InitiationCreateDTO dto) {

// ❌ FinanceDataController.java — 方法引用使用行内 FQN
wrapper.orderByDesc(com.njydsz.pmis.finance.domain.entity.ProfitSnapshot::getSnapshotAt);

// ❌ JobStatsController.java — new + 泛型 + 方法引用全用行内 FQN
jobMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.njydsz.pmis.cronjob.domain.entity.job.JobDO>()
    .eq(com.njydsz.pmis.cronjob.domain.entity.job.JobDO::getStatus, "NORMAL"));

// ❌ LiteRuleAutoConfiguration.java — new 表达式使用行内 FQN
new com.njydsz.pmis.literule.server.replay.ExecutionReplayService(...);

// ❌ AdvancedReportServiceImpl.java — 注解 + 常量使用行内 FQN
@com.baomidou.dynamic.datasource.annotation.DS(com.njydsz.pmis.common.datasource.DataSourceConstants.SLAVE)
```

---

## 2. 禁止使用 @SuppressWarnings 注解

**级别**：强制（P0）  
**生效范围**：所有 Java 源文件（`src/main/java`、`src/test/java`）  
**规则文件**：[`.trae/rules/no-inline-fqn.md`](../../../.trae/rules/no-inline-fqn.md)

### 2.1 规则定义

代码中不允许出现 `@SuppressWarnings` 注解。该注解会压制编译器警告，掩盖潜在的类型安全、未使用代码、弃用 API 等问题，违反项目「零警告」原则。所有警告必须从根源修复，而非压制。

### 2.2 违规示例与正确写法

| # | 违规写法 | 正确写法 |
|---|----------|----------|
| 1 | `@SuppressWarnings("unchecked")` 后裸类型转换 | 使用 `TypeReference`、泛型方法签名、或重设计 API 避免 unchecked 转换 |
| 2 | `@SuppressWarnings("unused")` 标注未使用的方法/字段 | 删除死代码 |
| 3 | `@SuppressWarnings("rawtypes")` 使用裸泛型类型 | 始终指定泛型参数，如 `Map<String, Object>` |
| 4 | `@SuppressWarnings("deprecation")` 使用弃用 API | 迁移到推荐的新 API |
| 5 | `@SuppressWarnings("all")` 一刀切压制所有警告 | 逐个分析和修复每个警告 |

### 2.3 代码示例

```java
// ❌ 违规：压制 unchecked 警告
@SuppressWarnings("unchecked")
List<String> list = (List<String>) obj;

// ❌ 违规：压制 unused 警告
@SuppressWarnings("unused")
private void unusedMethod() { ... }

// ❌ 违规：同时压制多种警告
@SuppressWarnings({"unchecked", "rawtypes"})
Map map = (Map) obj;

// ✅ 正确：使用泛型安全转换
List<String> list = JSON.parseObject(json, new TypeReference<List<String>>() {});

// ✅ 正确：移除未使用的方法/字段
// （直接删除 unusedMethod）

// ✅ 正确：使用类型安全的集合
Map<String, Object> map = new HashMap<>();
```

### 2.4 执行机制

1. **IDE 规则**：`.trae/rules/no-inline-fqn.md` 设置 `alwaysApply: true`，AI 代码生成阶段自动遵守。
2. **Code Review**：PR 审查必须检查 `@SuppressWarnings`，发现即打回。
3. **CI 检测（强制）**：CI 流水线 `backend-ci.yml` 的 `build` job 中集成 `deploy/scripts/check-inline-fqn.sh --strict`，同时检测 `@SuppressWarnings` 违规，有违规即 `exit 1` 阻断 PR 合并。

---

## 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-07-12 | 1.0 | 初始创建，收录「禁止行内 FQN」规范 | ydsz-pmis-team |
| 2026-07-13 | 1.1 | 修复例外描述矛盾：@throws/@see/@param/@return 中的 FQN 均属违规（仅 {@link} 可保留）；新增 @ConditionalOnClass 例外；更新执行机制（CI 强制） | ydsz-pmis-team |
| 2026-07-13 | 1.2 | 新增「禁止使用 @SuppressWarnings 注解」规范（Section 2） | ydsz-pmis-team |
