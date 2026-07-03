<!--
  ===========================================================================
  文件名: README.md
  路径:   docs/standards/README.md
  作用:   PMIS 工程规范总览入口，列出所有子规范文档及强制约束
  维护:   PMIS 架构组
  版本:   V1.0
  对标:   阿里 / 字节 / 美团 / 华为云 等互联网大厂工程标准
  ----------------------------------------------------------------------------
  阅读顺序:
    1. naming-convention.md  -> 命名先行
    2. git-workflow.md       -> 分支/提交流程
    3. backend-spec.md / frontend-spec.md -> 编码实现
    4. api-spec.md           -> 接口契约
    5. database-spec.md      -> 存储设计
    6. code-quality.md       -> 质量与安全门禁
    7. documentation.md      -> 文档交付
  ===========================================================================
-->

# 南京云顶 PMIS 开发规范总览

> 文档版本: V1.0 | 编制日期: 2026-06-30 | 最近更新: 2026-07-03
> 对标: 阿里 / 字节 / 美团 / 华为云 等互联网大厂工程标准
> 适用范围: PMIS 全栈（前端 / 后端 / 运维 / 测试）

本目录汇总 PMIS 项目第一阶段要求的全部工程规范，所有开发人员须 100% 遵循。
**任何脱离本规范的实现必须先提交 RFC 说明并经架构组批准。**

## 0. 规范索引

| # | 文档 | 路径 | 说明 | 重要程度 |
|---|------|------|------|----------|
| 1 | 编码与命名规范 | [naming-convention.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/naming-convention.md) | 标识符命名、目录结构、包/类/方法命名 | P0 |
| 2 | Git 工作流规范 | [git-workflow.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/git-workflow.md) | 分支模型、提交规范、Code Review、发布流程 | P0 |
| 3 | 前端工程规范 | [frontend-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/frontend-spec.md) | Vue3 + Vite + TS 工程结构、组件规范、状态管理 | P0 |
| 4 | 后端工程规范 | [backend-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/backend-spec.md) | Spring Boot 多模块结构、统一响应、异常、日志 | P0 |
| 5 | 后端基础设施手册 | [backend-infrastructure.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/backend-infrastructure.md) | 鉴权/JWT/TraceId/AOP/限流 等基础组件使用 | P0 |
| 6 | API 接口规范 | [api-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/api-spec.md) | RESTful 命名、版本控制、错误码、分页、签名 | P0 |
| 7 | 数据库设计规范 | [database-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/database-spec.md) | 表/字段命名、索引、审计字段、SQL 规范 | P0 |
| 8 | 代码质量与安全规范 | [code-quality.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/code-quality.md) | 单元测试、SonarQube、Checkstyle、OWASP | P0 |
| 9 | 文档与交付规范 | [documentation.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/documentation.md) | 必交付文档清单、ADR、变更日志 | P1 |

## 1. 仓库结构

PMIS 采用 **Monorepo** 模式集中管理前后端与基础设施代码：

```
ydsz-pmis/
├── ydsz-pmis-frontend/      # 前端工程 (Vue 3 + Vite + TS)
├── ydsz-pmis-backend/      # 后端微服务聚合 (Spring Cloud Alibaba, 7 部署 + 2 库)
│   ├── ydsz-pmis-common/   # 公共组件库 (响应/异常/工具/常量, 不独立部署)
│   ├── ydsz-pmis-gateway/  # API 网关
│   ├── ydsz-pmis-userinfo/  # 用户信息/RBAC/部门/人员/职级/字典/资源池/Bench (user + auth 合并)
│   ├── ydsz-pmis-workflow/ # 自研工作流引擎
│   ├── ydsz-pmis-project/  # 项目/商机/合同/执行/财务/报表 (project + execution 合并)
│   ├── ydsz-pmis-agent/    # AI 服务
│   ├── ydsz-pmis-system/   # 文件/配置/审计/通知/消息模板 (file + config + audit + notification + message 合并)
│   ├── ydsz-pmis-cronjob/ # 分布式任务调度
│   └── ydsz-pmis-literule/ # 轻量规则引擎 (库, 不独立部署)
├── deploy/                 # 部署与基础设施 (docker/sql/nacos)
├── docs/                   # 文档
├── scripts/                # 运维脚本
└── .github/workflows/      # CI 配置
```

> 📌 端口与服务名见 [`backend-infrastructure.md` §16 模块清单](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/backend-infrastructure.md#16-附录模块清单)。
> 📌 任何对仓库目录结构的调整必须先通过 PR 评审，并由架构组同步更新本节。

## 2. 强制约束（P0 - 必须遵守）

> 下述规则是 PMIS 工程基线（Baseline），所有代码合入前必须 100% 满足。
> 红线项（标 🔴）若违反，CI 将直接拒绝合并。

| 序号 | 规则 | 等级 | 校验手段 |
|------|------|------|----------|
| 🔴 1 | 所有提交必须经过 Code Review，主分支保护开启 | P0 | GitLab / GitHub Branch Protection |
| 🔴 2 | 所有公共方法必须有单元测试，业务代码行覆盖 ≥70%、分支覆盖 ≥60% | P0 | JaCoCo + SonarQube |
| 🔴 3 | 所有数据库表必须包含审计字段（`created_by/at`、`updated_by/at`、`deleted`、`status`） | P0 | Flyway Migration + Review |
| 🔴 4 | 所有对外 API 必须遵守 RESTful 命名 + 统一响应 `R<T>` | P0 | OpenAPI 校验 + Review |
| 🔴 5 | 所有枚举值必须先在枚举值管理中配置再硬编码引用 | P0 | 字典表 + Review |
| 🔴 6 | 所有金额字段统一使用 `NUMERIC(18,2)`，禁止浮点类型 | P0 | SQL 规范检查 |
| 🔴 7 | 所有日期时间统一使用 `TIMESTAMP` 并显式指定时区 | P0 | SQL 规范检查 |
| 🔴 8 | 禁止 `SELECT *`、禁止 `${}` 拼接 SQL、禁止 `NOT IN` | P0 | ArchUnit + MyBatis 拦截器 |
| 🔴 9 | 禁止硬编码密钥、密码、Token，统一从 Nacos / 环境变量读取 | P0 | GitLeaks + Trivy |
| 🔴 10 | 禁止提交 `.env`、`.idea/`、`target/`、`node_modules/`、密钥文件 | P0 | `.gitignore` + Pre-commit |
| ⚠️ 11 | Controller 入口必须 `@Valid`、`@PrePermission`、Swagger 注解 | P1 | Code Review |
| ⚠️ 12 | 所有外部调用必须捕获异常并打 ERROR 日志 + traceId | P1 | 全局异常处理器 |
| ⚠️ 13 | 大数据量列表接口必须使用服务端分页 + 排序参数 | P1 | Code Review |
| ⚠️ 14 | 公共组件/工具类必须放在 `common` 模块，禁止在业务模块重复实现 | P1 | ArchUnit |

## 3. 推荐做法（P1 - 强烈建议）

| 序号 | 做法 | 说明 |
|------|------|------|
| 1 | 使用 Lombok `@Slf4j` 简化 Logger 声明 | 统一 Logger 命名 |
| 2 | 使用 MapStruct 替代 BeanUtils 进行对象转换 | 编译期生成代码，性能更优 |
| 3 | DTO/VO 显式标注 `@Schema`，便于 OpenAPI 生成 | 文档质量 |
| 4 | 关键业务逻辑加 `@OperationLog` 自动记录操作日志 | 审计合规 |
| 5 | 高频接口加 `@RateLimit` 防刷 | 安全 |
| 6 | 关键路径加 `@PrePermission(value=…, mode=OR)` 多权限灵活控制 | 权限 |
| 7 | 复杂业务规则下沉到 `ydsz-pmis-literule` 规则引擎 | 可维护性 |
| 8 | 大文件、批量任务使用 MinIO / XXL-Job 异步处理 | 性能与稳定性 |

## 4. 流程图：规范落地路径

```
需求 PRD
   │
   ▼
架构评审 (RFC) ──► 命名/接口/库表设计 ──► 输出 ADR
   │
   ▼
分支 feature/PMIS-xxx
   │
   ▼
本地编码 (遵循 backend-spec / frontend-spec)
   │
   ▼
提交 (commitlint 校验) ──► 推送
   │
   ▼
CI 检查: 编译 → 单元测试 → SonarQube → 安全扫描 → 镜像构建
   │
   ▼
Code Review (≥1 Reviewer)
   │
   ▼
Squash Merge 至 main ──► 自动打 Tag ──► 触发 CD
   │
   ▼
文档更新 (CHANGELOG / OpenAPI / runbook)
```

## 5. 违规处理

| 等级 | 处理方式 |
|------|----------|
| P0 红线 | 拒绝合入，必须修改后重新提交 |
| P1 强制 | Reviewer 必须明确 Approve 才能合入，建议在 PR 中标注 TODO |
| P2 推荐 | 季度复盘纳入持续改进项，不阻塞合入 |

## 6. 变更与维护

- 任何规范的变更必须由架构组发起 PR，并经 2 位以上架构师 Approve
- 规范的版本号采用 SemVer：MAJOR（强制规则新增）、MINOR（推荐规则新增）、PATCH（错别字/示例修正）
- 规范与代码冲突时：**以代码实现反推规范**的合理性，避免文档与现实脱节

## 7. 联系与反馈

- 架构组邮箱：arch@ydsz-pmis.cn
- 问题反馈：Jira 项目 PMIS / 类型 "规范建议"
- 周会：每周三 16:00，议题"规范落地与改进"

> 💡 **One More Thing**：规范不是束缚，而是**降低协作成本**的契约。请把它当作"团队宪法"——先遵守，再讨论改进。
