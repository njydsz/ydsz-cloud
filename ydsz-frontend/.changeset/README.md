# Changesets

本目录由 [@changesets/cli](https://github.com/changesets/changesets) 管理，用于跟踪 ydsz-frontend monorepo 中各包的版本变更。

## 工作流

### 1. 日常开发产生变更

当某次提交修改了 `apps/` 或 `comm/` 下的包，且需要发布版本时，运行：

```bash
pnpm changeset
```

按交互提示选择：
- 影响的包（可多选）
- 版本变更级别（major / minor / patch）
- 变更摘要（会写入 CHANGELOG）

这会在 `.changeset/` 下生成一个 `<random>.md` 文件，**与代码一起提交**。

### 2. 合并到主分支后消费 changeset

CI 或本地运行：

```bash
pnpm changeset version
```

这会：
- 消费 `.changeset/*.md` 文件
- 更新对应包的 `package.json` version 与 `CHANGELOG.md`
- 删除已消费的 changeset 文件

随后 `pnpm changeset publish` 发布到内部 npm registry。

### 3. 配置说明

- `access: "restricted"` — 内部 registry，非公开包
- `baseBranch: "main"` — 主分支
- `updateInternalDependencies: "patch"` — workspace 依赖仅自动 bump patch 版本
- `ignore` — 以下包不参与版本管理（仅开发期工具）：`@ydsz/*-config`、`@ydsz/tsconfig`、`@ydsz/vsh`、`@ydsz/turbo-run`

## 版本策略

- **major**：破坏性变更（API 不兼容、架构调整）
- **minor**：新功能（向后兼容）
- **patch**：bug 修复、文档、重构（向后兼容）

micro-kernel 当前版本 v3.3.x，遵循语义化版本。
