---
alwaysApply: true
scene: code
---

# 禁止行内全限定类名（FQN）用法

> **公司代码规范（强制）** — 适用于所有 Java 源文件，不可豁免。

代码中不允许出现行内 FQN（Fully Qualified Name）导入，必须使用标准 `import` 语句后在代码中直接引用简单类名。

## 违规案例（真实）

以下写法在本项目中出现过并被标记为违规，严禁再次出现：

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

// ❌ JobService.java / JobDagService.java 等 8 个文件 — Javadoc @throws 使用行内 FQN
* @throws com.njydsz.pmis.common.exception.SysException 当任务不存在时抛出

// ❌ IFileStorage.java / LogicalDeleteConfiguration.java 等 10 个文件 — Javadoc @see 使用行内 FQN
* @see com.njydsz.pmis.common.file.storage.platform.LocalStorage

// ❌ EnableAudit.java — @Import 注解使用行内 FQN
@Import(com.njydsz.pmis.common.audit.config.AuditAutoConfiguration.class)

// ❌ NotifyChannelStrategy.java — 方法参数使用行内 FQN
default void setTemplateEngine(com.njydsz.pmis.common.notify.template.TemplateEngine templateEngine) {
```

正确写法：
```java
import com.njydsz.pmis.project.api.dto.InitiationCreateDTO;
import com.njydsz.pmis.finance.domain.entity.ProfitSnapshot;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.literule.server.replay.ExecutionReplayService;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.file.storage.platform.LocalStorage;
import com.njydsz.pmis.common.audit.config.AuditAutoConfiguration;
import com.njydsz.pmis.common.notify.template.TemplateEngine;

// ✅ 使用简单类名
Result<String> create(@RequestBody InitiationCreateDTO dto);

wrapper.orderByDesc(ProfitSnapshot::getSnapshotAt);

jobMapper.selectCount(new LambdaQueryWrapper<JobDO>().eq(JobDO::getStatus, "NORMAL"));

new ExecutionReplayService(...);

// ✅ Javadoc @throws 使用简单类名
* @throws SysException 当任务不存在时抛出

// ✅ Javadoc @see 使用简单类名
* @see LocalStorage

// ✅ @Import 注解使用简单类名
@Import(AuditAutoConfiguration.class)

// ✅ 方法参数使用简单类名
default void setTemplateEngine(TemplateEngine templateEngine) {
```

## 覆盖范围

以下场景禁止使用行内 FQN，必须先 `import` 再用简单类名：

1. **类型引用**：变量声明、返回类型、参数类型、泛型类型参数
2. **`.class` 字面量**：如 `com.njydsz.pmis.common.exception.SysException.class` → `SysException.class`
3. **注解**：如 `@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(...)` → `@ConditionalOnBean(...)`
4. **静态方法调用**：如 `org.junit.jupiter.api.Assertions.assertThrows(...)` → `assertThrows(...)`（配合 `import static`）
5. **`new` 表达式**：如 `new com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator()` → `new QLExpressExpressionEvaluator()`
6. **`instanceof` 检查**：如 `x instanceof com.njydsz.pmis.common.exception.SysException` → `x instanceof SysException`
7. **Javadoc `@throws` 标签**：如 `@throws com.njydsz.pmis.common.exception.SysException` → `@throws SysException`
8. **Javadoc `@see` 标签**：如 `@see com.njydsz.pmis.common.file.storage.platform.LocalStorage` → `@see LocalStorage`
9. **Javadoc `@param` / `@return` 标签中的类型名**：同上，禁止 FQN，必须 import + 简单类名
10. **注解参数中的 `.class` 字面量**：如 `@Import(com.njydsz.pmis.common.audit.config.AuditAutoConfiguration.class)` → `@Import(AuditAutoConfiguration.class)`

## 例外

1. **字符串字面量**中的 FQN（如反射类名 `"com.njydsz.pmis.literule.core.MicrometerRuleMetrics"`）可保留完整路径。
2. **Javadoc `{@link FQN}` 引用**可保留完整路径（当目标类未在代码中使用、仅作 Javadoc 交叉引用时）。但如果该类已被 import，则必须使用简单类名 `{@link SimpleName}`。**注意：此例外仅适用于 `{@link}` 标签，不适用于 `@throws`、`@see`、`@param`、`@return` 等其他 Javadoc 标签。**
3. **同名类冲突**（Java 语言限制）：当当前类与目标类简单名相同（如 `com.njydsz.pmis.cronjob.server.core.dag.DagEdge` 与 `com.njydsz.pmis.common.dag.DagEdge`），Java 不允许同时 import 两个同名类，此时对其中一个使用 FQN 是合法的。此类 FQN 必须在行尾添加 `// FQN-OK: name conflict with <ClassName>` 注释说明原因。

## 通用示例

错误（禁止）：
```java
// .class 字面量
assertThatThrownBy(() -> service.execute("INVALID", ctx()))
    .isInstanceOf(com.njydsz.pmis.common.exception.SysException.class);

// 注解
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RuleConfigProvider.class)

// 静态方法调用
org.junit.jupiter.api.Assertions.assertThrows(SysException.class, () -> service.send(req));

// new 表达式
return new com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator();

// Javadoc @throws 使用 FQN（禁止）
* @throws com.njydsz.pmis.common.exception.SysException 当条件不满足时抛出

// Javadoc @see 使用 FQN（禁止）
* @see com.njydsz.pmis.common.jdbc.interceptor.LogicalDeleteInterceptor

// @Import 注解参数使用 FQN（禁止）
@Import(com.njydsz.pmis.common.audit.config.AuditAutoConfiguration.class)
```

正确：
```java
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.jdbc.interceptor.LogicalDeleteInterceptor;
import com.njydsz.pmis.common.audit.config.AuditAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator;

// .class 字面量
assertThatThrownBy(() -> service.execute("INVALID", ctx()))
    .isInstanceOf(SysException.class);

// 注解
@ConditionalOnBean(RuleConfigProvider.class)

// 静态方法调用
assertThrows(SysException.class, () -> service.send(req));

// new 表达式
return new QLExpressExpressionEvaluator();

// Javadoc @throws 使用简单类名（正确）
* @throws SysException 当条件不满足时抛出

// Javadoc @see 使用简单类名（正确）
* @see LogicalDeleteInterceptor

// @Import 注解参数使用简单类名（正确）
@Import(AuditAutoConfiguration.class)
```

## 执行机制

- **IDE 检查**：Trae / CatPaw 规则文件 `alwaysApply: true`，在代码生成阶段自动遵守。
- **Code Review**：PR 审查必须检查行内 FQN，发现即打回。
- **CI 可选**：可在 CI 流水线中加入 `grep` 检测脚本（见 `deploy/scripts/check-inline-fqn.sh`）。
