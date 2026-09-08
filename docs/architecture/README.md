# 云顶架构决策记录（ADR）

> Architecture Decision Record（架构决策记录）是关于系统重大技术选择的持久化文档集合，记录每项关键决策的 Why、How 和 Trade-off，为长期维护提供技术债对照依据。

## 体系简介

ADR 体系用于：

* **固化决策上下文** — 记录每个重大技术选择当时面临的问题、驱动因素、备选方案和取舍
* **降低交接成本** — 新成员通过阅读 ADR 快速理解架构演进脉络而非猜测
* **版本可追踪** — 决策状态随时间演进（提议 → 接受 → 废弃/替代），历史可追溯
* **规范联动** — ADR 与云顶编码规范（YDIZ 系列）双向引用，确保决策落地到编码约束

## 如何编写新 ADR

### 步骤

1. **命名**：`docs/architecture/adr/ADR-{序号}-{短标题-kebab-case}.md`
2. **复制模板**：从 `docs/architecture/templates/adr-template.md` 复制
3. **填充内容**：
   - 状态、日期、决策者必须明确
   - 背景与问题陈述用「现象 + 挑战」描述，避免抽象
   - 驱动因素从性能/一致性/复杂度/团队/规范五个维度审视
   - 选项列出 ≥ 3 个可行方案，每个附简要描述
   - 决策结果一句话总结，并列出 ≥ 2 项正面 + ≥ 2 项负面后果
4. **规范对照**：在相关 YDIZ 规范条目下注明本决策引用关系
5. **PR 审批**：提交后需至少一位架构评审人 approve
6. **更新汇总表**：本文底部的 ADR 汇总表同步更新

### 状态定义

| 状态 | 含义 | 路径 |
|------|------|------|
| 提议 | 尚在设计阶段的初始状态 | 进入评审流程 |
| 接受 | 已通过评审并实施 | 持续维护 |
| 废弃 | 新决策推翻了本方案 | 由「替代」状态引用 |
| 替代 | 明确引用被替代的 ADR-xxx | 同时标记被替代 ADR 为废弃 |

### 模板位置

模板文件：`docs/architecture/templates/adr-template.md`（MADR 2.1.2 格式）

使用 Markdown 标题层级：
* H1（`#`）— ADR 标题
* H2（`##`）— 主章节（背景、驱动因素、选项、决策结果、对比、合规、参考）
* H3（`###`）— 选项子标题或正负面后果细分
* 表格 — 使用 GitHub Flavored Markdown 格式

## ADR 汇总表

| 编号 | 标题 | 状态 | 日期 |
|------|------|------|------|
| [ADR-001](./adr/ADR-001-ddd-layering.md) | DDD 分层架构选择 | 接受 | 2025-01-15 |
| [ADR-002](./adr/ADR-002-resilience4j.md) | Resilience4j 替代自研熔断 | 接受 | 2025-01-20 |
| [ADR-003](./adr/ADR-003-no-db-migration-tool.md) | 禁止 Flyway/Liquibase | 接受 | 2025-02-01 |
| [ADR-004](./adr/ADR-004-seata-mode.md) | Seata 分布式事务模式选择 | 接受 | 2025-02-10 |
| [ADR-005](./adr/ADR-005-multi-tenancy.md) | 多租户隔离策略 | 待决策 | 2025-02-15 |
| [ADR-006](./adr/ADR-006-observability.md) | 日志与可观测性标准化 | 部分接受 | 2025-02-20 |

## 目录结构

```
docs/architecture/
├── README.md              ← 本文件（体系简介 + 汇总表）
├── adr/                   ← 所有 ADR 文档
│   ├── ADR-001-ddd-layering.md
│   ├── ADR-002-resilience4j.md
│   ├── ADR-003-no-db-migration-tool.md
│   ├── ADR-004-seata-mode.md
│   ├── ADR-005-multi-tenancy.md
│   └── ADR-006-observability.md
└── templates/
    └── adr-template.md    ← MADR 2.1.2 格式模板
```

## 相关资源

* [云顶编码规范](../编码规范.md) — YDIZ 系列规范与 ADR 双向引用
* [云顶版本规范](../版本规范.md) — 版本兼容性约束
* [MADR 2.1.2 格式原文](https://adr.github.io/madr/)
