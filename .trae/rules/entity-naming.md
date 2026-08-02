---
alwaysApply: true
---

# 实体类命名规范

数据库实体类（Entity）**不以 `DO` 为后缀**，直接使用业务名称作为类名。

## 规则

- Entity: `Xxx`（无后缀）— 例：`UserAccount`、`Role`、`FlowDefinition`、`Job`
- VO: `XxxVO`（保留后缀）— 例：`UserAccountVO`
- DTO: `XxxDTO`（保留后缀）— 例：`InitiationCreateDTO`

## 基类

- `BaseString`（原 `BaseDO`）— String 主键实体基类
- `BaseLong`（原 `BaseLongDO`）— Long 主键实体基类
- `LogBase`（原 `LogBaseDO`）— 日志型实体基类

## 例外

当移除 `DO` 后缀后与同模块已有类同名时，保留 `DO` 后缀：
- `AgentDefinitionDO`、`RuleDefinitionDO`、`RuleExecutionTraceDO`、`RulePackDO`、`RuleChainGraphDO`、`RuleTestCaseDO`

## 详细文档

`deploy/docs/architecture/coding-standards.md` Section 1
