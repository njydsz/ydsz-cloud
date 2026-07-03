<!--
  ===========================================================================
  文件名: git-workflow.md
  路径:   docs/standards/git-workflow.md
  作用:   PMIS Git 分支模型、Conventional Commits、commitlint、Code Review、Tag/Release 流程
  模型:   GitLab Flow + Trunk Based 混合（按大厂标准）
  ===========================================================================
-->

# Git 工作流规范

> 文档版本: V1.0 | 编制日期: 2026-06-30 | 最近更新: 2026-07-03
> 模型: GitLab Flow + Trunk Based 混合 (按大厂标准)

## 1. 分支模型

| 分支 | 用途 | 命名规范 | 保护策略 |
|------|------|----------|----------|
| `main` | 生产可用主分支 | `main` | 受保护，禁止直推，需 PR + 2 Reviewer |
| `release/*` | 发布分支 | `release/2026Q3` | 受保护，仅允许 hotfix 合并 |
| `feature/*` | 功能开发 | `feature/PMIS-123-project-create` | 自由推送 |
| `bugfix/*` | 缺陷修复（未上线） | `bugfix/PMIS-456-fix-timeout` | 自由推送 |
| `hotfix/*` | 紧急线上修复 | `hotfix/PMIS-789-prod-crash` | 需 1 Reviewer + 1 Maintainer |
| `chore/*` | 工程性变更（脚手架/依赖） | `chore/upgrade-spring-boot-3.3` | 自由推送 |

## 2. 提交规范 (Conventional Commits)

### 2.1 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 2.2 type 类型

| type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(project): 新增商机分级管理` |
| `fix` | 缺陷修复 | `fix(auth): 修复 Token 刷新失败` |
| `docs` | 文档变更 | `docs(readme): 补充部署说明` |
| `style` | 代码格式（不影响功能） | `style: 修正缩进与换行` |
| `refactor` | 重构 | `refactor(user): 拆分 UserService` |
| `perf` | 性能优化 | `perf(report): 优化报表查询索引` |
| `test` | 测试相关 | `test(user): 补充单元测试` |
| `build` | 构建/依赖 | `build: 升级 Spring Boot 至 3.3.4` |
| `ci` | CI 配置 | `ci: 启用 SonarQube 扫描` |
| `chore` | 其他杂项 | `chore: 清理无效依赖` |
| `revert` | 回滚 | `revert: 回滚 PMIS-456` |

### 2.3 scope 命名

scope 必须为业务模块名，限定如下：
- `frontend` (前端)
- `gateway` `auth` `user` `project` `finance` `resource` `workflow` `report` `agent` `notification` (后端微服务)
- `common` (公共模块)
- `deploy` (部署)
- `doc` (文档)
- `ci` (CI/CD)

### 2.4 subject 规则

- 中文或英文均可，团队统一使用中文
- 不超过 50 字符
- 动词开头，结尾不加句号
- 描述做什么而非为什么

### 2.5 示例

```
feat(project): 新增商机转立项自动化

- 商机赢单后自动将客户信息带入立项草稿
- 同步商机预占人员至立项人员计划
- 关联 issue: PMIS-123

Reviewed-by: zhangsan
Refs: PMIS-123
```

## 3. Commit 信息强制校验

通过 `commitlint` + `husky` 在提交时强制校验格式：

```bash
# .commitlintrc.json
{
  "extends": ["@commitlint/config-conventional"],
  "rules": {
    "type-enum": [2, "always", ["feat","fix","docs","style","refactor","perf","test","build","ci","chore","revert"]],
    "scope-enum": [2, "always", ["frontend","gateway","auth","user","project","finance","resource","workflow","report","agent","notification","common","deploy","doc"]]
  }
}
```

## 4. 分支生命周期

1. 从 `main` 切出 `feature/PMIS-123-xxx`
2. 本地开发，多次提交
3. 推送前 `git pull --rebase` 同步远程
4. 推送并创建 Merge Request (MR)
5. CI 自动运行：编译 + 单元测试 + SonarQube + 镜像构建
6. 至少 1 位同事 Code Review 通过（核心模块 2 位）
7. Squash Merge 合入 `main`
8. 删除远程 `feature/*` 分支

## 5. 版本号管理

遵循 [SemVer 2.0.0](https://semver.org/lang/zh-CN/)：

```
MAJOR.MINOR.PATCH
  │      │      └─ 兼容的问题修复
  │      └──────── 新增功能（向下兼容）
  └───────────── 重大变更（不兼容）
```

`MAJOR` 升级需发布评审；`MINOR` 升级记录在 CHANGELOG。

## 6. Tag 与发布

- `main` 分支通过 MR 合入后自动打 `v1.0.0` Tag
- 部署包版本号与 Tag 一致
- Release Notes 由 CI 自动生成，列出本版本所有 feat/fix 提交

## 7. Code Review 准则

- **必须** 在 24 小时内响应
- **必须** 给出明确意见：Approve / Request Changes / Comment
- Reviewer 检查项：
  - 命名是否规范
  - 是否有重复代码（DRY）
  - 是否有异常吞掉、资源未释放
  - SQL 是否有索引、全表扫描
  - 是否有敏感信息打印
  - 测试用例是否覆盖核心路径
  - 是否引入新的硬编码或魔法值
- 提交者收到 Request Changes 后必须全部响应才能再次请求 Review

## 8. 大文件与敏感信息

- 禁止提交：`*.jar`、`*.war`、`target/`、`node_modules/`、`.env`、`.idea/`、`.vscode/`、密码、密钥
- 仓库根 `.gitignore` 必须包含
- 敏感配置统一从 Nacos / 环境变量读取，**禁止硬编码**

## 9. 紧急修复（Hotfix）流程

```
线上故障 ──► 创建 hotfix/PMIS-xxx 分支（基于 main）
        ──► 修复 + 自测
        ──► 紧急合入 main（1 Reviewer + 1 Maintainer 即可）
        ──► 同步 Cherry-pick 到 release/* 当前发布分支
        ──► 发布新 Tag（如 v1.0.1）
        ──► 事后复盘 + 补全单测
```

> ⚠️ Hotfix 仅用于**生产 P0/P1 故障**，常规 Bug 修复走 `bugfix/*` → release 流程。

## 10. 常用 Git 命令约定

```bash
# 同步最新 main
git fetch origin
git rebase origin/main

# 推送前自检（项目根）
./scripts/pre-commit.sh   # 触发 husky + lint-staged

# 撤销最近一次提交（保留改动）
git reset --soft HEAD~1

# 修改最近一次提交信息
git commit --amend

# 交互式 rebase 合并多个 commit
git rebase -i HEAD~3
```

## 11. 提交信息红线

| ❌ 禁止 | ✅ 推荐 |
|---------|----------|
| `fix bug` | `fix(project): 修复项目状态切换失败的并发问题` |
| `update` | `feat(user): 新增用户导入功能` |
| `wip` | `chore: 临时提交，完成后需拆分` |
| 大段无意义 emoji | 简洁中文或英文，描述"做什么" |
| 多个不相关变更混在一个提交 | 一个提交只做一件事（原子提交） |

## 12. 变更记录

| 日期 | 版本 | 变更人 | 变更内容 |
|------|------|--------|----------|
| 2026-07-03 | 1.1 | 架构组 | 新增 §9 Hotfix 流程、§10 常用命令、§11 提交红线 |
| 2026-06-30 | 1.0 | 架构组 | 初始版本 |
