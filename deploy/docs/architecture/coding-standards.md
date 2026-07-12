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
| 4 | **`.class` 字面量** | `com.njydsz.pmis.common.exception.BizException.class` | `BizException.class` |
| 5 | **注解** | `@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(...)` | `@ConditionalOnBean(...)` |
| 6 | **静态方法调用** | `org.junit.jupiter.api.Assertions.assertThrows(...)` | `import static ...assertThrows;` → `assertThrows(...)` |
| 7 | **instanceof 检查** | `x instanceof com.njydsz.pmis.common.exception.BizException` | `x instanceof BizException` |
| 8 | **泛型类型参数** | `new LambdaQueryWrapper<com.njydsz.pmis.cronjob.domain.entity.job.JobDO>()` | `new LambdaQueryWrapper<JobDO>()` |
| 9 | **for-each 变量类型** | `for (com.njydsz.pmis.literule.api.Rule rule : ...)` | `for (Rule rule : ...)` |

### 1.3 例外

1. **字符串字面量**中的 FQN（如反射类名 `"com.njydsz.pmis.literule.core.MicrometerRuleMetrics"`）可保留完整路径。
2. **Javadoc `{@link FQN}` / `@throws FQN` 引用**可保留完整路径，但推荐在已 import 的情况下使用简单类名。
3. **同名类冲突**（Java 语言限制）：当当前类与目标类简单名相同（如 `com.njydsz.pmis.cronjob.server.core.dag.DagEdge` 与 `com.njydsz.pmis.common.dag.DagEdge`），Java 不允许同时 import 两个同名类，此时对其中一个使用 FQN 是合法的。此类 FQN 必须在行尾添加 `// FQN-OK: name conflict with <ClassName>` 注释说明原因。

### 1.4 执行机制

1. **IDE 规则**：`.trae/rules/no-inline-fqn.md` 设置 `alwaysApply: true`，AI 代码生成阶段自动遵守。
2. **Code Review**：PR 审查必须检查行内 FQN，发现即打回。
3. **CI 检测（可选）**：可在 CI 流水线中加入检测脚本：

```bash
#!/bin/bash
# deploy/scripts/check-inline-fqn.sh — 检测行内 FQN 违规
# 用法: check-inline-fqn.sh <src-dir>
# 排除 import 行和字符串字面量，检测代码行中的 FQN 用法

SRC_DIR="${1:-ydsz-pmis-backend}"
VIOLATIONS=0

# 匹配非 import 行、非 package 行、非注释行中的 com.njydsz.pmis.xxx.YyyClass 模式
grep -rn --include='*.java' \
  -E '^\s+[^/*]*com\.njydsz\.pmis\.[a-z]+\.[a-z]+(\.[a-z]+)*\.[A-Z][a-zA-Z0-9_]*' \
  "$SRC_DIR" \
  | grep -v '^\s*//' \
  | grep -v '@link' \
  | grep -v '@code' \
  | grep -v '@throws' \
  | grep -v '{@' \
  | while read -r line; do
      echo "❌ INLINE FQN: $line"
      VIOLATIONS=$((VIOLATIONS + 1))
    done

echo "检测完成。"
```

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
