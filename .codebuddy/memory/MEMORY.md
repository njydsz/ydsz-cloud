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
  - `ReAuthService.java` / `JobService.java` / `JobDagService.java` 等 8 个文件中 Javadoc `@throws com.njydsz.pmis.common.exception.SysException` 违规，已修复为 import + `@throws SysException`。
  - `IFileStorage.java` / `LogicalDeleteConfiguration.java` 等 10 个文件中 Javadoc `@see com.njydsz.pmis.xxx.XxxClass` 违规，已修复为 import + `@see XxxClass`。
  - `EnableAudit.java` 中 `@Import(com.njydsz.pmis.common.audit.config.AuditAutoConfiguration.class)` 违规，已修复为 import + `@Import(AuditAutoConfiguration.class)`。
  - `NotifyChannelStrategy.java` 中方法参数 `com.njydsz.pmis.common.notify.template.TemplateEngine templateEngine` 违规，已修复为 import + `TemplateEngine templateEngine`。
- **覆盖范围**：类型引用、`.class` 字面量、注解（含 `@Import` 等注解参数）、静态方法调用、`new` 表达式、`instanceof` 检查、方法引用（`::`）、Javadoc `@throws`/`@see`/`@param`/`@return` 标签中的类型名。
- **唯一例外**：字符串字面量中的 FQN（如反射类名）、Javadoc `{@link FQN}` 引用（仅 `{@link}` 标签，不含 `@throws`/`@see` 等）可保留完整路径。但如果该类已被 import，则必须使用简单类名。
- **规则文件**：`.trae/rules/no-inline-fqn.md`（`alwaysApply: true`）。
- **详细文档**：`deploy/docs/architecture/coding-standards.md`。
