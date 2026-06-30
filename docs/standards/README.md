# 南京云顶 PMIS 开发规范总览

> 文档版本: V1.0 | 编制日期: 2026-06-30
> 对标: 阿里 / 字节 / 美团 / 华为云 等互联网大厂工程标准

本目录汇总 PMIS 项目第一阶段要求的全部工程规范，所有开发人员须 100% 遵循。

| 文档 | 路径 | 说明 |
|------|------|------|
| 编码与命名规范 | [naming-convention.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/naming-convention.md) | 标识符命名、目录结构、包/类/方法命名 |
| Git 工作流规范 | [git-workflow.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/git-workflow.md) | 分支模型、提交规范、Code Review、发布流程 |
| 前端工程规范 | [frontend-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/frontend-spec.md) | Vue3 + Vite + TS 工程结构、组件规范、状态管理 |
| 后端工程规范 | [backend-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/backend-spec.md) | Spring Boot 多模块结构、统一响应、异常、日志 |
| API 接口规范 | [api-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/api-spec.md) | RESTful 命名、版本控制、错误码、分页、签名 |
| 数据库设计规范 | [database-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/database-spec.md) | 表/字段命名、索引、审计字段、SQL 规范 |
| 代码质量与安全规范 | [code-quality.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/code-quality.md) | 单元测试、SonarQube、Checkstyle、OWASP |
| 文档与交付规范 | [documentation.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/documentation.md) | 必交付文档清单、ADR、变更日志 |

## 1. 仓库结构

PMIS 采用 **Monorepo** 模式集中管理前后端与基础设施代码：

```
ydsz-pmis/
├── ydsz-pmis-frontend/      # 前端工程 (Vue 3 + Vite + TS)
├── ydsz-pmis-backend/      # 后端微服务聚合 (Spring Cloud Alibaba)
│   ├── ydsz-pmis-common/   # 公共模块 (响应/异常/工具/常量)
│   ├── ydsz-pmis-gateway/  # API 网关
│   ├── ydsz-pmis-auth/     # 认证授权
│   ├── ydsz-pmis-user/     # 用户/组织/人员
│   ├── ydsz-pmis-project/  # 项目/商机/合同/执行
│   ├── ydsz-pmis-finance/  # 财务/成本/收入/利润
│   ├── ydsz-pmis-resource/ # 资源池/Bench
│   ├── ydsz-pmis-workflow/ # Flowable 工作流
│   ├── ydsz-pmis-report/   # 报表/驾驶舱
│   ├── ydsz-pmis-agent/    # AI 服务
│   └── ydsz-pmis-notification/ # 通知中心
├── deploy/                 # 部署与基础设施 (docker/sql/nacos)
├── docs/                   # 文档
├── scripts/                # 运维脚本
└── .github/workflows/      # CI 配置
```

## 2. 强制约束

1. **所有提交必须经过 Code Review**，主分支保护开启。
2. **所有公共方法必须有单元测试**，覆盖率要求：业务代码行覆盖 ≥70%、分支覆盖 ≥60%。
3. **所有数据库表必须包含审计字段** (`created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`)。
4. **所有对外 API 必须遵守 RESTful 命名 + 统一响应格式**。
5. **所有枚举值必须先在枚举值管理中配置**再硬编码引用。
6. **所有金额字段统一使用 `NUMERIC(18,2)`，禁止使用浮点类型**。
7. **所有日期时间统一使用 `TIMESTAMP` 并显式指定时区**。
