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
4. **Checkstyle（辅助）**：`checkstyle.xml` 已配置 `IllegalImport` 等规则，与 shell 脚本形成双重防线。
5. **Spotless（自动修复）**：引入 Spotless + Google Java Format 插件，`mvn spotless:apply` 可自动将行内 FQN 转为 import 语句。

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

## 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-07-12 | 1.0 | 初始创建，收录「禁止行内 FQN」规范 | ydsz-pmis-team |
| 2026-07-13 | 1.1 | 修复例外描述矛盾：@throws/@see/@param/@return 中的 FQN 均属违规（仅 {@link} 可保留）；新增 @ConditionalOnClass 例外；更新执行机制（CI 强制 + Spotless 自动修复） | ydsz-pmis-team |
