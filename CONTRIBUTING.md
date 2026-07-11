# 贡献指南

感谢您对 YDSZ PMIS 项目的关注！本文档描述了参与项目开发的规范与流程。

## 分支策略

- `main`：生产分支，受保护，仅通过 PR 合并
- `develop`：开发主干，日常集成分支
- `feature/*`：功能分支，从 develop 切出
- `fix/*`：修复分支
- `hotfix/*`：紧急修复，从 main 切出，合并回 main 和 develop

## 提交规范

本项目使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范，commitlint 强制校验。

### 提交格式
```
<type>(<scope>): <subject>

<body>

<footer>
```

### type 枚举
- `feat`：新功能
- `fix`：Bug 修复
- `docs`：文档变更
- `style`：代码格式（不影响功能）
- `refactor`：重构（既不是新功能也不是修复）
- `perf`：性能优化
- `test`：测试相关
- `chore`：构建/工具/依赖变更
- `ci`：CI/CD 变更

### scope 枚举
后端模块：`gateway` / `common` / `system` / `userinfo` / `project` / `sales` / `execution` / `finance` / `cronjob` / `message` / `workflow` / `agent` / `literule`
前端：`frontend`
部署：`deploy` / `k8s` / `helm` / `docker`
基础设施：`infra` / `sql` / `monitoring`

### 示例
```
feat(project): 新增合同变更审批流程

- 支持 ContractChangeController 发起变更申请
- 审批通过后自动同步原合同条款
- 关联工作流引擎自动创建审批实例
```

## 代码审查清单

### 后端
- [ ] 代码通过 `mvn checkstyle:check` 和 `mvn spotbugs:check`
- [ ] 新增/修改的 Service 方法有单元测试
- [ ] 写操作标注了 `@Idempotent` 或 `@IdempotentExempt`
- [ ] 查询操作返回 VO 而非 DO
- [ ] 分页接口使用 PageQuery 基类
- [ ] 敏感数据字段标注了 `@Sensitive`
- [ ] 列表查询考虑了 `@DataScope` 数据权限
- [ ] 无行内全限定类名（FQN）
- [ ] 未引入 Flyway / Liquibase 依赖

### 前端
- [ ] 代码通过 `pnpm run lint:check` 和 `vue-tsc --noEmit`
- [ ] 无硬编码中文（使用 i18n t() 函数）
- [ ] 表单有防重复提交（submitting 状态）
- [ ] 按钮有 v-permission 权限控制
- [ ] 使用 PageLayout + StatusTag 公共组件

### 通用
- [ ] commit message 符合 Conventional Commits 规范
- [ ] PR 描述清晰，关联了对应 Issue
- [ ] CI 流水线全部通过

## CI 门禁

### 后端 CI (backend-ci.yml)
- Maven Verify（编译 + 单元测试 + JaCoCo 覆盖率门禁）
- Checkstyle 静态检查
- SpotBugs 安全漏洞检测
- OWASP 依赖漏洞扫描（CVSS >= 7 阻断）
- 幂等注解覆盖检查
- DB Migration 拦截（禁止 Flyway/Liquibase）
- Schema 漂移检测

### 前端 CI (frontend-ci.yml)
- ESLint + Prettier 检查
- TypeScript 类型检查（vue-tsc --noEmit）
- i18n 覆盖率检测（硬编码中文阈值 30%）
- OpenAPI schema 漂移检测
- Vitest 单元测试 + 覆盖率门禁
- Playwright E2E 测试

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 21+ |
| Node.js | 20+ |
| pnpm | 9+ |
| Maven | 3.9+ |
| PostgreSQL | 18 |
| Redis | 8 |
| Nacos | 2.3.2 |

## 本地开发

### 后端
```bash
cd ydsz-pmis-backend
mvn clean compile
mvn test -pl ydsz-pmis-common
```

### 前端
```bash
cd ydsz-pmis-frontend
pnpm install
pnpm dev
```

## 项目规范

详见 `.trae/rules/` 目录下的规则文件：
- `git-commit-message.md`：Git 提交信息规范
- `no-inline-fqn.md`：禁止行内全限定类名

详见 `deploy/sql/README.md`：
- 禁止引入 Flyway / Liquibase
- 所有 schema 变更直接编辑 `deploy/sql/V1.0.0.sql`
