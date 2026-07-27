# YDSZ-PMIS 编码规范

> 本文档定义 YDSZ-PMIS 项目的强制编码规范，所有贡献者必须遵守。

---

## Section 1: 实体类命名规范

### 1.1 规则

数据库实体类（Entity）**不以 `DO` 为后缀**，直接使用业务名称作为类名。

| 层次 | 命名规则 | 示例 |
|------|----------|------|
| Entity（数据库实体） | `Xxx`（无后缀） | `UserAccount`、`Role`、`FlowDefinition`、`Job` |
| VO（视图对象） | `XxxVO`（保留后缀） | `UserAccountVO`、`RoleVO` |
| DTO（数据传输对象） | `XxxDTO`（保留后缀） | `InitiationCreateDTO`、`UserAccountDTO` |

### 1.2 基类命名

| 旧名称 | 新名称 | 说明 |
|--------|--------|------|
| `BaseDO` | `Base` | String 主键数据库实体基类 |
| `BaseLongDO` | `BaseLong` | Long 主键数据库实体基类 |
| `LogBaseDO` | `LogBase` | 日志型实体基类 |

### 1.3 例外：命名冲突保留 DO 后缀

当移除 `DO` 后缀后与同模块中已有的领域模型/API 类同名时，**保留 `DO` 后缀**作为消歧义手段。
这符合 DDD 分层架构中持久层实体与领域模型共存的模式。

| DO 类名 | 保留原因 | 冲突类 |
|---------|----------|--------|
| `AgentDefinitionDO` | 与领域值对象同名 | `com.njydsz.agent.domain.agent.AgentDefinition` |
| `RuleDefinitionDO` | 与 API 模型同名 | `com.njydsz.literule.api.RuleDefinition` |
| `RuleExecutionTraceDO` | 与 API 模型同名 | `com.njydsz.literule.api.RuleExecutionTrace` |
| `RulePackDO` | 与 API 模型同名 | `com.njydsz.literule.api.RulePack` |
| `RuleChainGraphDO` | 与 Server 编排模型同名 | `com.njydsz.literule.server.orchestrator.RuleChainGraph` |
| `RuleTestCaseDO` | 与 Server 测试模型同名 | `com.njydsz.literule.server.testing.RuleTestCase` |

### 1.4 MyBatis-Plus 注意事项

- `@TableName("xxx")` 注解的表名**不变**，仅类名变化
- Mapper XML 中 `resultType` / `type` 属性的 FQN 需同步更新
- `mybatis-plus.type-aliases-package` 配置引用的是包路径，不受类名变更影响

### 1.5 变量命名

- 实体类型变量名使用 camelCase：`UserAccount userAccount = ...`
- **不要求**变量名也移除 DO 后缀（如 `userAccountDO` → `userAccount`），但建议新代码遵循无 DO 后缀的命名

---

## Section 2: 禁止行内全限定类名（FQN）

Java 代码中不允许出现行内 FQN 用法，必须使用标准 `import` 语句后在代码中直接引用简单类名。

- **覆盖范围**：类型引用、`.class` 字面量、注解参数、静态方法调用、`new` 表达式、`instanceof` 检查、方法引用、Javadoc `@throws`/`@see`/`@param`/`@return` 标签
- **唯一例外**：字符串字面量中的 FQN、Javadoc `{@link FQN}` 引用（但已 import 的类必须用简单类名）
- **同名类冲突**：使用 FQN 并添加 `// FQN-OK: name conflict with <ClassName>` 注释
- **规则文件**：`.trae/rules/no-inline-fqn.md`（`alwaysApply: true`）
- **检测脚本**：`deploy/scripts/check-inline-fqn.sh`
- **工程化防线**：IDE 检查 → Pre-commit Hook → Checkstyle(severity=error) → Spotless → CI 流水线

---

## Section 3: 禁止使用 @SuppressWarnings 注解

Java 代码中不允许出现 `@SuppressWarnings` 注解。所有警告必须从根源修复而非压制。

- **常见修复**：`unchecked`→泛型方法签名、`unused`→删除死代码、`rawtypes`→指定泛型参数、`deprecation`→迁移新 API
- **规则文件**：`.trae/rules/no-inline-fqn.md`（与 FQN 规则同一文件）
- **检测脚本**：`deploy/scripts/check-inline-fqn.sh` 同时检测 `@SuppressWarnings`

---

## Section 4: 脚本执行优先使用 Python

在 ydsz 项目中执行脚本命令时（批量文件处理、文本替换、代码生成等），**必须优先使用 Python**，禁止使用 PowerShell。

- **原因**：PowerShell 编码损坏（UTF-16 LE BOM）、BOM 污染、转义陷阱、跨平台不一致
- **正确做法**：使用 Python `pathlib`、`io` 模块，固定 `encoding="utf-8"`
- **规则文件**：`.trae/rules/prefer-python-over-powershell.md`（`alwaysApply: true`）

---

## Section 5: 忽略单元测试覆盖率检查

YDSZ 项目全局禁用 JaCoCo 单元测试覆盖率采集和阈值检查。项目已移除全部单元测试代码。

- **配置**：`ydsz-backend/pom.xml` 中 `<skipJacoco>true</skipJacoco>`
- **临时启用**：`mvn verify -DskipJacoco=false -DskipTests=false`
- **CI 影响**：CI 流水线仅执行 `mvn compile -DskipTests`，不涉及 verify 阶段
