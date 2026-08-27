# Flyway 引入评估报告

## 1. 评估背景

本报告评估在 ydzs-cloud 项目中引入 Flyway 数据库迁移框架的必要性和可行性。

## 2. 现状分析

### 2.1 当前数据库管理方式

| 维度 | 现状 |
|------|------|
| ORM 框架 | MyBatis-Plus 3.5.16 |
| 数据库 | PostgreSQL 42.7.4 |
| 连接池 | Druid 1.2.28 |
| 数据源 | 动态数据源（baomidou dynamic-datasource 4.3.1） |
| Schema 管理 | 手动 SQL 脚本（项目规范禁止 Flyway/Liquibase） |

### 2.2 项目规范约束

根据 `pom.xml` 第 56 行注释：

```
<!-- 项目规范禁止 Flyway / Liquibase（见 deploy/sql/README.md §1），不引入任何 schema-migration 框架 -->
```

**结论：项目明确禁止引入 Flyway/Liquibase 等自动化 Schema 迁移框架。**

## 3. Flyway 适用场景分析

### 3.1 Flyway 核心价值

Flyway 是一款数据库迁移管理工具，核心功能包括：
- 版本化数据库 Schema 变更（V__*.sql）
- 可重复迁移（R__*.sql）
- 迁移历史记录（flyway_schema_history）
- 多环境一致性保障

### 3.2 本项目不适用 Flyway 的原因

| 原因 | 说明 |
|------|------|
| 架构决策 | 项目采用 MyBatis-Plus，Schema 变更通过 DDL 手动管理 |
| 多租户设计 | 动态数据源场景下，Flyway 需要为每个租户单独配置迁移 |
| DDD 分层 | 数据库 Schema 属于基础设施层，变更应由 DBA 统一管理 |
| 规范约束 | 项目规范已明确禁止引入 schema-migration 框架 |

## 4. 替代方案建议

### 4.1 推荐的数据库变更管理方式

```
┌─────────────────────────────────────────────────────────────┐
│                    数据库变更管理流程                         │
├─────────────────────────────────────────────────────────────┤
│  1. DDL 脚本编写 → deploy/sql/V{version}__{description}.sql │
│  2. Code Review → 至少 2 人审核                             │
│  3. 测试环境验证 → 自动化测试通过                            │
│  4. DBA 审核 → 性能影响评估                                 │
│  5. 生产执行 → 维护窗口期执行                               │
│  6. 回滚方案 → 准备回滚脚本                                 │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 目录结构建议

```
deploy/
└── sql/
    ├── README.md                    # 变更规范说明
    ├── V1.0.0__initial_schema.sql   # 初始建表
    ├── V1.1.0__add_agent_tables.sql # Agent 模块表
    ├── V1.2.0__add_trigger_tables.sql # 触发器表
    └── rollback/
        ├── V1.1.0__rollback.sql     # 回滚脚本
        └── V1.2.0__rollback.sql
```

### 4.3 命名规范

- 版本迁移：`V{major}.{minor}.{patch}__{description}.sql`
- 可重复迁移：`R__{description}.sql`
- 回滚脚本：`V{version}__rollback.sql`

## 5. 新增模块的 DDL 管理

### 5.1 本次新增模块涉及的数据持久化

| 模块 | 存储方式 | 是否需要建表 |
|------|----------|--------------|
| RuntimeSession | InMemory（预留 Redis） | 否（内存实现） |
| MemoryExtractedFact | 待实现 | 是（未来） |
| AgentTrigger | InMemory（预留 DB） | 否（内存实现） |
| TeamRun | InMemory（预留 DB） | 否（内存实现） |
| SkillLesson | InMemory（预留 DB） | 否（内存实现） |
| Channel统计 | 内存 + 日志 | 否 |

### 5.2 未来建表建议

当内存实现需要替换为数据库存储时，按以下流程执行：

1. 编写 DDL 脚本：`deploy/sql/V1.3.0__add_agent_runtime_tables.sql`
2. 提交 DBA 审核
3. 测试环境验证
4. 生产环境执行

## 6. 结论

### 6.1 评估结论

| 评估项 | 结论 |
|--------|------|
| 是否引入 Flyway | **否**（项目规范禁止） |
| 数据库变更管理方式 | 手动 SQL 脚本 + DBA 审核 |
| 新增模块存储方式 | 内存实现（预留数据库扩展接口） |
| 未来扩展建议 | 按需编写版本化 DDL 脚本 |

### 6.2 行动项

| 序号 | 行动项 | 优先级 | 负责人 |
|------|--------|--------|--------|
| 1 | 创建 `deploy/sql/` 目录结构 | P2 | 开发团队 |
| 2 | 编写数据库变更规范文档 | P2 | DBA |
| 3 | 为新增模块预留数据库表设计 | P3 | 架构组 |

## 7. 参考

- 项目根 `pom.xml` 第 56 行：Flyway/Liquibase 禁止引入
- MyBatis-Plus 官方文档：https://baomidou.com/
- Flyway 官方文档：https://flywaydb.org/documentation/
