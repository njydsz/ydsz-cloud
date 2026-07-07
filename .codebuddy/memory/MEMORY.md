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

### 已知代码缺陷（待修复）
- DecisionTableRule.java 第231行: `!=null`条件匹配bug (`&& false`导致永不匹配)
- ChainGraphConverter.java: WHEN边类型未区分(第128行)、CHAIN/GROUP反向解析未实现(第530行)、IF/SWITCH条件还原为启发式(第417/445行)
- RuleChain.java: transient字段线程安全隐患
- ScriptRule.java: 第三层超时防御注释提及但未实现(第307行)

### 核心短板（对标竞品）
- 缺RETE算法(线性扫描)、缺DSL文本语法、脚本沙箱不完整、多数据源支持不足(仅DB)、缺表达式级追踪/归因、缺交叉决策表/复杂评分卡
