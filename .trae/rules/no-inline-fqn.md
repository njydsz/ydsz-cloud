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
```

正确写法：
```java
import com.njydsz.pmis.project.api.dto.InitiationCreateDTO;
import com.njydsz.pmis.finance.domain.entity.ProfitSnapshot;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.literule.server.replay.ExecutionReplayService;

// ✅ 使用简单类名
Result<String> create(@RequestBody InitiationCreateDTO dto);

wrapper.orderByDesc(ProfitSnapshot::getSnapshotAt);

jobMapper.selectCount(new LambdaQueryWrapper<JobDO>().eq(JobDO::getStatus, "NORMAL"));

new ExecutionReplayService(...);
```

## 覆盖范围

以下场景禁止使用行内 FQN，必须先 `import` 再用简单类名：

1. **类型引用**：变量声明、返回类型、参数类型、泛型类型参数
2. **`.class` 字面量**：如 `com.njydsz.pmis.common.exception.BizException.class` → `BizException.class`
3. **注解**：如 `@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(...)` → `@ConditionalOnBean(...)`
4. **静态方法调用**：如 `org.junit.jupiter.api.Assertions.assertThrows(...)` → `assertThrows(...)`（配合 `import static`）
5. **`new` 表达式**：如 `new com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator()` → `new QLExpressExpressionEvaluator()`
6. **`instanceof` 检查**：如 `x instanceof com.njydsz.pmis.common.exception.BizException` → `x instanceof BizException`

## 例外

1. **字符串字面量**中的 FQN（如反射类名 `"com.njydsz.pmis.literule.core.MicrometerRuleMetrics"`）可保留完整路径。
2. **Javadoc `{@link FQN}` / `@throws FQN` 引用**可保留完整路径，但推荐在已 import 的情况下使用简单类名。
3. **同名类冲突**（Java 语言限制）：当当前类与目标类简单名相同（如 `com.njydsz.pmis.cronjob.server.core.dag.DagEdge` 与 `com.njydsz.pmis.common.dag.DagEdge`），Java 不允许同时 import 两个同名类，此时对其中一个使用 FQN 是合法的。此类 FQN 必须在行尾添加 `// FQN-OK: name conflict with <ClassName>` 注释说明原因。

## 通用示例

错误（禁止）：
```java
// .class 字面量
assertThatThrownBy(() -> service.execute("INVALID", ctx()))
    .isInstanceOf(com.njydsz.pmis.common.exception.BizException.class);

// 注解
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RuleConfigProvider.class)

// 静态方法调用
org.junit.jupiter.api.Assertions.assertThrows(BizException.class, () -> service.send(req));

// new 表达式
return new com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator();
```

正确：
```java
import com.njydsz.pmis.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator;

// .class 字面量
assertThatThrownBy(() -> service.execute("INVALID", ctx()))
    .isInstanceOf(BizException.class);

// 注解
@ConditionalOnBean(RuleConfigProvider.class)

// 静态方法调用
assertThrows(BizException.class, () -> service.send(req));

// new 表达式
return new QLExpressExpressionEvaluator();
```

## 执行机制

- **IDE 检查**：Trae / CatPaw 规则文件 `alwaysApply: true`，在代码生成阶段自动遵守。
- **Code Review**：PR 审查必须检查行内 FQN，发现即打回。
- **CI 可选**：可在 CI 流水线中加入 `grep` 检测脚本（见 `deploy/scripts/check-inline-fqn.sh`）。
