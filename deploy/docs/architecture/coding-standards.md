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

## 3. 脚本执行优先使用 Python 而非 PowerShell

**级别**：强制（P0）
**生效范围**：所有脚本执行场景（CI、Pre-commit、本地开发、Docker 构建、批量处理、代码生成等）
**规则文件**：[`.trae/rules/prefer-python-over-powershell.md`](../../../.trae/rules/prefer-python-over-powershell.md)

### 3.1 规则定义

在 ydsz-pmis 项目中执行脚本命令时（包括但不限于批量文件处理、文本替换、代码生成、数据转换、文件读写等），**必须优先使用 Python**，禁止使用 PowerShell。

### 3.2 原因

PowerShell 在处理文件编码时存在严重问题：

| # | 问题 | 说明 |
|---|------|------|
| 1 | **编码损坏** | PowerShell 默认使用 UTF-16 LE BOM 或系统 ANSI 编码读写文件，在处理 UTF-8 无 BOM 的源代码文件时，会将文件内容转换为乱码。 |
| 2 | **BOM 污染** | PowerShell 的 `Out-File`、`Set-Content` 等 cmdlet 默认添加 BOM 前缀，导致 Java 编译器、Git diff、Spotless 等工具出现兼容性问题。 |
| 3 | **转义陷阱** | PowerShell 的引号转义规则与正则表达式交互混乱，容易在文本替换中引入意外修改。 |
| 4 | **跨平台不一致** | Windows PowerShell 5.x 与 PowerShell 7+ 行为差异大，脚本可移植性差。 |

Python 的 `pathlib`、`io` 模块默认使用 UTF-8 编码，且 `encoding="utf-8"` 参数行为明确、跨平台一致，不会损坏源代码文件。

### 3.3 正确做法

```python
# 使用 Python 进行文件读写和文本替换
import pathlib

content = pathlib.Path("src/main/java/.../Example.java").read_text(encoding="utf-8")

new_content = content.replace("oldText", "newText")

pathlib.Path("src/main/java/.../Example.java").write_text(new_content, encoding="utf-8")
```

```python
# 使用 Python 批量处理多个文件
import pathlib

for f in pathlib.Path("ydsz-pmis-backend").rglob("*.java"):
    content = f.read_text(encoding="utf-8")
    if "oldText" in content:
        f.write_text(content.replace("oldText", "newText"), encoding="utf-8")
```

### 3.4 错误做法

```powershell
# ❌ PowerShell 会损坏文件编码
Get-ChildItem -Recurse -Filter "*.java" | ForEach-Object {
    (Get-Content $_.FullName) -replace 'oldText', 'newText' | Set-Content $_.FullName
}

# ❌ Out-File 默认添加 BOM
"Some content" | Out-File -FilePath "example.txt"
```

### 3.5 执行机制

1. **IDE 规则**：`.trae/rules/prefer-python-over-powershell.md` 设置 `alwaysApply: true`，AI 代码生成阶段自动遵守。
2. **Code Review**：PR 审查中如发现由 PowerShell 脚本引入的编码损坏或 BOM 污染，即打回。
3. **CI 检测（推荐）**：CI 流水线中可加入 `deploy/scripts/check-bom.ps1`（已存在 PowerShell 版，建议重写为 Python 版），拒绝含 BOM 的源代码文件。
4. **新脚本约束**：所有新增的脚本工具（位于 `deploy/scripts/`、`scripts/` 等）默认使用 Python 实现。**既有 `.ps1` 脚本逐步迁移到 `.py`，迁移完成前可保留作为 Windows 兼容入口**。

### 3.6 例外

仅以下场景允许使用 PowerShell：

1. Windows 平台特定的运维命令（如 Windows Service 管理、注册表操作）。
2. 调用 PowerShell 特有的 .NET 绑定 API（如 `[System.Reflection.Assembly]`）。
3. 用户交互密集且仅限 PowerShell 终端的命令（如 Azure / M365 管理）。

> **注意**：即便在例外场景下，所有涉及文件读写、文本处理的操作也必须通过 Python 包装执行，严禁在 PowerShell 中直接 `Get-Content` / `Set-Content` 处理源代码文件。

---

## 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-07-12 | 1.0 | 初始创建，收录「禁止行内 FQN」规范 | ydsz-pmis-team |
| 2026-07-13 | 1.1 | 修复例外描述矛盾：@throws/@see/@param/@return 中的 FQN 均属违规（仅 {@link} 可保留）；新增 @ConditionalOnClass 例外；更新执行机制（CI 强制） | ydsz-pmis-team |
| 2026-07-13 | 1.2 | 新增「禁止使用 @SuppressWarnings 注解」规范（Section 2） | ydsz-pmis-team |
| 2026-07-14 | 1.3 | 新增「脚本执行优先使用 Python 而非 PowerShell」规范（Section 3） | ydsz-pmis-team |
